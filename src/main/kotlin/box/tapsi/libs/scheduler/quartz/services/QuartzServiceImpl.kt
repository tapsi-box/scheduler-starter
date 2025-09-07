package box.tapsi.libs.scheduler.quartz.services

import box.tapsi.libs.scheduler.quartz.annotations.OnQuartzEnabled
import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import org.quartz.Job
import org.quartz.JobBuilder
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.JobListener
import org.quartz.SchedulerListener
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.quartz.TriggerListener
import org.quartz.impl.matchers.GroupMatcher
import org.quartz.plugins.history.LoggingJobHistoryPlugin
import org.quartz.plugins.history.LoggingTriggerHistoryPlugin
import org.slf4j.Logger
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.quartz.SchedulerFactoryBean
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import box.tapsi.libs.scheduler.quartz.annotations.JobListener as AnnotationsJobListener
import box.tapsi.libs.scheduler.quartz.annotations.SchedulerListener as AnnotationSchedulerListener
import box.tapsi.libs.scheduler.quartz.annotations.TriggerListener as AnnotationTriggerListener

@Service
@OnQuartzEnabled
@Primary
class QuartzServiceImpl(
  private val logger: Logger,
  private val applicationContext: ApplicationContext,
  private val schedulerFactoryBean: SchedulerFactoryBean,
  private val quartzRegistry: QuartzRegistry,
) : QuartzService,
  InitializingBean {
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

  override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Mono<Void> = Mono.fromRunnable<Void?> {
    schedulerFactoryBean.scheduler.scheduleJob(jobDetail, trigger)
  }.transform { quartzRegistry.exposeSchedulingMetrics(it, jobDetail) }
    .doOnSuccess {
      logger.info(
        "Job scheduled successfully with job name: ${jobDetail.key} and trigger: ${trigger.nextFireTime}",
      )
    }.doOnError {
      logger.error(
        "Error in job scheduling with job name: ${jobDetail.key} and trigger: ${trigger.nextFireTime}",
        it,
      )
    }

  override fun deleteJob(jobKey: JobKey): Mono<Void> = Mono.fromRunnable<Void?> {
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
    LoggingJobHistoryPlugin().initialize("LoggingJobHistoryPlugin", schedulerFactoryBean.scheduler, null)
    LoggingTriggerHistoryPlugin().initialize("LoggingTriggerHistoryPlugin", schedulerFactoryBean.scheduler, null)
  }

  private fun getJobListeners(applicationContext: ApplicationContext): List<JobListener> = applicationContext
    .getBeansWithAnnotation(AnnotationsJobListener::class.java)
    .values
    .map {
      (it as JobListener).also { jobListener ->
        logger.info("Found job listener: ${jobListener.name}")
      }
    }

  private fun getSchedulerListeners(
    applicationContext: ApplicationContext,
  ): List<SchedulerListener> = applicationContext
    .getBeansWithAnnotation(AnnotationSchedulerListener::class.java)
    .values
    .map {
      (it as SchedulerListener).also { schedulerListener ->
        logger.info("Found scheduler listener: ${schedulerListener::class.simpleName}")
      }
    }

  private fun getTriggerListeners(applicationContext: ApplicationContext): List<TriggerListener> = applicationContext
    .getBeansWithAnnotation(AnnotationTriggerListener::class.java)
    .values
    .map {
      (it as TriggerListener).also { triggerListener ->
        logger.info("Found trigger listener: ${triggerListener.name}")
      }
    }
}
