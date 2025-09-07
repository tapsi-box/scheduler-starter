package box.tapsi.libs.scheduler.scheduler.factories

import box.tapsi.libs.scheduler.quartz.services.QuartzService
import box.tapsi.libs.scheduler.scheduler.SchedulingInstruction
import box.tapsi.libs.scheduler.scheduler.getCompositeJobId
import box.tapsi.libs.scheduler.scheduler.jobs.DefaultSchedulerJob
import box.tapsi.libs.scheduler.scheduler.schedulers.Scheduler
import box.tapsi.libs.scheduler.scheduler.toJobDataMap
import org.quartz.JobDataMap
import org.quartz.JobDetail
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

@Component
class QuartzJobDetailFactory(
  private val quartzService: QuartzService,
  private val applicationContext: ApplicationContext,
) {
  fun createQuartzJobDetail(schedulingInstruction: SchedulingInstruction): JobDetail {
    val jobDataMap = schedulingInstruction.jobStore.toJobDataMap()
    putRegularSchedulerClassInJobDataMap(jobDataMap, schedulingInstruction.scheduler)
    return quartzService.createJob(
      jobClass = DefaultSchedulerJob::class.java,
      isDurable = false,
      jobName = schedulingInstruction.getCompositeJobId(),
      jobGroup = schedulingInstruction.jobGroup.value,
      jobDataMap = jobDataMap,
    )
  }

  private fun putRegularSchedulerClassInJobDataMap(
    jobDataMap: JobDataMap,
    scheduler: KClass<out Scheduler>,
  ) {
    jobDataMap[SCHEDULER_JOB_STORE_KEY_MAP] =
      applicationContext.getBeansOfType(scheduler.java).keys.first()
  }

  companion object {
    const val SCHEDULER_JOB_STORE_KEY_MAP = "scheduler"
  }
}
