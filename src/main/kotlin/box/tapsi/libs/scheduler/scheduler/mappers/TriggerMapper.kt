package box.tapsi.libs.scheduler.scheduler.mappers

import box.tapsi.libs.scheduler.quartz.QuartzTriggerBuilder
import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import java.time.Duration
import org.quartz.CronTrigger as QuartzCronTrigger
import org.quartz.SimpleTrigger as QuartzSimpleTrigger
import org.quartz.Trigger as QuartzTrigger

object TriggerMapper {
  fun toModel(trigger: Trigger): QuartzTrigger = when (trigger) {
    is Trigger.SimpleTrigger -> SimpleTriggerMapper.toModel(trigger)
    is Trigger.CronTrigger -> CronTriggerMapper.toModel(trigger)
  }

  fun fromModel(quartzTrigger: QuartzTrigger): Trigger = when (quartzTrigger) {
    is QuartzSimpleTrigger -> SimpleTriggerMapper.fromModel(quartzTrigger)
    is QuartzCronTrigger -> CronTriggerMapper.fromModel(quartzTrigger)
    else -> error("Unknown trigger type: ${quartzTrigger::class.java}")
  }

  private object SimpleTriggerMapper {
    fun toModel(trigger: Trigger.SimpleTrigger): QuartzTrigger = QuartzTriggerBuilder.Simple(
      triggerName = trigger.triggerId,
      triggerGroup = trigger.triggerGroup.value,
      startTime = trigger.startTimestamp,
      repeatInterval = trigger.repeatIntervalDuration.toMillis(),
      repeatCount = trigger.repeatCount,
    ).build()

    fun fromModel(quartzTrigger: QuartzSimpleTrigger): Trigger.SimpleTrigger = Trigger.SimpleTrigger(
      jobGroup = JobGroup.fromString(quartzTrigger.jobKey.group),
      jobId = quartzTrigger.jobKey.name,
      triggerGroup = TriggerGroup.fromString(quartzTrigger.key.group),
      triggerId = quartzTrigger.key.name,
      startTimestamp = quartzTrigger.startTime.toInstant(),
    ).apply {
      nextFireTimestamp = quartzTrigger.nextFireTime?.toInstant()
      misFireInstruction = quartzTrigger.misfireInstruction
      repeatCount = quartzTrigger.repeatCount
      repeatIntervalDuration = Duration.ofMillis(quartzTrigger.repeatInterval)
    }
  }

  private object CronTriggerMapper {
    fun toModel(trigger: Trigger.CronTrigger): QuartzTrigger = QuartzTriggerBuilder.Cron(
      triggerName = trigger.triggerId,
      triggerGroup = trigger.triggerGroup.value,
      startTime = trigger.startTimestamp,
      cronExpression = trigger.cronExpression,
    ).build()

    fun fromModel(quartzTrigger: QuartzCronTrigger): Trigger.CronTrigger = Trigger.CronTrigger(
      jobGroup = JobGroup.fromString(quartzTrigger.jobKey.group),
      jobId = quartzTrigger.jobKey.name,
      triggerGroup = TriggerGroup.fromString(quartzTrigger.key.group),
      triggerId = quartzTrigger.key.name,
      startTimestamp = quartzTrigger.startTime.toInstant(),
      cronExpression = quartzTrigger.cronExpression,
    ).apply {
      nextFireTimestamp = quartzTrigger.nextFireTime.toInstant()
      misFireInstruction = quartzTrigger.misfireInstruction
    }
  }
}
