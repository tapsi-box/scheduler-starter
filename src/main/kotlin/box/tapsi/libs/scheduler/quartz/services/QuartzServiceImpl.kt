package box.tapsi.libs.scheduler.quartz.services

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobListener
import org.quartz.ObjectAlreadyExistsException
import org.quartz.SchedulerListener
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.TriggerListener
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.plugins.history.LoggingJobHistoryPlugin
import org.quartz.plugins.history.LoggingTriggerHistoryPlugin
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import box.tapsi.libs.scheduler.quartz.annotations.JobListener as AnnotationsJobListener
import box.tapsi.libs.scheduler.quartz.annotations.SchedulerListener as AnnotationSchedulerListener
import box.tapsi.libs.scheduler.quartz.annotations.TriggerListener as AnnotationTriggerListener

@Suppress("TooManyFunctions")
class QuartzServiceImpl(
  private val applicationContext: ApplicationContext,
  private val schedulerFactoryBean: SchedulerFactoryBean,
  private val quartzRegistry: QuartzRegistry,
  private val historyLoggingEnabled: Boolean = false,
) : QuartzService,
  InitializingBean {
  private val logger = LoggerFactory.getLogger(QuartzServiceImpl::class.java)

  override fun <TJob : Job> createJob(
    jobClass: Class<TJob>,
    isDurable: Boolean,
    jobName: String,
    jobGroup: String,
    jobDataMap: JobDataMap,
  ): JobDetail = JobBuilder.newJob(jobClass)
    .apply {
      jobDataMap.put(jobName + jobGroup, jobClass.name)
      setJobData(jobDataMap)
    }.withIdentity(jobName, jobGroup)
    .storeDurably(isDurable)
    .requestRecovery()
    .build()

  override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Mono<Void> = Mono.defer {
    val startNanos = System.nanoTime()
    Mono.fromRunnable<Void?> {
      schedulerFactoryBean.scheduler.scheduleJob(jobDetail, trigger)
    }.doOnSuccess {
      quartzRegistry.recordScheduling(
        jobDetail,
        trigger,
        QuartzRegistry.SchedulingOutcome.Success,
        elapsedMillis(startNanos),
      )
      logger.info(
        "Job scheduled successfully with job name: ${jobDetail.key} and trigger: ${trigger.nextFireTime}",
      )
    }.doOnError { throwable ->
      val outcome = if (throwable is ObjectAlreadyExistsException) {
        QuartzRegistry.SchedulingOutcome.AlreadyExists
      } else {
        QuartzRegistry.SchedulingOutcome.Failure
      }
      quartzRegistry.recordScheduling(jobDetail, trigger, outcome, elapsedMillis(startNanos))
      // The already-exists case is expected for cron re-registration and is logged by the caller,
      // so only real failures are logged here to avoid a duplicate log line on every pod start.
      if (outcome != QuartzRegistry.SchedulingOutcome.AlreadyExists) {
        logger.error(
          "Error in job scheduling with job name: ${jobDetail.key} and trigger: ${trigger.nextFireTime}",
          throwable,
        )
      }
    }
  }

  override fun scheduleTrigger(trigger: Trigger): Mono<Void> = Mono.fromCallable {
    schedulerFactoryBean.scheduler.scheduleJob(trigger)
  }.doOnNext {
    logger.info("Trigger ${trigger.key} added to job ${trigger.jobKey} with next fire time: $it")
  }.doOnError {
    logger.error("Error adding trigger ${trigger.key} to job ${trigger.jobKey}", it)
  }.then()

  private fun elapsedMillis(startNanos: Long): Long = (System.nanoTime() - startNanos) / NANOS_PER_MILLI

  override fun deleteJob(jobKey: JobKey): Mono<Void> = Mono.fromRunnable<Void> {
    schedulerFactoryBean.scheduler.deleteJob(jobKey)
  }.doOnSuccess {
    logger.info("Job deleted successfully with job name: ${jobKey.name} and group: ${jobKey.group}")
  }.doOnError {
    logger.error("Error in a job deletion with job name: ${jobKey.name} and group: ${jobKey.group}", it)
  }

  override fun getTriggers(triggerGroup: String): Flux<Trigger> = Mono.fromCallable {
    schedulerFactoryBean.scheduler.getTriggerKeys(GroupMatcher.groupEquals(triggerGroup))
  }.flatMapIterable { it }
    .flatMap { Mono.fromCallable { schedulerFactoryBean.scheduler.getTrigger(it) } }
    .doOnNext {
      logger.info("Trigger found with trigger name: ${it.key.name} and group: ${it.key.group}")
    }.doOnError {
      logger.error("Error in finding triggers in group $triggerGroup", it)
    }

  override fun rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): Mono<Void> = Mono.fromCallable {
    schedulerFactoryBean.scheduler.rescheduleJob(triggerKey, trigger)
  }.doOnNext {
    logger.info("Job rescheduled successfully with trigger: $triggerKey and next fire time: $it")
  }.doOnError {
    logger.error("Error in a job rescheduling with trigger: $triggerKey", it)
  }.then()

  override fun afterPropertiesSet() {
    getJobListeners(applicationContext).forEach {
      schedulerFactoryBean.scheduler.listenerManager.addJobListener(it)
    }
    getSchedulerListeners(applicationContext).forEach {
      schedulerFactoryBean.scheduler.listenerManager.addSchedulerListener(it)
    }
    getTriggerListeners(applicationContext).forEach {
      schedulerFactoryBean.scheduler.listenerManager.addTriggerListener(it)
    }
    installHistoryLoggingPlugins()
  }

  /**
   * Installs the Quartz history logging plugins. They log a line for every job fire and every
   * trigger fire, which is noisy in a service with many jobs. They stay off unless the consumer
   * sets `box.libs.scheduler.quartz.history-logging-enabled` to `true`.
   */
  private fun installHistoryLoggingPlugins() {
    if (!historyLoggingEnabled) {
      return
    }
    LoggingJobHistoryPlugin().initialize("LoggingJobHistoryPlugin", schedulerFactoryBean.scheduler, null)
    LoggingTriggerHistoryPlugin().initialize("LoggingTriggerHistoryPlugin", schedulerFactoryBean.scheduler, null)
  }

  private fun getJobListeners(applicationContext: ApplicationContext): List<JobListener> = applicationContext
    .getBeansWithAnnotation<AnnotationsJobListener>()
    .values
    .map {
      (it as JobListener).also { jobListener ->
        logger.info("Found job listener: ${jobListener.name}")
      }
    }

  private fun getSchedulerListeners(
    applicationContext: ApplicationContext,
  ): List<SchedulerListener> = applicationContext
    .getBeansWithAnnotation<AnnotationSchedulerListener>()
    .values
    .map {
      (it as SchedulerListener).also { schedulerListener ->
        logger.info("Found scheduler listener: ${schedulerListener::class.simpleName}")
      }
    }

  private fun getTriggerListeners(applicationContext: ApplicationContext): List<TriggerListener> = applicationContext
    .getBeansWithAnnotation<AnnotationTriggerListener>()
    .values
    .map {
      (it as TriggerListener).also { triggerListener ->
        logger.info("Found trigger listener: ${triggerListener.name}")
      }
    }

  companion object {
    private const val NANOS_PER_MILLI = 1_000_000L
  }
}
