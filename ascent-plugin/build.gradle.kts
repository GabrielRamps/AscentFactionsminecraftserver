plugins {
  alias(libs.plugins.shadow)
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
  compileOnly(libs.annotations)
  // Vault's Economy interface (E1-S4). Vault ships the classes at runtime; the
  // hook is only touched when the Vault plugin is present.
  compileOnly(libs.vaultapi) { exclude(group = "org.bukkit") }
  // The WorldEdit API for mine fills and resets (E4-S1); FastAsyncWorldEdit provides these
  // classes at runtime, and the mines module checks the plugin is present before touching them.
  compileOnly(libs.worldedit.core) { isTransitive = false }
  compileOnly(libs.worldedit.bukkit) { isTransitive = false }

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
  testImplementation(libs.vaultapi) { exclude(group = "org.bukkit") }
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
  relocate("org.mariadb.jdbc", "gg.ascent.lib.mariadb")
  // Flyway is deliberately not relocated: it loads its own SQL resources by
  // their original package path, which relocation does not rewrite.

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
    // A version bump changes the jar name. Remove any other Ascent jar first,
    // or Paper finds two copies of the plugin and loads neither.
    val current = tasks.shadowJar.get().archiveFileName.get()
    plugins.listFiles { f -> f.name.startsWith("Ascent-") && f.name.endsWith(".jar") && f.name != current }
      ?.forEach { stale ->
        logger.lifecycle("Removing stale ${stale.name}")
        stale.delete()
      }
  }
  doLast { logger.lifecycle("Copied Ascent-${project.version}.jar -> $ascentServerDir/plugins") }
}
