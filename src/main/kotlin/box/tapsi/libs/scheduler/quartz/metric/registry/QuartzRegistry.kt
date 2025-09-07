package box.tapsi.libs.scheduler.quartz.metric.registry

import box.tapsi.libs.metrics.core.services.MeterRegistryService
import io.micrometer.core.instrument.Tag
import io.micrometer.observation.ObservationRegistry
import org.quartz.JobDetail
import org.quartz.JobExecutionContext
import org.quartz.TriggerKey
import org.slf4j.Logger
import org.springframework.stereotype.Component
import reactor.core.observability.micrometer.Micrometer
import reactor.core.publisher.Mono
import box.tapsi.libs.metrics.core.MeterName as TapsiMeterName

@Component
class QuartzRegistry(
  private val logger: Logger,
  private val meterRegistryService: MeterRegistryService,
  private val observationRegistry: ObservationRegistry,
) {
  fun incrementPendingJob(triggerKey: TriggerKey) {
    meterRegistryService.incrementCounter(
      MeterName.QuartzPendingExecutionJobCount,
      listOf(
        Tag.of(QUARTZ_TRIGGER_GROUP_METRICS_NAME, triggerKey.group),
        Tag.of(QUARTZ_TRIGGER_NAME_METRICS_NAME, triggerKey.name),
      ),
    )
    logger.info("Incremented pending job count for triggerKey: $triggerKey")
  }

  fun exposeMisfireMetrics(expectedFireTime: Long, actualFireTime: Long, triggerKey: TriggerKey) {
    val misfireTime = actualFireTime - expectedFireTime
    meterRegistryService.recordTimer(
      MeterName.QuartzMisfireTime,
      misfireTime,
      listOf(
        Tag.of(QUARTZ_TRIGGER_GROUP_METRICS_NAME, triggerKey.group),
        Tag.of(QUARTZ_TRIGGER_NAME_METRICS_NAME, triggerKey.name),
      ),
    )
    logger.info("Misfire time for triggerKey: $triggerKey is $misfireTime")
  }

  fun incrementActiveJob(ctx: JobExecutionContext) {
    meterRegistryService.incrementCounter(
      MeterName.QuartzActiveExecutionJobCount,
      listOf(
        Tag.of(QUARTZ_JOB_GROUP_METRICS_NAME, ctx.jobDetail.key.group),
        Tag.of(QUARTZ_JOB_NAME_METRICS_NAME, ctx.jobDetail.key.name),
      ),
    )
    logger.info("Incremented active job count for jobKey: ${ctx.jobDetail.key}")
  }

  fun <T> exposeSchedulingMetrics(
    publisher: Mono<T>,
    jobDetail: JobDetail,
  ): Mono<T> = publisher.name(QUARTZ_SCHEDULING_METRICS_NAME)
    .tag(QUARTZ_JOB_GROUP_METRICS_NAME, jobDetail.key.group)
    .tag(QUARTZ_JOB_NAME_METRICS_NAME, jobDetail.key.name)
    .contextCapture()
    .tap(Micrometer.observation(observationRegistry))

  fun <T> exposeExecutionMetrics(
    publisher: Mono<T>,
    jobDetail: JobDetail,
  ): Mono<T> = publisher.name(QUARTZ_EXECUTION_METRICS_NAME)
    .tag(QUARTZ_JOB_GROUP_METRICS_NAME, jobDetail.key.group)
    .tag(QUARTZ_JOB_NAME_METRICS_NAME, jobDetail.key.name)
    .contextCapture()
    .tap(Micrometer.observation(observationRegistry))

  companion object {
    const val QUARTZ_TRIGGER_GROUP_METRICS_NAME = "triggerGroup"
    const val QUARTZ_TRIGGER_NAME_METRICS_NAME = "triggerName"
    const val QUARTZ_JOB_GROUP_METRICS_NAME = "jobGroup"
    const val QUARTZ_JOB_NAME_METRICS_NAME = "jobName"
    const val QUARTZ_SCHEDULING_METRICS_NAME = "quartz.metrics.scheduling"
    const val QUARTZ_EXECUTION_METRICS_NAME = "quartz.metrics.execution"

    enum class MeterName(override val meterName: String) : TapsiMeterName {
      QuartzActiveExecutionJobCount("quartz.metrics.execution.active.count"),
      QuartzPendingExecutionJobCount("quartz.metrics.execution.pending.count"),
      QuartzMisfireTime("quartz.metrics.execution.misfire.time"),
    }
  }
}
