package box.tapsi.libs.scheduler.scheduler.mappers

import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.getCompositeJobId
import box.tapsi.libs.utilities.time.TimeOperator

object SchedulingInstructionMapper {
  fun toModel(instruction: SchedulingInstruction, timeOperator: TimeOperator): Trigger = when (instruction) {
    is SchedulingInstruction.Regular -> RegularMapper.toModel(instruction)
    is SchedulingInstruction.Cron -> CronMapper.toModel(instruction, timeOperator)
  }

  private object RegularMapper {
    private const val SIMPLE_TRIGGER_TRIGGER_ID_FORMAT = "%s_trigger"

    fun toModel(instruction: SchedulingInstruction.Regular): Trigger = Trigger.SimpleTrigger(
      jobGroup = instruction.jobGroup,
      jobId = instruction.getCompositeJobId(),
      triggerGroup = instruction.triggerGroup,
      triggerId = SIMPLE_TRIGGER_TRIGGER_ID_FORMAT.format(instruction.getCompositeJobId()),
      startTimestamp = instruction.fireTimestamp,
    )
  }

  private object CronMapper {
    private const val CRON_TRIGGER_TRIGGER_ID_FORMAT = "%s_cron_trigger"

    fun toModel(
      instruction: SchedulingInstruction.Cron,
      timeOperator: TimeOperator,
    ): Trigger.CronTrigger = Trigger.CronTrigger(
      jobGroup = instruction.jobGroup,
      jobId = instruction.getCompositeJobId(),
      triggerGroup = instruction.triggerGroup,
      triggerId = CRON_TRIGGER_TRIGGER_ID_FORMAT.format(instruction.getCompositeJobId()),
      startTimestamp = instruction.fireTimestamp ?: timeOperator.getCurrentTime(),
      cronExpression = instruction.cronExpression,
    )
  }
}
