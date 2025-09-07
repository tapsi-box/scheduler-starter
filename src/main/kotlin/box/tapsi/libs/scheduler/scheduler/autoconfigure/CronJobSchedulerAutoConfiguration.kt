package box.tapsi.libs.scheduler.scheduler.autoconfigure

import box.tapsi.libs.scheduler.SchedulerProperties
import box.tapsi.libs.scheduler.scheduler.schedulers.CronScheduler
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import reactor.core.publisher.Mono

class CronJobSchedulerAutoConfiguration(
  private val applicationContext: ApplicationContext,
  private val schedulerProperties: SchedulerProperties,
) : InitializingBean {
  private val logger = LoggerFactory.getLogger(this::class.java)

  override fun afterPropertiesSet() {
    logger.info("Auto scheduling of cron jobs is enabled")
    schedule()
      .subscribe({}, {
        logger.error("Auto scheduling of cron jobs failed", it)
      }, {
        logger.info("Auto scheduling of cron jobs completed")
      })
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
