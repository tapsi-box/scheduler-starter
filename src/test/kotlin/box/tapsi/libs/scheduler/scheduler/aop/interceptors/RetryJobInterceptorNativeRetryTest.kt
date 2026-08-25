package box.tapsi.libs.scheduler.scheduler.aop.interceptors

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import box.tapsi.libs.scheduler.quartz.services.QuartzService
import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.RegularScheduler
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import box.tapsi.libs.utilities.time.TimeOperatorImpl
import io.github.mahdibohloul.projectreactor.retry.aop.annotation.ReactiveRetryable
import org.aopalliance.intercept.MethodInvocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.ObjectAlreadyExistsException
import org.quartz.Trigger
import org.quartz.TriggerKey
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.lang.reflect.AccessibleObject
import java.lang.reflect.Method

/**
 * Proves the native retry routing: a regular scheduler adds a retry trigger to its own stable job
 * key, a cron scheduler on cadence spawns a separate `<jobId>_retry` job (dropping a duplicate),
 * and the spawned cron retry job retries itself in place. It uses a recording [QuartzService] so no
 * Quartz scheduler is needed.
 */
class RetryJobInterceptorNativeRetryTest {
  private lateinit var quartzService: RecordingQuartzService
  private lateinit var interceptor: RetryJobInterceptor
  private val schedulerService = mock(SchedulerService::class.java)

  @BeforeEach
  fun setUp() {
    quartzService = RecordingQuartzService()
    interceptor = RetryJobInterceptor(TimeOperatorImpl(), mock(QuartzRegistry::class.java), quartzService)
  }

  @Test
  fun `a failed regular scheduler adds a retry trigger to its own stable job key`() {
    // given
    val scheduler = RetryableRegularScheduler(schedulerService)
    val invocation = failingInvocation(scheduler, JobStore())

    // when

    // verify
    assertOriginalErrorPropagates(interceptor.invoke(invocation) as Mono<*>)
    assertTrue(quartzService.scheduledJobs.isEmpty(), "a regular retry must not spawn a new job")
    assertEquals(1, quartzService.scheduledTriggers.size)
    val retryTrigger = quartzService.scheduledTriggers.single()
    assertEquals("settlement-123", retryTrigger.jobKey.name, "the retry trigger must target the stable job key")
    assertEquals("settlement-123_retry_1", retryTrigger.key.name)
  }

  @Test
  fun `a second regular retry attempt adds a distinct trigger to the same stable job key`() {
    // given
    val scheduler = RetryableRegularScheduler(schedulerService)
    val jobStore = JobStore().apply { put(RetryJobInterceptor.RETRY_COUNT_JOB_STORE_KEY, 1) }
    val invocation = failingInvocation(scheduler, jobStore)

    // when

    // verify
    assertOriginalErrorPropagates(interceptor.invoke(invocation) as Mono<*>)
    assertTrue(quartzService.scheduledJobs.isEmpty(), "a later regular retry must still not spawn a job")
    val retryTrigger = quartzService.scheduledTriggers.single()
    assertEquals("settlement-123", retryTrigger.jobKey.name, "the retry keeps targeting the stable job key")
    assertEquals("settlement-123_retry_2", retryTrigger.key.name, "the second attempt uses a distinct trigger key")
  }

  @Test
  fun `a failed cron cadence fire spawns a separate stable retry job`() {
    // given
    val scheduler = RetryableCronScheduler(schedulerService)
    val invocation = failingInvocation(scheduler, JobStore())

    // when

    // verify
    assertOriginalErrorPropagates(interceptor.invoke(invocation) as Mono<*>)
    assertTrue(quartzService.scheduledTriggers.isEmpty(), "a cron cadence retry must not add a trigger to the cron job")
    assertEquals(1, quartzService.scheduledJobs.size)
    val (retryJob, retryTrigger) = quartzService.scheduledJobs.single()
    assertEquals("reconciliation-cron_retry", retryJob.key.name, "the cron retry job must use the stable _retry id")
    assertEquals("reconciliation-cron_retry_trigger", retryTrigger.key.name)
    assertTrue(
      retryJob.jobDataMap.containsKey(RetryJobInterceptor.CRON_RETRY_JOB_STORE_KEY),
      "the spawned job must carry the cron-retry flag so its own failures retry in place",
    )
  }

  @Test
  fun `a duplicate cron retry job is dropped and the original error still propagates`() {
    // given
    val scheduler = RetryableCronScheduler(schedulerService)
    val invocation = failingInvocation(scheduler, JobStore())

    // when
    quartzService.scheduleJobError = ObjectAlreadyExistsException("retry job already pending")

    // verify
    assertOriginalErrorPropagates(interceptor.invoke(invocation) as Mono<*>)
  }

  @Test
  fun `a failed cron retry job retries itself in place instead of spawning again`() {
    // given
    val scheduler = RetryableCronScheduler(schedulerService)
    val jobStore = JobStore().apply { put(RetryJobInterceptor.CRON_RETRY_JOB_STORE_KEY, true) }
    val invocation = failingInvocation(scheduler, jobStore)

    // when

    // verify
    assertOriginalErrorPropagates(interceptor.invoke(invocation) as Mono<*>)
    assertTrue(quartzService.scheduledJobs.isEmpty(), "the cron retry job must not spawn another job")
    assertEquals(1, quartzService.scheduledTriggers.size)
    assertEquals(
      "reconciliation-cron_retry",
      quartzService.scheduledTriggers.single().jobKey.name,
      "the in-place retry must target the spawned cron retry job",
    )
  }

  private fun assertOriginalErrorPropagates(mono: Mono<*>) {
    val error = runCatching { mono.block() }.exceptionOrNull()
    assertTrue(
      error is IllegalStateException,
      "the original error must propagate after the retry is scheduled, but was: $error",
    )
  }

  private fun failingInvocation(scheduler: RegularScheduler, jobStore: JobStore): MethodInvocation {
    val executeMethod = scheduler::class.java.getMethod("execute", JobStore::class.java)
    return FakeMethodInvocation(executeMethod, scheduler, arrayOf(jobStore), Mono.error(IllegalStateException("boom")))
  }

  private class FakeMethodInvocation(
    private val method: Method,
    private val target: Any,
    private val args: Array<Any?>,
    private val result: Mono<Void>,
  ) : MethodInvocation {
    override fun getMethod(): Method = method
    override fun getArguments(): Array<Any?> = args
    override fun proceed(): Any = result
    override fun getThis(): Any = target
    override fun getStaticPart(): AccessibleObject = method
  }

  private class RecordingQuartzService : QuartzService {
    val scheduledTriggers = mutableListOf<Trigger>()
    val scheduledJobs = mutableListOf<Pair<JobDetail, Trigger>>()
    var scheduleJobError: Throwable? = null

    override fun <TJob : Job> createJob(
      jobClass: Class<TJob>,
      isDurable: Boolean,
      jobName: String,
      jobGroup: String,
      jobDataMap: JobDataMap,
    ): JobDetail = JobBuilder.newJob(jobClass)
      .withIdentity(jobName, jobGroup)
      .setJobData(jobDataMap)
      .storeDurably(isDurable)
      .build()

    override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Mono<Void> {
      scheduleJobError?.let { return Mono.error(it) }
      scheduledJobs.add(jobDetail to trigger)
      return Mono.empty()
    }

    override fun scheduleTrigger(trigger: Trigger): Mono<Void> {
      scheduledTriggers.add(trigger)
      return Mono.empty()
    }

    override fun deleteJob(jobKey: JobKey): Mono<Void> = Mono.empty()
    override fun rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): Mono<Void> = Mono.empty()
    override fun getTriggers(triggerGroup: String): Flux<Trigger> = Flux.empty()
  }

  @ReactiveRetryable(
    interceptor = RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME,
    maxAttempts = 5,
    backOffFixDelay = FIX_DELAY_MILLIS,
  )
  private class RetryableRegularScheduler(schedulerService: SchedulerService) : RegularScheduler(schedulerService) {
    override fun execute(jobStore: JobStore?): Mono<Void> = Mono.empty()
    override fun cancel(jobStore: JobStore?): Mono<Void> = Mono.empty()
    override fun createJobId(jobStore: JobStore): String = "settlement-123"
    override fun getJobGroup(): JobGroup = JobGroup.fromString("job-group")
    override fun getTriggerGroup(): TriggerGroup = TriggerGroup.fromString("trigger-group")
  }

  @ReactiveRetryable(
    interceptor = RetryJobInterceptor.RETRY_JOB_INTERCEPTOR_NAME,
    maxAttempts = 5,
    backOffFixDelay = FIX_DELAY_MILLIS,
  )
  private class RetryableCronScheduler(schedulerService: SchedulerService) : CronScheduler(schedulerService) {
    override fun getCronExpression(): String = "0 0 * * * ?"
    override fun execute(jobStore: JobStore?): Mono<Void> = Mono.empty()
    override fun cancel(jobStore: JobStore?): Mono<Void> = Mono.empty()
    override fun createJobId(jobStore: JobStore): String = "reconciliation-cron"
    override fun getJobGroup(): JobGroup = JobGroup.fromString("job-group")
    override fun getTriggerGroup(): TriggerGroup = TriggerGroup.fromString("trigger-group")
  }

  companion object {
    private const val FIX_DELAY_MILLIS = 1_000L
  }
}
