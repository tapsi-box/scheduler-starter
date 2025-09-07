package box.tapsi.libs.scheduler.quartz.metric.listeners

import box.tapsi.libs.scheduler.quartz.QuartzHelper
import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.SchedulerException
import org.quartz.SchedulerListener
import org.quartz.Trigger
import org.quartz.TriggerKey
import box.tapsi.libs.scheduler.quartz.annotations.SchedulerListener as AnnotationsSchedulerListener

@AnnotationsSchedulerListener
@Suppress("TooManyFunctions")
class QuartzPendingJobRegistry(
  private val quartzRegistry: QuartzRegistry,
) : SchedulerListener {
  override fun jobScheduled(trigger: Trigger?) {
    trigger?.let {
      val triggerKey =
        TriggerKey.triggerKey(
          QuartzHelper.TriggerHelper.getTriggerIdFromRescheduledTriggerId(it.key.name),
          it.key.group,
        )
      quartzRegistry.incrementPendingJob(triggerKey)
    }
  }

  override fun jobUnscheduled(p0: TriggerKey?) {
    // NO_OP
  }

  override fun triggerFinalized(p0: Trigger?) {
    // NO_OP
  }

  override fun triggerPaused(triggerKey: TriggerKey?) {
    // NO_OP
  }

  override fun triggersPaused(triggerGroup: String?) {
    // NO_OP
  }

  override fun triggerResumed(triggerKey: TriggerKey?) {
    // NO_OP
  }

  override fun triggersResumed(triggerGroup: String?) {
    // NO_OP
  }

  override fun jobAdded(jobDetail: JobDetail?) {
    // NO_OP
  }

  override fun jobDeleted(jobKey: JobKey?) {
    // NO_OP
  }

  override fun jobPaused(jobKey: JobKey?) {
    // NO_OP
  }

  override fun jobsPaused(jobGroup: String?) {
    // NO_OP
  }

  override fun jobResumed(jobKey: JobKey?) {
    // NO_OP
  }

  override fun jobsResumed(jobGroup: String?) {
    // NO_OP
  }

  override fun schedulerError(msg: String?, cause: SchedulerException?) {
    // NO_OP
  }

  override fun schedulerInStandbyMode() {
    // NO_OP
  }

  override fun schedulerStarted() {
    // NO_OP
  }

  override fun schedulerStarting() {
    // NO_OP
  }

  override fun schedulerShutdown() {
    // NO_OP
  }

  override fun schedulerShuttingdown() {
    // NO_OP
  }

  override fun schedulingDataCleared() {
    // NO_OP
  }
}
