package box.tapsi.libs.scheduler.quartz

import box.tapsi.libs.utilities.time.toDate
import org.quartz.CronScheduleBuilder
import org.quartz.SimpleScheduleBuilder
import org.quartz.Trigger
import org.quartz.TriggerBuilder
import java.time.Instant

sealed class QuartzTriggerBuilder(
  val triggerName: String,
  val triggerGroup: String,
  val startTime: Instant,
) {
  abstract fun build(): Trigger

  class Cron(
    triggerName: String,
    triggerGroup: String,
    startTime: Instant,
    val cronExpression: String,
  ) : QuartzTriggerBuilder(triggerName, triggerGroup, startTime) {
    override fun build(): Trigger = TriggerBuilder.newTrigger()
      .withIdentity(this.triggerName, this.triggerGroup)
      .startAt(this.startTime.toDate())
      .withSchedule(
        CronScheduleBuilder.cronSchedule(this.cronExpression),
      )
      .build()
  }

  class Simple(
    triggerName: String,
    triggerGroup: String,
    startTime: Instant,
    val repeatInterval: Long,
    val repeatCount: Int,
  ) : QuartzTriggerBuilder(triggerName, triggerGroup, startTime) {
    override fun build(): Trigger = TriggerBuilder.newTrigger()
      .withIdentity(this.triggerName, this.triggerGroup)
      .startAt(this.startTime.toDate())
      .withSchedule(
        SimpleScheduleBuilder.simpleSchedule()
          .withIntervalInMilliseconds(this.repeatInterval)
          .withRepeatCount(this.repeatCount),
      ).build()
  }
}
