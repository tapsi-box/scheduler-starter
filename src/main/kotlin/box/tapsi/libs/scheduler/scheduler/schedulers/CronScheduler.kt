package box.tapsi.libs.scheduler.scheduler.schedulers

import box.tapsi.libs.scheduler.scheduler.SchedulerException
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import reactor.core.publisher.Mono
import java.time.Instant

abstract class CronScheduler(schedulerService: SchedulerService) : RegularScheduler(schedulerService) {
  abstract fun getCronExpression(): String

  override fun schedule(
    jobStore: JobStore?,
    fireTimestamp: Instant?,
  ): Mono<Void> = super.schedule(jobStore, fireTimestamp)
    .onErrorResume(SchedulerException.NoJobStoreFoundException::class.java) {
      return@onErrorResume scheduleCronJob(JobStore(), fireTimestamp)
    }

  protected open fun getFireTimestamp(): Instant? = null

  protected open fun scheduleCronJob(jobStore: JobStore, fireTimestamp: Instant?): Mono<Void> = Mono.fromSupplier {
    SchedulingInstruction.Cron(
      fireTimestamp = fireTimestamp ?: getFireTimestamp(),
      jobStore = jobStore,
      scheduler = this::class,
      jobId = createJobId(jobStore),
      retriedCount = getRetriedCount(jobStore),
      cronExpression = getCronExpression(),
      jobGroup = getJobGroup(),
      triggerGroup = getTriggerGroup(),
    )
  }.flatMap { instruction ->
    schedulerService.scheduleCronJob(instruction)
  }
}
