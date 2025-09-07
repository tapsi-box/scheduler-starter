package box.tapsi.libs.scheduler.scheduler.schedulers

import box.tapsi.libs.scheduler.scheduler.JobGroup
import box.tapsi.libs.scheduler.scheduler.TriggerGroup
import box.tapsi.libs.scheduler.scheduler.aop.interceptors.RetryJobInterceptor
import box.tapsi.libs.scheduler.scheduler.store.JobStore

abstract class DefaultScheduler : Scheduler {
  abstract fun createJobId(jobStore: JobStore): String
  abstract fun getJobGroup(): JobGroup
  abstract fun getTriggerGroup(): TriggerGroup

  protected fun getRetriedCount(
    jobStore: JobStore,
  ): Int? = jobStore.getInt(RetryJobInterceptor.RETRY_COUNT_JOB_STORE_KEY)
}
