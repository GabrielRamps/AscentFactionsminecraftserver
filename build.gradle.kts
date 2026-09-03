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
