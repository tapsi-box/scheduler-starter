package box.tapsi.libs.scheduler.quartz.metric.listeners

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.quartz.TriggerListener
import org.slf4j.Logger
import java.time.Instant
import box.tapsi.libs.scheduler.quartz.annotations.TriggerListener as AnnotationsTriggerListener

@AnnotationsTriggerListener
class QuartzMisfireTriggerListener(
  private val quartzRegistry: QuartzRegistry,
  private val logger: Logger,
) : TriggerListener {
  override fun getName(): String = QuartzMisfireTriggerListener::class.java.simpleName

  override fun triggerFired(trigger: Trigger?, context: JobExecutionContext?) {
    // NO_OP
  }

  override fun vetoJobExecution(trigger: Trigger?, context: JobExecutionContext?): Boolean = false

  override fun triggerMisfired(trigger: Trigger?) {
    trigger?.let {
      logger.info("Trigger misfired with trigger name: ${it.key} and expected fire time: ${it.nextFireTime}")
      quartzRegistry.exposeMisfireMetrics(
        it.nextFireTime.time,
        Instant.now().toEpochMilli(),
        it.key,
      )
    }
  }

  override fun triggerComplete(
    trigger: Trigger?,
    context: JobExecutionContext?,
    triggerInstructionCode: Trigger.CompletedExecutionInstruction?,
  ) {
    // NO_OP
  }
}
