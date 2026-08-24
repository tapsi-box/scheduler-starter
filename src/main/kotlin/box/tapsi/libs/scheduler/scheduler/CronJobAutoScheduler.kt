package box.tapsi.libs.scheduler.scheduler

import box.tapsi.libs.scheduler.SchedulerProperties
import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Mono

/**
 * Schedules every [CronScheduler] bean once the application context is ready.
 *
 * It runs as an [ApplicationRunner], not as an initializing bean, for two reasons. The context is
 * complete at that point, so the lookup finds every [CronScheduler] and forces no early bean
 * creation. And the run blocks on the result, so a failure to schedule stops the startup instead
 * of leaving a healthy application with no cron job registered.
 */
class CronJobAutoScheduler(
  private val applicationContext: ApplicationContext,
  private val schedulerProperties: SchedulerProperties,
) : ApplicationRunner {
  private val logger = LoggerFactory.getLogger(CronJobAutoScheduler::class.java)

  override fun run(args: ApplicationArguments?) {
    logger.info("Auto scheduling of cron jobs is enabled")
    schedule().block(schedulerProperties.cronJob.schedulingTimeout)
    logger.info("Auto scheduling of cron jobs completed")
  }

  fun schedule(): Mono<Void> {
    logger.info("Scheduling cron jobs")
    return Mono.fromSupplier { applicationContext.getBeansOfType(CronScheduler::class.java) }
      .doOnNext { logger.info("Found ${it.size} cron jobs") }
      .filter { it.isNotEmpty() }
      .flatMapIterable { it.toList() }
      .filter { maybeExclude(beanName = it.first) }
      .doOnNext { logger.info("Scheduling cron job: ${it.first}") }
      .flatMap { stringCronSchedulerPair ->
        scheduleCronScheduler(beanName = stringCronSchedulerPair.first, cronScheduler = stringCronSchedulerPair.second)
      }
      .collectList()
      .then()
      .doOnSuccess {
        logger.info("Scheduling cron jobs completed")
      }.doOnError {
        logger.error("Scheduling cron jobs failed", it)
      }
  }

  private fun maybeExclude(
    beanName: String,
  ): Boolean = schedulerProperties.cronJob.schedulingExcludes.contains(beanName).not()
    .also {
      logger.info("Maybe exclude cron job: $beanName - ${it.not()}")
    }

  private fun scheduleCronScheduler(
    beanName: String,
    cronScheduler: CronScheduler,
  ): Mono<Void> = cronScheduler.schedule(jobStore = null, fireTimestamp = null)
    .doOnSuccess {
      logger.info("Cron job scheduled: $beanName")
    }.doOnError {
      logger.error("Error scheduling cron job: $beanName", it)
    }
}
