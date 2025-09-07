package box.tapsi.libs.scheduler.scheduler

import box.tapsi.libs.scheduler.scheduler.store.JobStore
import box.tapsi.libs.utilities.TapsiException

sealed class SchedulerException(message: String) : TapsiException(message) {
  class ExhaustedJobRetryException(message: String) : SchedulerException(message)

  data class NoSchedulerKeyFoundException(val key: String, val jobStore: JobStore) :
    SchedulerException(
      "No scheduler key found for $key in $jobStore.",
    )

  data class NoJobStoreFoundException(val jobKey: String) :
    SchedulerException(
      "No job store found for $jobKey.",
    )
}
