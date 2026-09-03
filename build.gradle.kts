plugins {
  alias(libs.plugins.shadow) apply false
  alias(libs.plugins.spotless) apply false
}

allprojects {
  group = "dev.ascentfactions"
  version = property("ascentVersion") as String
}

subprojects {
  apply(plugin = "java-library")
  apply(plugin = "com.diffplug.spotless")

  extensions.configure<JavaPluginExtension> {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    withSourcesJar()
  }

  extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
      target("src/**/*.java")
      googleJavaFormat()
      removeUnusedImports()
      trimTrailingWhitespace()
      endWithNewline()
    }
    kotlinGradle {
      target("*.gradle.kts")
      trimTrailingWhitespace()
      endWithNewline()
    }
  }

  tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(21)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
  }

  tasks.withType<Javadoc>().configureEach { options.encoding = "UTF-8" }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
      showStandardStreams = false
    }
  }
}
