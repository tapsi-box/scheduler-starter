plugins {
  kotlin("jvm") version "1.9.23"
  kotlin("plugin.spring") version "1.9.23"
  id("io.spring.dependency-management") version "1.1.7"

  id("com.vanniktech.maven.publish") version "0.34.0"
  id("com.diffplug.spotless") version "7.2.1"
  id("io.gitlab.arturbosch.detekt") version "1.23.6"
  `java-library`
}

group = "box.tapsi.libs"
version = "0.9.0"
description = "scheduler-starter"

java {
  toolchain {
    languageVersion = JavaLanguageVersion.of(21)
  }
}

repositories {
  mavenCentral()
}

dependencies {
  api("org.springframework.boot:spring-boot-starter-quartz:3.5.5")

  implementation("io.projectreactor:reactor-core:3.7.9")
  implementation("box.tapsi.libs:metrics-core:1.0.2")
  implementation("box.tapsi.libs:utilities-starter:0.9.1")
  implementation("org.springframework:spring-context:6.2.10")
  implementation("org.springframework.boot:spring-boot-autoconfigure:3.5.5")
  implementation("io.github.mahdibohloul:projectreactor-retry-aop:2.0.0-RC2")
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.23")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions:1.2.3")


  testImplementation("org.springframework.boot:spring-boot-starter-test:3.5.5")
  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.9.23")

  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
  compilerOptions {
    freeCompilerArgs.addAll("-Xjsr305=strict")
  }
}

tasks.withType<Test> {
  useJUnitPlatform()
}

mavenPublishing {
  publishToMavenCentral()
  signAllPublications()

  pom {
    name.set("scheduler-starter")
    description.set(
      """A Spring Boot starter library providing reactive job scheduling 
      |capabilities with Quartz integration, 
      |supporting both regular and cron-based scheduling with automatic configuration, 
      |retry mechanisms, and comprehensive monitoring.""".trimMargin()
    )
    url.set("https://github.com/tapsi-box/scheduler-starter")
    licenses {
      license {
        name.set("MIT License")
        url.set("https://opensource.org/licenses/MIT")
        distribution.set("repo")
      }
    }
    developers {
      developer {
        id.set("mahdibohloul")
        name.set("Mahdi Bohloul")
        email.set("mahdiibohloul@gmail.com")
        url.set("https://github.com/mahdibohloul/")
      }
    }
    scm {
      url.set("https://github.com/tapsi-box/scheduler-starter")
    }
  }
}

spotless {
  kotlin {
    target("src/**/*.kt")
    ktlint("1.5.0")
      .editorConfigOverride(
        mapOf(
          "indent_size" to 2,
          "ktlint_standard_filename" to "disabled",
          "ktlint_standard_max-line-length" to "120"
        )
      )
    trimTrailingWhitespace()
    leadingTabsToSpaces()
    endWithNewline()
  }
}

detekt {
  buildUponDefaultConfig = true
  allRules = true
  config.setFrom("$projectDir/detekt.yml")
  baseline = file("$projectDir/detekt-baseline.xml")
}

tasks.register("verifyReadmeContent") {
  doLast {
    val readmeFile = file("README.md")
    val content = readmeFile.readText()

    // List of checks
    val checks = listOf(
      Check("group ID", """<groupId>${project.group}</groupId>"""),
      Check("version", """<version>${project.version}</version>"""),
    )

    val errors = checks.mapNotNull { check ->
      if (!content.contains(check.expectedValue)) {
        "Missing or incorrect ${check.name}: ${check.expectedValue}"
      } else null
    }

    if (errors.isNotEmpty()) {
      throw GradleException(
        """
                README content verification failed!
                ${errors.joinToString("\n")}
                Please update the README.md with correct values
            """.trimIndent()
      )
    }
  }
}

tasks.check {
  dependsOn("verifyReadmeContent")
}

data class Check(val name: String, val expectedValue: String)

