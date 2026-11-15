plugins {
    id("com.gradleup.shadow")
}

configurations.all {
    attributes {
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))
    implementation(project(":adapter-legacy"))
    implementation(project(":adapter-modern"))
    implementation(project(":mythic-v4"))
    implementation(project(":mythic-v5"))
    compileOnly("com.destroystokyo.paper:paper-api:1.13.2-R0.1-SNAPSHOT")
}

tasks.processResources {
    val props = mapOf("version" to rootProject.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.shadowJar {
    archiveBaseName.set("CraftGUI")
    archiveClassifier.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
