rootProject.name = "ascent"

pluginManagement {
  repositories {
    gradlePluginPortal()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
  repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") { name = "papermc" }
    maven("https://oss.sonatype.org/content/groups/public/") { name = "sonatype" }
    maven("https://jitpack.io") { name = "jitpack" }
    // WorldEdit (the API FastAsyncWorldEdit implements) and WorldGuard publish here.
    maven("https://maven.enginehub.org/repo/") { name = "enginehub" }
  }
}

include("ascent-api")
include("ascent-plugin")
