plugins { alias(libs.plugins.shadow) }

dependencies {
  api(project(":ascent-api"))
  compileOnly(libs.paper.api)

  // Shaded runtime dependencies (Adventure/MiniMessage and SLF4J are provided by Paper).
  implementation(libs.hikari)
  implementation(libs.jedis)
  implementation(libs.caffeine)
  implementation(libs.flyway.core)
  implementation(libs.flyway.mysql)
  implementation(libs.mariadb.client)

  testImplementation(libs.paper.api)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.launcher)
  testImplementation(libs.mockito)
}

// Where `copyToServer` and dev.sh put the jar. Override with ASCENT_SERVER_DIR.
val serverDir: String =
    System.getenv("ASCENT_SERVER_DIR") ?: "${System.getProperty("user.home")}/ascent-server"

tasks {
  processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") { expand(props) }
  }

  jar { enabled = false }

  shadowJar {
    archiveBaseName.set("Ascent")
    archiveClassifier.set("")
    mergeServiceFiles()

    // Relocate shaded libraries so they can never clash with another plugin's copy.
    relocate("com.zaxxer.hikari", "dev.ascentfactions.libs.hikari")
    relocate("redis.clients", "dev.ascentfactions.libs.jedis")
    relocate("com.github.benmanes.caffeine", "dev.ascentfactions.libs.caffeine")
    relocate("org.apache.commons.pool2", "dev.ascentfactions.libs.commonspool2")
    // Flyway and the MariaDB driver are intentionally not relocated: Flyway locates
    // migrations and plugins by classpath resource names, and the driver class name
    // is referenced verbatim in the datasource config.

    dependencies { exclude(dependency("org.slf4j:.*")) }
  }

  build { dependsOn(shadowJar) }

  register<Copy>("copyToServer") {
    group = "ascent"
    description = "Copies the built plugin jar into the local test server's plugins/ directory."
    dependsOn(shadowJar)
    from(shadowJar.flatMap { it.archiveFile })
    into("$serverDir/plugins")
    doLast { logger.lifecycle("Copied plugin jar to $serverDir/plugins") }
  }
}
