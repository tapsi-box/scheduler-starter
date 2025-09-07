package box.tapsi.libs.scheduler.quartz.metric.listeners

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import org.quartz.JobExecutionContext
import org.quartz.JobExecutionException
import org.quartz.JobListener
import box.tapsi.libs.scheduler.quartz.annotations.JobListener as AnnotationsJobListener

@AnnotationsJobListener
class QuartzActiveJobListener(
  private val quartzRegistry: QuartzRegistry,
) : JobListener {
  override fun getName(): String = "QuartzActiveJobListener"

  override fun jobToBeExecuted(context: JobExecutionContext?) {
    context?.let { ctx ->
      quartzRegistry.incrementActiveJob(ctx)
    }
  }

  override fun jobExecutionVetoed(context: JobExecutionContext?) {
    // NO_OP
  }

  override fun jobWasExecuted(context: JobExecutionContext?, jobException: JobExecutionException?) {
    // NO_OP
  }
}
