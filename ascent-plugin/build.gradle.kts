plugins {
  alias(libs.plugins.shadow)
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
  compileOnly(libs.annotations)

  api(project(":ascent-api"))

  // Shaded into the jar. Adventure/MiniMessage is provided by Paper and must
  // never be bundled here.
  implementation(libs.bundles.runtime)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
  testImplementation(libs.mockito.core)
  // MockBukkit is added in Epic 1, when the first listener/service tests land.
  // Confirm the current coordinates at https://mockbukkit.org before adding it:
  // the artifact moved from com.github.seeseemelk to org.mockbukkit.mockbukkit.
  testImplementation("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}

tasks.processResources {
  val props =
    mapOf(
      "version" to project.version.toString(),
      "apiVersion" to
        rootProject.property("paperApiVersion").toString().substringBefore("-R").substringBeforeLast('.'),
    )
  inputs.properties(props)
  filesMatching("plugin.yml") { expand(props) }
}

tasks.shadowJar {
  archiveBaseName.set("Ascent")
  archiveClassifier.set("")

  // Relocated so we can never collide with another plugin shipping its own copy.
  relocate("com.zaxxer.hikari", "gg.ascent.lib.hikari")
  relocate("redis.clients.jedis", "gg.ascent.lib.jedis")
  relocate("com.github.benmanes.caffeine", "gg.ascent.lib.caffeine")

  mergeServiceFiles()
  exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.jar { archiveClassifier.set("plain") }

tasks.build { dependsOn(tasks.shadowJar) }

// E0-S2: one command puts the jar on the dev server.
val ascentServerDir: String =
  (project.findProperty("ascentServerDir") as String?)
    ?.takeIf { it.isNotBlank() }
    ?: "${System.getProperty("user.home")}/ascent-server"

tasks.register<Copy>("copyToServer") {
  group = "ascent"
  description = "Copies the shaded plugin jar into the dev server's plugins/ directory."
  dependsOn(tasks.shadowJar)
  from(tasks.shadowJar.flatMap { it.archiveFile })
  into("$ascentServerDir/plugins")
  doFirst {
    val plugins = file("$ascentServerDir/plugins")
    if (!plugins.isDirectory) {
      throw GradleException(
        "Server plugins directory not found: $plugins\n" +
          "Run Epic 0 story E0-S1 first, or pass -PascentServerDir=/path/to/server.",
      )
    }
  }
  doLast { logger.lifecycle("Copied Ascent-${project.version}.jar -> $ascentServerDir/plugins") }
}
