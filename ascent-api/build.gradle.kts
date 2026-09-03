// ascent-api: service interfaces, DTOs, and custom Bukkit events.
// Modules in ascent-plugin only talk to each other through the interfaces declared here.

dependencies {
  compileOnly(libs.paper.api)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.launcher)
}
