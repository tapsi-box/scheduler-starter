package box.tapsi.libs.scheduler.scheduler.jobs

import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import box.tapsi.libs.scheduler.scheduler.SchedulerException
import box.tapsi.libs.scheduler.scheduler.factories.QuartzJobDetailFactory
import box.tapsi.libs.scheduler.scheduler.schedulers.DefaultScheduler
import box.tapsi.libs.scheduler.scheduler.store.JobStore
import box.tapsi.libs.scheduler.scheduler.toJobStore
import box.tapsi.libs.utilities.logging.addTraceIdToReactorContext
import box.tapsi.libs.utilities.reactor.withContextualObject
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.quartz.QuartzJobBean
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink
import reactor.kotlin.core.publisher.switchIfEmpty

@Component
class DefaultSchedulerJob(
  private val applicationContext: ApplicationContext,
  private val quartzRegistry: QuartzRegistry,
) : QuartzJobBean() {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun executeInternal(context: JobExecutionContext) {
    logger.info("Executing job ${context.jobDetail.key.name}")
    doExecute(context)
      .doOnSuccess {
        logger.info("scheduler for ${context.jobDetail?.key} executed successfully")
      }
      .onErrorResume(::couldIgnoreError) {
        logger.error("Error executing scheduler for ${context.jobDetail?.key}", it)
        Mono.empty()
      }
      .doOnError {
        logger.error("Internal Error executing scheduler for ${context.jobDetail?.key}", it)
      }
      .addTraceIdToReactorContext()
      .transform { quartzRegistry.exposeExecutionMetrics(it, context.jobDetail) }
      .block()
    logger.info("Job ${context.jobDetail.key.name} executed")
  }

  private fun doExecute(context: JobExecutionContext): Mono<Void> = Mono.defer {
    logger.info("default scheduler job executed for ${context.jobDetail?.key} at ${context.fireTime}")
    val jobStore = context.jobDetail?.jobDataMap?.toJobStore()
    return@defer Mono.justOrEmpty(jobStore)
  }.handle { jobStore, sink: SynchronousSink<JobStore> ->
    if (!jobStore.contains(QuartzJobDetailFactory.SCHEDULER_JOB_STORE_KEY_MAP)) {
      return@handle sink.error(
        SchedulerException.NoSchedulerKeyFoundException(
          QuartzJobDetailFactory.SCHEDULER_JOB_STORE_KEY_MAP,
          jobStore,
        ),
      )
    }
    return@handle sink.next(jobStore)
  }.switchIfEmpty { Mono.error(SchedulerException.NoJobStoreFoundException(context.jobDetail?.key!!.name)) }
    .flatMap { jobStore ->
      return@flatMap executeScheduler<DefaultScheduler>(context, jobStore)
    }

  private inline fun <reified TJob : DefaultScheduler> executeScheduler(
    context: JobExecutionContext,
    jobStore: JobStore,
  ): Mono<Void> = Mono.defer {
    val schedulerBeanName = jobStore.getString(QuartzJobDetailFactory.SCHEDULER_JOB_STORE_KEY_MAP)
      ?: throw IllegalArgumentException("no scheduler class found for ${context.jobDetail?.key}")
    val scheduler = applicationContext.getBean(schedulerBeanName, TJob::class.java)
    return@defer Mono.just(scheduler)
  }.doOnNext {
    logger.info("Scheduler class found for ${context.jobDetail?.key} is ${it::class.simpleName}")
  }.flatMap { cronScheduler ->
    cronScheduler.execute(jobStore)
  }.withContextualObject(context)

  private fun couldIgnoreError(err: Throwable): Boolean = err !is SchedulerException.NoSchedulerKeyFoundException &&
    err !is SchedulerException.NoJobStoreFoundException
}
