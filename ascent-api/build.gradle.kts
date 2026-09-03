// ascent-api holds only interfaces, custom events and value types. It must never
// depend on the plugin implementation; modules talk to each other through this
// module and never through each other's implementation classes (PRD §5.6).

dependencies {
  compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
  compileOnly(libs.annotations)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly(libs.junit.platform.launcher)
}
