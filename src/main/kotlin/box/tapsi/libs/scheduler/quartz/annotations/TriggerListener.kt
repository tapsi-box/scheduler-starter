package box.tapsi.libs.scheduler.quartz.annotations

import org.springframework.core.annotation.AliasFor
import org.springframework.stereotype.Component

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
@Component
annotation class TriggerListener(
  @get:AliasFor(annotation = Component::class)
  val value: String = "",
)
