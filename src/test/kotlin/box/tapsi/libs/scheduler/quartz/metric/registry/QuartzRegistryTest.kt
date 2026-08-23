package box.tapsi.libs.scheduler.quartz.metric.registry

import box.tapsi.libs.metrics.core.services.MeterRegistryServiceImpl
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.observation.ObservationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.slf4j.Logger
import reactor.core.publisher.Mono
import java.util.concurrent.TimeUnit

class QuartzRegistryTest {
  private lateinit var registry: SimpleMeterRegistry
  private lateinit var quartzRegistry: QuartzRegistry

  @BeforeEach
  fun setUp() {
    registry = SimpleMeterRegistry()
    val observationRegistry = ObservationRegistry.create()
    observationRegistry.observationConfig().observationHandler(DefaultMeterObservationHandler(registry))
    quartzRegistry = QuartzRegistry(
      logger = mock(Logger::class.java),
      meterRegistryService = MeterRegistryServiceImpl(registry),
      observationRegistry = observationRegistry,
    )
  }

  @Test
  fun `should clamp a negative misfire delay to zero`() {
    // given
    val triggerKey = TriggerKey.triggerKey("some-trigger", "SomeTriggerGroup")

    // when
    quartzRegistry.exposeMisfireMetrics(
      scheduledFireTimeEpochMillis = 2_000L,
      actualFireTimeEpochMillis = 1_000L,
      job = "SomeCronJob",
      jobGroup = "SomeJobGroup",
      triggerKey = triggerKey,
    )

    // verify
    val timer = registry.find(QuartzRegistry.MeterName.QuartzMisfireTime.meterName).timer()!!
    assertEquals(1L, timer.count())
    assertEquals(0.0, timer.totalTime(TimeUnit.MILLISECONDS))
  }

  @Test
  fun `should record the positive misfire delay tagged with the stable job and job group`() {
    // given
    val triggerKey = TriggerKey.triggerKey("some-trigger", "SomeTriggerGroup")

    // when
    quartzRegistry.exposeMisfireMetrics(
      scheduledFireTimeEpochMillis = 1_000L,
      actualFireTimeEpochMillis = 3_500L,
      job = "SomeCronJob",
      jobGroup = "SomeJobGroup",
      triggerKey = triggerKey,
    )

    // verify
    val timer = registry.find(QuartzRegistry.MeterName.QuartzMisfireTime.meterName)
      .tag(QuartzRegistry.QUARTZ_JOB_NAME_METRICS_NAME, "SomeCronJob")
      .tag(QuartzRegistry.QUARTZ_JOB_GROUP_METRICS_NAME, "SomeJobGroup")
      .tag(QuartzRegistry.QUARTZ_TRIGGER_GROUP_METRICS_NAME, "SomeTriggerGroup")
      .timer()!!
    assertEquals(2_500.0, timer.totalTime(TimeUnit.MILLISECONDS))
  }

  @Test
  fun `should increment the retry counter with the exhausted outcome`() {
    // given
    val job = "SomeCronJob"
    val jobGroup = "SomeJobGroup"

    // when
    quartzRegistry.incrementRetry(job, jobGroup, QuartzRegistry.RetryResult.Exhausted)

    // verify
    val counter = registry.find(QuartzRegistry.MeterName.QuartzRetryCount.meterName)
      .tag(QuartzRegistry.QUARTZ_JOB_NAME_METRICS_NAME, job)
      .tag(QuartzRegistry.QUARTZ_JOB_GROUP_METRICS_NAME, jobGroup)
      .tag(QuartzRegistry.QUARTZ_OUTCOME_METRICS_NAME, "exhausted")
      .counter()!!
    assertEquals(1.0, counter.count())
  }

  @Test
  fun `should record a failed execution with a non-none error tag`() {
    // given
    val context = jobExecutionContext(schedulerBeanName = "someCronJobBean")

    // when
    quartzRegistry.exposeExecutionMetrics(Mono.error<Void>(IllegalStateException("boom")), context)
      .onErrorResume { Mono.empty() }
      .block()

    // verify
    val timer = registry.find(QuartzRegistry.QUARTZ_EXECUTION_METRICS_NAME)
      .tag(QuartzRegistry.QUARTZ_JOB_NAME_METRICS_NAME, "someCronJobBean")
      .timer()!!
    val errorTag = timer.id.getTag("error")
    assertNotNull(errorTag)
    assertNotEquals("none", errorTag)
  }

  @Test
  fun `should tag a successful execution with the stable bean name`() {
    // given
    val context = jobExecutionContext(schedulerBeanName = "someCronJobBean")

    // when
    quartzRegistry.exposeExecutionMetrics(Mono.empty<Void>(), context).block()

    // verify
    val timer = registry.find(QuartzRegistry.QUARTZ_EXECUTION_METRICS_NAME)
      .tag(QuartzRegistry.QUARTZ_JOB_NAME_METRICS_NAME, "someCronJobBean")
      .tag(QuartzRegistry.QUARTZ_JOB_TYPE_METRICS_NAME, QuartzRegistry.JOB_TYPE_REGULAR)
      .timer()
    assertNotNull(timer)
  }

  @Test
  fun `should fall back to a bounded job name when the scheduler bean is missing`() {
    // given
    val context = jobExecutionContext(schedulerBeanName = null)

    // when
    quartzRegistry.exposeExecutionMetrics(Mono.empty<Void>(), context).block()

    // verify
    val timer = registry.find(QuartzRegistry.QUARTZ_EXECUTION_METRICS_NAME)
      .tag(QuartzRegistry.QUARTZ_JOB_NAME_METRICS_NAME, QuartzRegistry.UNKNOWN_JOB)
      .timer()
    assertNotNull(timer)
  }

  private fun jobExecutionContext(schedulerBeanName: String?): JobExecutionContext {
    val jobDataMap = JobDataMap()
    schedulerBeanName?.let { jobDataMap.put(QuartzJobDetailFactory.SCHEDULER_JOB_STORE_KEY_MAP, it) }
    val jobDetail = mock(JobDetail::class.java)
    `when`(jobDetail.jobDataMap).thenReturn(jobDataMap)
    `when`(jobDetail.key).thenReturn(JobKey.jobKey("some-unique-id", "SomeJobGroup"))
    val context = mock(JobExecutionContext::class.java)
    `when`(context.jobDetail).thenReturn(jobDetail)
    `when`(context.trigger).thenReturn(mock(Trigger::class.java))
    return context
  }
}
