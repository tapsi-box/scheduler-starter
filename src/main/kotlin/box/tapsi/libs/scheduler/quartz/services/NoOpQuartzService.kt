package box.tapsi.libs.scheduler.quartz.services

import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.quartz.JobDetailFactoryBean
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * The no-op [QuartzService]. It builds job details, but it never touches the Quartz scheduler.
 *
 * This is the active implementation when `box.libs.scheduler.quartz.enabled` is not `true`. It
 * lets an application keep its scheduler beans and its wiring while no job runs. It is production
 * behaviour, not a test double.
 */
class NoOpQuartzService(
  private val applicationContext: ApplicationContext,
) : QuartzService {
  private val logger = LoggerFactory.getLogger(NoOpQuartzService::class.java)

  override fun <TJob : Job> createJob(
    jobClass: Class<TJob>,
    isDurable: Boolean,
    jobName: String,
    jobGroup: String,
    jobDataMap: JobDataMap,
  ): JobDetail = JobDetailFactoryBean()
    .also {
      logger.info("Creating a job with name: $jobName and group: $jobGroup while Quartz is disabled")
    }
    .apply {
      setJobClass(jobClass)
      setDurability(isDurable)
      setApplicationContext(applicationContext)
      setName(jobName)
      setGroup(jobGroup)
      jobDataMap[jobName + jobGroup] = jobClass.name
      setJobDataAsMap(jobDataMap)
      afterPropertiesSet()
    }.also {
      logger.info("Job created successfully with name: $jobName and group: $jobGroup while Quartz is disabled")
    }.let {
      it.`object`!!
    }

  override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Mono<Void> = Mono.empty<Void>()
    .doOnSuccess {
      logger.info(
        "Job not scheduled with job name: " +
          "${jobDetail.key} and trigger: ${trigger.startTime} because Quartz is disabled",
      )
    }

  override fun scheduleTrigger(trigger: Trigger): Mono<Void> = Mono.empty<Void>().doOnSuccess {
    logger.info("Trigger ${trigger.key} not added to job ${trigger.jobKey} because Quartz is disabled")
  }

  override fun deleteJob(jobKey: JobKey): Mono<Void> = Mono.empty<Void>()
    .doOnSuccess {
      logger.info("Job not deleted with job name: $jobKey because Quartz is disabled")
    }

  override fun rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): Mono<Void> = Mono.empty<Void>().doOnSuccess {
    logger.info("Job not rescheduled with trigger name: ${trigger.key} because Quartz is disabled")
  }

  override fun getTriggers(triggerGroup: String): Flux<Trigger> {
    logger.info("Getting triggers for the group: $triggerGroup while Quartz is disabled")
    return Flux.empty()
  }
}
