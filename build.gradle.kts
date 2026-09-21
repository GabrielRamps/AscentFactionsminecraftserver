import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
  java
  alias(libs.plugins.spotless)
}

// Captured at root scope so the values are plain data by the time the
// subprojects{} lambda runs.
val googleJavaFormatVersion: String = libs.versions.googleJavaFormat.get()

allprojects {
  group = rootProject.group
  version = rootProject.version
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "com.diffplug.spotless")
  apply(plugin = "jacoco")

  configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging { events("passed", "skipped", "failed") }
    finalizedBy("jacocoTestReport")
  }

  // PRD §5.6 targets 70% line coverage on core packages. The report is
  // build/reports/jacoco/test/html/index.html; the threshold is checked by
  // review rather than enforced, so a story is never blocked on a number.
  tasks.withType<JacocoReport>().configureEach {
    dependsOn("test")
    reports {
      xml.required.set(true)
      html.required.set(true)
    }
  }

  configure<SpotlessExtension> {
    java {
      googleJavaFormat(googleJavaFormatVersion)
      target("src/**/*.java")
      removeUnusedImports()
      trimTrailingWhitespace()
      endWithNewline()
    }
  }

  // PRD §5.6: the build fails on format violations.
  tasks.named("check") { dependsOn("spotlessCheck") }
}

spotless {
  format("gradleKts") {
    target("*.gradle.kts", "*/*.gradle.kts")
    trimTrailingWhitespace()
    endWithNewline()
  }
}
