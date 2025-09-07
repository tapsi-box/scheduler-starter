package box.tapsi.libs.scheduler.scheduler

import java.time.Duration
import java.time.Instant

interface TriggerGroup {
  val value: String

  companion object {
    fun fromString(value: String): TriggerGroup = object : TriggerGroup {
      override val value: String = value
      override fun toString(): String = value
    }
  }
}

sealed class Trigger {
  abstract val jobGroup: JobGroup
  abstract val jobId: String
  abstract val triggerGroup: TriggerGroup
  abstract val triggerId: String
  abstract val startTimestamp: Instant

  var misFireInstruction: Int = 1
  var nextFireTimestamp: Instant? = null
    get() = field ?: startTimestamp

  data class SimpleTrigger(
    override val jobGroup: JobGroup,
    override val jobId: String,
    override val triggerGroup: TriggerGroup,
    override val triggerId: String,
    override val startTimestamp: Instant,
    var repeatIntervalDuration: Duration = Duration.ofSeconds(1),
    var repeatCount: Int = 0,
  ) : Trigger()

  data class CronTrigger(
    override val jobGroup: JobGroup,
    override val jobId: String,
    override val triggerGroup: TriggerGroup,
    override val triggerId: String,
    override val startTimestamp: Instant,
    val cronExpression: String,
  ) : Trigger()
}
