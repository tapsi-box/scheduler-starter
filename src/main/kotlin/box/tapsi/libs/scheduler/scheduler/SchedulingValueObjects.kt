package box.tapsi.libs.scheduler.scheduler

import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.RegularScheduler
import box.tapsi.libs.scheduler.scheduler.schedulers.Scheduler
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import java.time.Instant
import kotlin.reflect.KClass

sealed class SchedulingInstruction {
  abstract val jobStore: JobStore
  abstract val jobId: String
  abstract val retriedCount: Int?
  abstract val jobGroup: JobGroup
  abstract val triggerGroup: TriggerGroup
  abstract val scheduler: KClass<out Scheduler>

  data class Regular(
    val fireTimestamp: Instant,
    override val scheduler: KClass<out RegularScheduler>,
    override val jobStore: JobStore,
    override val jobId: String,
    override val retriedCount: Int?,
    override val jobGroup: JobGroup,
    override val triggerGroup: TriggerGroup,
  ) : SchedulingInstruction()

  data class Cron(
    val fireTimestamp: Instant?,
    val cronExpression: String,
    override val scheduler: KClass<out CronScheduler>,
    override val jobStore: JobStore,
    override val jobId: String,
    override val retriedCount: Int?,
    override val jobGroup: JobGroup,
    override val triggerGroup: TriggerGroup,
  ) : SchedulingInstruction()
}
