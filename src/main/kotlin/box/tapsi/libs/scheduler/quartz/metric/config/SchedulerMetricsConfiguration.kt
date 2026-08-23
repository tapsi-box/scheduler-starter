package box.tapsi.libs.scheduler.quartz.metric.config

import box.tapsi.libs.scheduler.quartz.annotations.OnQuartzEnabled
import box.tapsi.libs.scheduler.quartz.metric.registry.QuartzRegistry
import io.micrometer.core.instrument.Meter
import io.micrometer.core.instrument.config.MeterFilter
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

@Configuration(proxyBeanMethods = false)
class SchedulerMetricsConfiguration {
  /**
   * Enables service-level-objective histogram buckets on the Quartz execution and scheduling
   * timers, so a dashboard can compute p95/p99 with `histogram_quantile`. A small, fixed bucket
   * set is used on purpose to keep the exported series count bounded.
   */
  @Bean
  @OnQuartzEnabled
  @Suppress("SpreadOperator")
  fun quartzTimerMeterFilter(): MeterFilter = object : MeterFilter {
    override fun configure(id: Meter.Id, config: DistributionStatisticConfig): DistributionStatisticConfig {
      if (!isQuartzHistogramTimer(id.name)) {
        return config
      }
      return DistributionStatisticConfig.builder()
        .percentilesHistogram(false)
        .serviceLevelObjectives(*SLO_NANOS)
        .build()
        .merge(config)
    }
  }

  private fun isQuartzHistogramTimer(name: String): Boolean = name == QuartzRegistry.QUARTZ_EXECUTION_METRICS_NAME ||
    name == QuartzRegistry.MeterName.QuartzSchedulingTime.meterName

  companion object {
    private val SLO_BUCKETS_MILLIS = longArrayOf(5, 10, 25, 50, 100, 250, 500, 1_000, 2_500, 5_000, 10_000, 30_000)

    private val SLO_NANOS: DoubleArray = SLO_BUCKETS_MILLIS
      .map { Duration.ofMillis(it).toNanos().toDouble() }
      .toDoubleArray()
  }
}
