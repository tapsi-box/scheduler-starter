package box.tapsi.libs.scheduler.scheduler.schedulers

import box.tapsi.libs.scheduler.scheduler.SchedulerException
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import reactor.core.publisher.Mono
import java.time.Instant

abstract class RegularScheduler(
  protected val schedulerService: SchedulerService,
) : DefaultScheduler() {

  override fun schedule(jobStore: JobStore?, fireTimestamp: Instant?): Mono<Void> {
    if (jobStore == null || fireTimestamp == null) {
      return Mono.error(SchedulerException.NoJobStoreFoundException(getJobGroup().value))
    }
    return scheduleRegularJob(jobStore, fireTimestamp)
  }

  protected open fun scheduleRegularJob(jobStore: JobStore, fireTimestamp: Instant): Mono<Void> = Mono.fromSupplier {
    SchedulingInstruction.Regular(
      fireTimestamp = fireTimestamp,
      jobStore = jobStore,
      scheduler = this::class,
      jobId = createJobId(jobStore),
      jobGroup = getJobGroup(),
      triggerGroup = getTriggerGroup(),
    )
  }.flatMap { instruction ->
    schedulerService.scheduleRegularJob(instruction)
  }
}
