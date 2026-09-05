plugins {
    id("com.gradleup.shadow") version "9.6.1"
}

description = "Ascent Factions server plugin (shaded jar)."

val paperApiVersion: String by project
val paperApiLevel: String by project

dependencies {
    api(project(":ascent-api"))

    // Provided by the server or by soft-depended plugins at runtime.
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    compileOnly("net.luckperms:api:5.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }

    // Shaded into the plugin jar. Adventure/MiniMessage and Gson come from Paper.
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.10")
    implementation("org.flywaydb:flyway-core:13.5.0")
    implementation("org.flywaydb:flyway-mysql:13.5.0")
    implementation("redis.clients:jedis:8.0.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.2.4")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.20.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Resolve the local test server directory: -PserverDir, then ASCENT_SERVER_DIR, then ~/ascent-server.
val serverDir: String =
    providers.gradleProperty("serverDir").orNull?.takeIf { it.isNotBlank() }
        ?: providers.environmentVariable("ASCENT_SERVER_DIR").orNull?.takeIf { it.isNotBlank() }
        ?: "${System.getProperty("user.home")}/ascent-server"

tasks {
    processResources {
        val props = mapOf("version" to project.version.toString(), "apiVersion" to paperApiLevel)
        inputs.properties(props)
        filesMatching("plugin.yml") { expand(props) }
    }

    shadowJar {
        archiveBaseName = "Ascent"
        archiveClassifier = ""
        mergeServiceFiles()

        // Paper ships SLF4J and Gson; do not bundle second copies.
        dependencies {
            exclude(dependency("org.slf4j:.*"))
            exclude(dependency("com.google.code.gson:gson"))
        }

        val base = "gg.ascent.libs"
        relocate("com.zaxxer.hikari", "$base.hikari")
        relocate("org.mariadb.jdbc", "$base.mariadb")
        relocate("redis.clients.jedis", "$base.jedis")
        relocate("org.apache.commons.pool2", "$base.commons.pool2")
        relocate("com.github.benmanes.caffeine", "$base.caffeine")
        relocate("com.fasterxml.jackson", "$base.jackson")
        // Flyway is intentionally not relocated: it locates its own resources by package name.
        // No minimize(): JDBC drivers, Flyway and Jedis load classes reflectively.
    }

    build { dependsOn(shadowJar) }

    register<Copy>("copyToServer") {
        group = "ascent"
        description = "Copies the shaded jar into $serverDir/plugins (removing older Ascent-*.jar files)."
        from(shadowJar)
        into("$serverDir/plugins")
        doFirst {
            file("$serverDir/plugins").listFiles { f ->
                f.name.startsWith("Ascent-") && f.name.endsWith(".jar")
            }?.forEach { it.delete() }
        }
    }
}
