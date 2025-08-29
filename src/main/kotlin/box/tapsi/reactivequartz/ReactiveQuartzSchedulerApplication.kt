package box.tapsi.reactivequartz

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ReactiveQuartzSchedulerApplication

fun main(args: Array<String>) {
  runApplication<ReactiveQuartzSchedulerApplication>(*args)
}
