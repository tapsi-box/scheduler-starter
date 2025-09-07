package box.tapsi.libs.scheduler.quartz.services

import org.quartz.Job
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.quartz.JobKey
import org.quartz.Trigger
import org.quartz.TriggerKey
import org.slf4j.Logger
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.quartz.JobDetailFactoryBean
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
@ConditionalOnMissingBean(QuartzServiceImpl::class)
class QuartzServiceMockImpl(
  private val logger: Logger,
  private val applicationContext: ApplicationContext,
) : QuartzService {
  override fun <TJob : Job> createJob(
    jobClass: Class<TJob>,
    isDurable: Boolean,
    jobName: String,
    jobGroup: String,
    jobDataMap: JobDataMap,
  ): JobDetail = JobDetailFactoryBean()
    .also {
      logger.info("Creating a job with name: $jobName and group: $jobGroup in mock implementation")
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
      logger.info("Job created successfully with name: $jobName and group: $jobGroup in mock implementation")
    }.let {
      it.`object`!!
    }

  override fun scheduleJob(jobDetail: JobDetail, trigger: Trigger): Mono<Void> = Mono.empty<Void>()
    .doOnSuccess {
      logger.info(
        "Job scheduled successfully with job name: " +
          "${jobDetail.key} and trigger: ${trigger.startTime} in mock implementation",
      )
    }

  override fun deleteJob(jobKey: JobKey): Mono<Void> = Mono.empty<Void>()
    .doOnSuccess {
      logger.info("Job deleted successfully with job name: $jobKey in mock implementation")
    }

  override fun rescheduleJob(triggerKey: TriggerKey, trigger: Trigger): Mono<Void> = Mono.empty<Void>().doOnSuccess {
    logger.info("Job rescheduled successfully with trigger name: ${trigger.key} in mock implementation")
  }

  override fun getTriggers(triggerGroup: String): Flux<Trigger> {
    logger.info("Getting triggers for the group: $triggerGroup in mock implementation")
    return Flux.empty()
  }
}
