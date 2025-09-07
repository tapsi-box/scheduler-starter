package box.tapsi.libs.scheduler.quartz.annotations

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import java.lang.annotation.Inherited

/**
 * This annotation will be used to indicate a bean that should be created only if the property
 * `box.libs.scheduler.quartz.enabled` is set to `true`.
 * It is a handy annotation to avoid creating beans that are unnecessary when Quartz is disabled.
 *
 * @see ConditionalOnProperty
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@ConditionalOnProperty(
  value = ["box.libs.scheduler.quartz.enabled"],
  havingValue = "true",
  matchIfMissing = false,
)
@Inherited
@MustBeDocumented
annotation class OnQuartzEnabled
