package box.tapsi.libs.scheduler.scheduler.factories

import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.Trigger
import box.tapsi.libs.scheduler.scheduler.mappers.SchedulingInstructionMapper
import box.tapsi.libs.scheduler.scheduler.mappers.TriggerMapper
import box.tapsi.libs.utilities.time.TimeOperator
import org.springframework.stereotype.Component
import org.quartz.Trigger as QuartzTrigger

@Component
class QuartzTriggerFactory(
  private val timeOperator: TimeOperator,
) {
  fun createQuartzTrigger(
    schedulingInstruction: SchedulingInstruction,
  ): QuartzTrigger = SchedulingInstructionMapper.toModel(schedulingInstruction, timeOperator)
    .let { TriggerMapper.toModel(it) }

  fun createQuartzTrigger(trigger: Trigger): QuartzTrigger = TriggerMapper.toModel(trigger)

  fun createTrigger(quartzTrigger: QuartzTrigger): Trigger = TriggerMapper.fromModel(quartzTrigger)
}
