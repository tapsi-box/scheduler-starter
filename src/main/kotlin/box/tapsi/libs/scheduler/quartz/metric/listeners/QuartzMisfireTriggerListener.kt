package box.tapsi.libs.scheduler.quartz.metric.listeners

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import org.quartz.JobExecutionContext
import org.quartz.Trigger
import org.quartz.TriggerListener
import org.slf4j.LoggerFactory
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import java.time.Instant
import box.tapsi.libs.scheduler.quartz.annotations.TriggerListener as AnnotationsTriggerListener

// @AnnotationsTriggerListener stays on the class. QuartzServiceImpl finds trigger listeners
// with getBeansWithAnnotation, so this annotation is the discovery marker, not a stereotype.
@AnnotationsTriggerListener
class QuartzMisfireTriggerListener(
  private val quartzRegistry: QuartzRegistry,
  private val schedulerFactoryBean: SchedulerFactoryBean,
) : TriggerListener {
  private val logger = LoggerFactory.getLogger(QuartzMisfireTriggerListener::class.java)

  override fun getName(): String = QuartzMisfireTriggerListener::class.java.simpleName

  override fun triggerFired(trigger: Trigger?, context: JobExecutionContext?) {
    // NO_OP
  }

  override fun vetoJobExecution(trigger: Trigger?, context: JobExecutionContext?): Boolean = false

  override fun triggerMisfired(trigger: Trigger?) {
    trigger?.let {
      logger.info("Trigger misfired with trigger name: ${it.key} and expected fire time: ${it.nextFireTime}")
      quartzRegistry.exposeMisfireMetrics(
        scheduledFireTimeEpochMillis = it.nextFireTime.time,
        actualFireTimeEpochMillis = Instant.now().toEpochMilli(),
        job = resolveJob(it),
        jobGroup = it.jobKey.group,
        triggerKey = it.key,
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

  /**
   * Resolves the stable job identifier (scheduler bean name) for a misfired trigger by looking up
   * its job detail. The lookup runs only on the rare misfire event. It falls back to a bounded
   * placeholder if the job is gone, so a deleted one-shot job cannot reintroduce cardinality.
   */
  @Suppress("TooGenericExceptionCaught")
  private fun resolveJob(trigger: Trigger): String = try {
    schedulerFactoryBean.scheduler.getJobDetail(trigger.jobKey)
      ?.jobDataMap
      ?.getString(QuartzJobDetailFactory.SCHEDULER_JOB_STORE_KEY_MAP)
      ?: QuartzRegistry.UNKNOWN_JOB
  } catch (exception: Exception) {
    logger.error("Error resolving job for misfired trigger ${trigger.key}", exception)
    QuartzRegistry.UNKNOWN_JOB
  }
}
