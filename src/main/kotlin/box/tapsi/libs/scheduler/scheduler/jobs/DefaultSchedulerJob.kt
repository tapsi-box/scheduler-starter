package box.tapsi.libs.scheduler.scheduler.jobs

import box.tapsi.libs.scheduler.SchedulerProperties
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
import reactor.core.publisher.Mono
import reactor.core.publisher.SynchronousSink
import reactor.kotlin.core.publisher.switchIfEmpty

/**
 * Quartz instantiates this class for every job fire through the Spring job factory, so its
 * constructor arguments are autowired from the context. It is not registered as a bean.
 */
class DefaultSchedulerJob(
  private val applicationContext: ApplicationContext,
  private val quartzRegistry: QuartzRegistry,
  private val schedulerProperties: SchedulerProperties,
) : QuartzJobBean() {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun executeInternal(context: JobExecutionContext) {
    logger.info("Executing job ${context.jobDetail.key.name}")
    doExecute(context)
      // Metrics MUST wrap the raw execution, upstream of the error-swallow below, so the
      // observation records the true terminal signal and failures are counted.
      .transform { quartzRegistry.exposeExecutionMetrics(it, context) }
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
      // A job chain that never terminates holds a Quartz worker thread for the life of the
      // process. `box.libs.scheduler.job.execution-timeout` bounds the wait. It has no default,
      // because a wrong finite value would fail a job that is only slow.
      .let { execution -> blockWithOptionalTimeout(execution, context) }
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

  private fun blockWithOptionalTimeout(execution: Mono<Void>, context: JobExecutionContext): Void? {
    val timeout = schedulerProperties.job.executionTimeout
      ?: return execution.block()
    logger.debug("Job {} runs with an execution timeout of {}", context.jobDetail.key, timeout)
    return execution.block(timeout)
  }

  private fun couldIgnoreError(err: Throwable): Boolean = err !is SchedulerException.NoSchedulerKeyFoundException &&
    err !is SchedulerException.NoJobStoreFoundException
}
