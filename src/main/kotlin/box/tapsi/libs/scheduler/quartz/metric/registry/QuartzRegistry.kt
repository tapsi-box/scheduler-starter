package box.tapsi.libs.scheduler.quartz.metric.registry

import box.tapsi.libs.metrics.core.services.MeterRegistryService
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import io.micrometer.core.instrument.Tag
import io.micrometer.observation.ObservationRegistry
import org.quartz.CronTrigger
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.slf4j.Logger
import org.springframework.stereotype.Component
import reactor.core.observability.micrometer.Micrometer
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import box.tapsi.libs.metrics.core.MeterName as TapsiMeterName

@Component
class QuartzRegistry(
  private val logger: Logger,
  private val meterRegistryService: MeterRegistryService,
  private val observationRegistry: ObservationRegistry,
) {
  private val lastSuccessGauges = ConcurrentHashMap<String, AtomicLong>()

  /**
   * Records the delay of a misfired trigger. The delay is clamped to a minimum of zero, because
   * Quartz can recompute the trigger's next fire time to the future before the listener runs,
   * which would otherwise produce a negative (and silently dropped) duration.
   *
   * @param scheduledFireTimeEpochMillis the fire time that the trigger missed.
   * @param actualFireTimeEpochMillis the time the misfire was observed.
   * @param job the stable job identifier (scheduler bean name).
   * @param jobGroup the group of the job the trigger fires, so the dashboard job-group filter
   *   matches misfire series too.
   * @param triggerKey the misfired trigger key (used only for its group tag).
   */
  fun exposeMisfireMetrics(
    scheduledFireTimeEpochMillis: Long,
    actualFireTimeEpochMillis: Long,
    job: String,
    jobGroup: String,
    triggerKey: TriggerKey,
  ) {
    val misfireDelay = (actualFireTimeEpochMillis - scheduledFireTimeEpochMillis).coerceAtLeast(0)
    meterRegistryService.recordTimer(
      MeterName.QuartzMisfireTime,
      misfireDelay,
      listOf(
        Tag.of(QUARTZ_TRIGGER_GROUP_METRICS_NAME, triggerKey.group),
        Tag.of(QUARTZ_JOB_GROUP_METRICS_NAME, jobGroup),
        Tag.of(QUARTZ_JOB_NAME_METRICS_NAME, job),
      ),
    )
    logger.info("Misfire delay for job $job (trigger ${triggerKey.name}) is $misfireDelay ms")
  }

  /**
   * Increments the retry counter for a job. The [result] tells apart a scheduled retry attempt
   * from an exhausted retry budget.
   */
  fun incrementRetry(job: String, jobGroup: String, result: RetryResult) {
    meterRegistryService.incrementCounter(
      MeterName.QuartzRetryCount,
      listOf(
        Tag.of(QUARTZ_JOB_NAME_METRICS_NAME, job),
        Tag.of(QUARTZ_JOB_GROUP_METRICS_NAME, jobGroup),
        Tag.of(QUARTZ_OUTCOME_METRICS_NAME, result.tagValue),
      ),
    )
    logger.info("Incremented retry counter for job $job with result ${result.tagValue}")
  }

  /**
   * Records the duration and outcome of a scheduling operation. The outcome tag keeps normal cron
   * re-registration (an [SchedulingOutcome.AlreadyExists] result) apart from a real failure.
   */
  fun recordScheduling(
    jobDetail: JobDetail,
    trigger: Trigger,
    outcome: SchedulingOutcome,
    durationMillis: Long,
  ) {
    meterRegistryService.recordTimer(
      MeterName.QuartzSchedulingTime,
      durationMillis,
      listOf(
        Tag.of(QUARTZ_JOB_NAME_METRICS_NAME, resolveJobName(jobDetail)),
        Tag.of(QUARTZ_JOB_GROUP_METRICS_NAME, jobDetail.key.group),
        Tag.of(QUARTZ_JOB_TYPE_METRICS_NAME, resolveJobType(trigger)),
        Tag.of(QUARTZ_OUTCOME_METRICS_NAME, outcome.tagValue),
      ),
    )
  }

  /**
   * Wraps a job execution publisher with the execution timer. The caller MUST apply this transform
   * upstream of any error-swallowing operator, so the observation records the true terminal signal
   * and the `error` tag is populated on failure. On success the last-success timestamp gauge is
   * updated for freshness alerting.
   */
  fun <T> exposeExecutionMetrics(
    publisher: Mono<T>,
    context: JobExecutionContext,
  ): Mono<T> {
    val job = resolveJobName(context.jobDetail)
    val jobGroup = context.jobDetail.key.group
    return publisher.name(QUARTZ_EXECUTION_METRICS_NAME)
      .tag(QUARTZ_JOB_GROUP_METRICS_NAME, jobGroup)
      .tag(QUARTZ_JOB_NAME_METRICS_NAME, job)
      .tag(QUARTZ_JOB_TYPE_METRICS_NAME, resolveJobType(context.trigger))
      .contextCapture()
      .tap(Micrometer.observation(observationRegistry))
      .doOnSuccess { recordLastSuccess(job, jobGroup) }
  }

  private fun recordLastSuccess(job: String, jobGroup: String) {
    val key = "$job|$jobGroup"
    val holder = lastSuccessGauges.computeIfAbsent(key) {
      val value = AtomicLong(0)
      registerLastSuccessGauge(job, jobGroup, value)
      value
    }
    holder.set(Instant.now().epochSecond)
  }

  private fun registerLastSuccessGauge(job: String, jobGroup: String, value: AtomicLong) {
    @Suppress("TooGenericExceptionCaught")
    try {
      meterRegistryService.registerGauge(
        MeterName.QuartzLastSuccessTimestamp,
        listOf(
          Tag.of(QUARTZ_JOB_NAME_METRICS_NAME, job),
          Tag.of(QUARTZ_JOB_GROUP_METRICS_NAME, jobGroup),
        ),
        value,
      ) { it.get().toDouble() }
    } catch (exception: Exception) {
      logger.error("Error registering last-success gauge for job $job", exception)
    }
  }

  /**
   * Resolves the stable job identifier from the job data map. It is the scheduler bean name that
   * [QuartzJobDetailFactory] stores when it builds the job. The Quartz job key name is the
   * per-execution composite id, so it MUST NOT be used as a tag value; the fallback is a bounded
   * placeholder to keep the tag cardinality bounded even on the missing-key path.
   */
  private fun resolveJobName(jobDetail: JobDetail): String = jobDetail.jobDataMap
    .getString(QuartzJobDetailFactory.SCHEDULER_JOB_STORE_KEY_MAP)
    ?: UNKNOWN_JOB

  private fun resolveJobType(trigger: Trigger?): String = if (trigger is CronTrigger) {
    JOB_TYPE_CRON
  } else {
    JOB_TYPE_REGULAR
  }

  enum class MeterName(override val meterName: String) : TapsiMeterName {
    // Keeps the existing dashboard series names; only tag values and outcomes change.
    QuartzMisfireTime("quartz.metrics.execution.misfire.time"),
    QuartzSchedulingTime("quartz.metrics.scheduling"),

    // Additive metrics introduced by the enhancement.
    QuartzRetryCount("quartz.metrics.execution.retry.count"),
    QuartzLastSuccessTimestamp("quartz.metrics.execution.last.success.timestamp"),
  }

  enum class RetryResult(val tagValue: String) {
    Retried("retried"),
    Exhausted("exhausted"),
  }

  enum class SchedulingOutcome(val tagValue: String) {
    Success("success"),
    AlreadyExists("already_exists"),
    Failure("failure"),
  }

  companion object {
    const val QUARTZ_TRIGGER_GROUP_METRICS_NAME = "triggerGroup"
    const val QUARTZ_JOB_GROUP_METRICS_NAME = "jobGroup"
    const val QUARTZ_JOB_NAME_METRICS_NAME = "jobName"
    const val QUARTZ_JOB_TYPE_METRICS_NAME = "jobType"
    const val QUARTZ_OUTCOME_METRICS_NAME = "outcome"
    const val QUARTZ_EXECUTION_METRICS_NAME = "quartz.metrics.execution"

    const val JOB_TYPE_CRON = "cron"
    const val JOB_TYPE_REGULAR = "regular"

    /** Bounded placeholder used when the stable job identifier cannot be resolved. */
    const val UNKNOWN_JOB = "unknown"
  }
}
