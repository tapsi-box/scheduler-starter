package box.tapsi.libs.scheduler.quartz

object QuartzHelper {
  object TriggerHelper {
    private const val RESCHEDULED_TRIGGER_ID_FORMAT = "%s_rescheduled_%s"

    fun prepareRescheduledTriggerId(triggerId: String, newTriggerId: String?): String {
      val rescheduledCount = getRescheduledCountFromTriggerId(triggerId)
      val newRescheduledCount = rescheduledCount + 1
      val newTriggerIdPrefix = newTriggerId ?: getTriggerIdFromRescheduledTriggerId(triggerId)
      return RESCHEDULED_TRIGGER_ID_FORMAT.format(newTriggerIdPrefix, newRescheduledCount.toString())
    }

    fun getTriggerIdFromRescheduledTriggerId(rescheduledTriggerId: String): String {
      val regex = Regex("""(.*)_rescheduled_\d+""")
      val matchResult = regex.matchEntire(rescheduledTriggerId)
      return matchResult?.groupValues?.get(1) ?: rescheduledTriggerId
    }

    internal fun getRescheduledCountFromTriggerId(triggerId: String): Int {
      val regex = Regex(""".*_rescheduled_(\d+)""")
      val matchResult = regex.matchEntire(triggerId)
      return matchResult?.groupValues?.get(1)?.toInt() ?: 0
    }
  }
}
