package box.tapsi.libs.scheduler.quartz

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class QuartzHelperTest {
  @Test
  fun `triggerHelper should find rescheduled count from trigger id`() {
    // given
    val triggerId = "test_rescheduled_1"

    // when

    // verify
    assertEquals(1, QuartzHelper.TriggerHelper.getRescheduledCountFromTriggerId(triggerId))
  }

  @Test
  fun `triggerHelper should return zero when trigger id is not rescheduled`() {
    // given
    val triggerId = "test"

    // when

    // verify
    assertEquals(0, QuartzHelper.TriggerHelper.getRescheduledCountFromTriggerId(triggerId))
  }

  @Test
  fun `triggerHelper should find trigger id from rescheduled trigger id`() {
    // given
    val triggerId = "test_rescheduled_1"

    // when

    // verify
    assertEquals("test", QuartzHelper.TriggerHelper.getTriggerIdFromRescheduledTriggerId(triggerId))
  }

  @Test
  fun `triggerHelper should return same trigger id when trigger id is not rescheduled`() {
    // given
    val triggerId = "test"

    // when

    // verify
    assertEquals(triggerId, QuartzHelper.TriggerHelper.getTriggerIdFromRescheduledTriggerId(triggerId))
  }
}
