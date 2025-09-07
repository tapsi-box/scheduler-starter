package box.tapsi.libs.scheduler.scheduler.schedulers

import box.tapsi.libs.scheduler.quartz.QuartzHelper
import box.tapsi.libs.scheduler.scheduler.SchedulerException
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.factories.QuartzTriggerFactory
import box.tapsi.libs.scheduler.scheduler.services.SchedulerService
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import box.tapsi.libs.utilities.reactor.monoDeferWithObject
import org.quartz.JobExecutionContext
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
      retriedCount = getRetriedCount(jobStore),
      jobGroup = getJobGroup(),
      triggerGroup = getTriggerGroup(),
    )
  }.flatMap { instruction ->
    schedulerService.scheduleRegularJob(instruction)
  }

  protected fun reschedule(
    nextFireTimestamp: Instant,
    newTriggerId: String? = null,
    quartzTriggerFactory: QuartzTriggerFactory,
  ): Mono<Void> = monoDeferWithObject(JobExecutionContext::class) { ctx ->
    val trigger = quartzTriggerFactory.createTrigger(ctx.trigger) as Trigger.SimpleTrigger
    val rescheduledTriggerId = QuartzHelper.TriggerHelper.prepareRescheduledTriggerId(trigger.triggerId, newTriggerId)
    val rescheduledTrigger = trigger.copy(
      triggerId = rescheduledTriggerId,
      startTimestamp = nextFireTimestamp,
    )
    return@monoDeferWithObject schedulerService.reschedule(trigger.triggerId, rescheduledTrigger)
  }
}
