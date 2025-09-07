package box.tapsi.libs.scheduler.scheduler

import box.tapsi.libs.scheduler.scheduler.store.JobStore
import org.quartz.JobDataMap

fun JobStore.toJobDataMap(): JobDataMap = JobDataMap()
  .apply {
    this@toJobDataMap.getAll().forEach { (key, value) ->
      this[key] = value
    }
  }

fun JobDataMap.toJobStore(): JobStore = JobStore()
  .apply {
    this@toJobStore.forEach { (key, value) ->
      this.put(key, value)
    }
  }
