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
  }
}

include("ascent-api")
include("ascent-plugin")
