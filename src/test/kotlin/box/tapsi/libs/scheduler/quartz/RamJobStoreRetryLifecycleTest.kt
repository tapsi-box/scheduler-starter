package box.tapsi.libs.scheduler.quartz

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobExecutionContext
import org.quartz.JobKey
import org.quartz.Scheduler
import org.quartz.TriggerBuilder
import org.quartz.TriggerKey
import org.quartz.impl.StdSchedulerFactory
import java.util.Date
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards the core Quartz [org.quartz.spi.JobStore] contract that the retry redesign depends on. The
 * retry adds the next one-shot trigger to the same, stable, non-durable job key before the current
 * fire completes. This test proves the store keeps the job while a sibling trigger stays, removes
 * the job when its last trigger completes, and removes the job together with a pending trigger on a
 * delete.
 *
 * It runs against [org.quartz.simpl.RAMJobStore], the reference in-memory store. The JDBC stores and
 * the novemberain Mongo store implement the same contract, so a pass here is the store-agnostic
 * proof for the auto-clear mechanism.
 */
class RamJobStoreRetryLifecycleTest {
  private lateinit var scheduler: Scheduler

  @BeforeEach
  fun setUp() {
    SelfReschedulingJob.runCount.set(0)
    CapturingJob.merged.clear()
    scheduler = createRamScheduler("ram-retry-lifecycle-${counter.incrementAndGet()}")
    scheduler.start()
  }

  @AfterEach
  fun tearDown() {
    scheduler.shutdown(true)
  }

  @Test
  fun `non-durable job survives its first trigger completion while a sibling retry trigger stays`() {
    // given
    val jobKey = JobKey.jobKey("survives-job", GROUP)
    val firstTriggerKey = TriggerKey.triggerKey("first-trigger", GROUP)
    val jobDetail = JobBuilder.newJob(SelfReschedulingJob::class.java)
      .withIdentity(jobKey)
      .storeDurably(false)
      .build()
    val firstTrigger = TriggerBuilder.newTrigger()
      .withIdentity(firstTriggerKey)
      .forJob(jobKey)
      .startNow()
      .build()

    // when

    // verify
    scheduler.scheduleJob(jobDetail, firstTrigger)
    // The job adds a far-future sibling retry trigger on its first fire, so the first trigger's
    // completion must not orphan the job.
    awaitUntil { scheduler.getTrigger(firstTriggerKey) == null }
    assertTrue(scheduler.checkExists(jobKey), "job must survive while a sibling retry trigger stays")
    assertEquals(1, scheduler.getTriggersOfJob(jobKey).size, "only the pending retry trigger must stay")
    assertEquals(1, SelfReschedulingJob.runCount.get(), "the sibling retry trigger must not have fired yet")
  }

  @Test
  fun `non-durable job is auto-removed after its last trigger completes`() {
    // given
    val jobKey = JobKey.jobKey("auto-clear-job", GROUP)
    val jobDetail = JobBuilder.newJob(NoOpJob::class.java)
      .withIdentity(jobKey)
      .storeDurably(false)
      .build()
    val onlyTrigger = TriggerBuilder.newTrigger()
      .withIdentity("only-trigger", GROUP)
      .forJob(jobKey)
      .startNow()
      .build()

    // when

    // verify
    scheduler.scheduleJob(jobDetail, onlyTrigger)
    awaitUntil { !scheduler.checkExists(jobKey) }
    assertFalse(scheduler.checkExists(jobKey), "the non-durable job must clear itself after its last fire")
  }

  @Test
  fun `merged job data map is the job data on a normal fire and the trigger data overrides it on a retry fire`() {
    // given
    val normalJobKey = JobKey.jobKey("merge-normal-job", GROUP)
    val retryJobKey = JobKey.jobKey("merge-retry-job", GROUP)
    val normalJob = JobBuilder.newJob(CapturingJob::class.java)
      .withIdentity(normalJobKey)
      .storeDurably(false)
      .usingJobData("scheduler", "beanName")
      .usingJobData("state", "job")
      .build()
    // A retry trigger carries the failed run's updated state; the merge must let it win over job data.
    val retryJob = JobBuilder.newJob(CapturingJob::class.java)
      .withIdentity(retryJobKey)
      .storeDurably(false)
      .usingJobData("scheduler", "beanName")
      .usingJobData("state", "job")
      .build()
    val normalTrigger = TriggerBuilder.newTrigger().withIdentity("merge-normal-trigger", GROUP)
      .forJob(normalJobKey).startNow().build()
    val retryTrigger = TriggerBuilder.newTrigger().withIdentity("merge-retry-trigger", GROUP)
      .forJob(retryJobKey).startNow().usingJobData("state", "retry").build()

    // when

    // verify
    scheduler.scheduleJob(normalJob, normalTrigger)
    awaitUntil { CapturingJob.merged[normalJobKey.name] != null }
    assertEquals("beanName", CapturingJob.merged[normalJobKey.name]?.get("scheduler"))
    assertEquals("job", CapturingJob.merged[normalJobKey.name]?.get("state"), "an empty trigger map leaves job data")

    scheduler.scheduleJob(retryJob, retryTrigger)
    awaitUntil { CapturingJob.merged[retryJobKey.name] != null }
    assertEquals("beanName", CapturingJob.merged[retryJobKey.name]?.get("scheduler"), "job-only keys survive")
    assertEquals("retry", CapturingJob.merged[retryJobKey.name]?.get("state"), "trigger data overrides job data")
  }

  @Test
  fun `deleteJob removes a non-durable job together with its pending retry trigger`() {
    // given
    val jobKey = JobKey.jobKey("cancel-job", GROUP)
    val pendingTriggerKey = TriggerKey.triggerKey("pending-trigger", GROUP)
    val jobDetail = JobBuilder.newJob(NoOpJob::class.java)
      .withIdentity(jobKey)
      .storeDurably(false)
      .build()
    // A trigger far in the future stands in for a pending retry that a cancel must remove.
    val pendingTrigger = TriggerBuilder.newTrigger()
      .withIdentity(pendingTriggerKey)
      .forJob(jobKey)
      .startAt(Date(System.currentTimeMillis() + FAR_FUTURE_MILLIS))
      .build()

    // when

    // verify
    scheduler.scheduleJob(jobDetail, pendingTrigger)
    assertTrue(scheduler.checkExists(jobKey), "the job must exist before the cancel")
    scheduler.deleteJob(jobKey)
    assertFalse(scheduler.checkExists(jobKey), "deleteJob must remove the job")
    assertFalse(scheduler.checkExists(pendingTriggerKey), "deleteJob must remove the pending retry trigger")
  }

  private fun awaitUntil(timeoutMillis: Long = AWAIT_TIMEOUT_MILLIS, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
      if (condition()) {
        return
      }
      Thread.sleep(POLL_INTERVAL_MILLIS)
    }
    throw AssertionError("condition was not met within ${timeoutMillis}ms")
  }

  private fun createRamScheduler(name: String): Scheduler {
    val properties = Properties().apply {
      setProperty("org.quartz.scheduler.instanceName", name)
      setProperty("org.quartz.scheduler.instanceId", "AUTO")
      setProperty("org.quartz.scheduler.skipUpdateCheck", "true")
      setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool")
      setProperty("org.quartz.threadPool.threadCount", "2")
      setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore")
    }
    return StdSchedulerFactory(properties).scheduler
  }

  /**
   * Mirrors the retry mechanism: on its first fire it adds a one-shot sibling trigger to its own,
   * stable job key. The sibling starts far in the future, so the test can assert the job survived
   * the first trigger's completion before the sibling fires.
   */
  class SelfReschedulingJob : Job {
    override fun execute(context: JobExecutionContext) {
      if (runCount.incrementAndGet() == 1) {
        val retryTrigger = TriggerBuilder.newTrigger()
          .withIdentity("retry-trigger", GROUP)
          .forJob(context.jobDetail.key)
          .startAt(Date(System.currentTimeMillis() + FAR_FUTURE_MILLIS))
          .build()
        context.scheduler.scheduleJob(retryTrigger)
      }
    }

    companion object {
      val runCount = AtomicInteger(0)
    }
  }

  class NoOpJob : Job {
    override fun execute(context: JobExecutionContext) = Unit
  }

  /** Records the merged job data map of each fire, keyed by job name, so a test can assert the merge. */
  class CapturingJob : Job {
    override fun execute(context: JobExecutionContext) {
      merged[context.jobDetail.key.name] = HashMap(context.mergedJobDataMap.wrappedMap)
    }

    companion object {
      val merged = ConcurrentHashMap<String, Map<String, Any?>>()
    }
  }

  companion object {
    private const val GROUP = "retry-lifecycle-test"
    private const val FAR_FUTURE_MILLIS = 60_000L
    private const val AWAIT_TIMEOUT_MILLIS = 5_000L
    private const val POLL_INTERVAL_MILLIS = 20L
    private val counter = AtomicInteger(0)
  }
}
