package box.tapsi.libs.scheduler.scheduler.services

import box.tapsi.libs.metrics.core.annotations.ReactiveTimed
import box.tapsi.libs.scheduler.quartz.services.QuartzService
import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import box.tapsi.libs.scheduler.scheduler.factories.QuartzTriggerFactory
import box.tapsi.libs.scheduler.scheduler.getCompositeJobId
import org.quartz.CronExpression
import org.quartz.JobKey
import org.quartz.ObjectAlreadyExistsException
import org.quartz.TriggerKey
import org.slf4j.Logger
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
@ReactiveTimed
class SchedulerServiceImpl(
  private val logger: Logger,
  private val quartzTriggerFactory: QuartzTriggerFactory,
  private val quartzJobDetailFactory: QuartzJobDetailFactory,
  private val quartzService: QuartzService,
) : SchedulerService {
  override fun scheduleRegularJob(
    instruction: SchedulingInstruction.Regular,
  ): Mono<Void> = Mono.defer {
    val jobDetail = quartzJobDetailFactory.createQuartzJobDetail(instruction)
    val quartzTrigger = quartzTriggerFactory.createQuartzTrigger(instruction)
    logger.info("Scheduling a regular job with id ${jobDetail.key} at ${instruction.fireTimestamp}")
    return@defer quartzService.scheduleJob(jobDetail, quartzTrigger)
      .doOnSuccess {
        logger.info("Scheduled regular job with id ${jobDetail.key} at ${instruction.fireTimestamp}")
      }.doOnError {
        logger.error(
          "Error scheduling a regular job with id ${jobDetail.key} at ${instruction.fireTimestamp}",
          it,
        )
      }
  }

  override fun getTriggers(triggerGroup: TriggerGroup): Flux<Trigger> = quartzService.getTriggers(triggerGroup.value)
    .map { quartzTriggerFactory.createTrigger(quartzTrigger = it) }

  override fun reschedule(triggerId: String, trigger: Trigger): Mono<Void> = Mono.defer {
    val triggerKey = TriggerKey.triggerKey(triggerId, trigger.triggerGroup.value)
    val quartzTrigger = quartzTriggerFactory.createQuartzTrigger(trigger)
    return@defer quartzService.rescheduleJob(triggerKey, quartzTrigger)
  }.doOnSuccess {
    logger.info(
      "Rescheduled trigger with id $triggerId in " +
        "group ${trigger.triggerGroup.value} for ${trigger.startTimestamp}",
    )
  }.doOnError {
    logger.error(
      "Error rescheduling trigger with id $triggerId in " +
        "group ${trigger.triggerGroup.value} for ${trigger.startTimestamp}",
      it,
    )
  }

  override fun scheduleCronJob(
    instruction: SchedulingInstruction.Cron,
  ): Mono<Void> = Mono.defer {
    val jobDetail = quartzJobDetailFactory.createQuartzJobDetail(instruction)
    logger.info(
      "Scheduling a cron job with id ${jobDetail.key} at " +
        CronExpression(instruction.cronExpression).expressionSummary,
    )
    val quartzTrigger = quartzTriggerFactory.createQuartzTrigger(instruction)
    return@defer quartzService.scheduleJob(jobDetail, quartzTrigger)
  }
    .doOnError(
      { it is ObjectAlreadyExistsException },
    ) { logger.info("Cron job with id ${instruction.getCompositeJobId()} already exists") }
    .onErrorComplete(ObjectAlreadyExistsException::class.java).doOnSuccess {
      logger.info(
        "Scheduled cron job with id ${instruction.getCompositeJobId()} " +
          "at ${CronExpression(instruction.cronExpression).expressionSummary}",
      )
    }.doOnError {
      logger.error(
        "Error scheduling cron job with id ${instruction.getCompositeJobId()} " +
          "at ${CronExpression(instruction.cronExpression).expressionSummary}",
        it,
      )
    }

  override fun deleteJob(jobId: String, jobGroup: JobGroup): Mono<Void> = Mono.just(jobId)
    .doOnNext {
      logger.info("Deleting the regular job with id $it")
    }.map {
      JobKey(jobId, jobGroup.value)
    }.flatMap { jobKey ->
      quartzService.deleteJob(jobKey)
    }.doOnSuccess {
      logger.info("Deleted the regular job with id $jobId")
    }.doOnError {
      logger.error("Error deleting the regular job with id $jobId", it)
    }
}
