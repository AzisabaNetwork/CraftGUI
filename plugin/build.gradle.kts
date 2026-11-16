plugins {
    id("com.gradleup.shadow")
}

// adapter-modern (Java 21) を同梱するため，解決時は Java 21 向けバリアントとして扱う
configurations.matching { it.name.endsWith("Classpath") }.configureEach {
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

    // 他プラグインが同梱する Kotlin と衝突しないよう標準ライブラリを再配置する
    relocate("kotlin", "net.azisaba.craftgui.libs.kotlin")
    dependencies {
        exclude(dependency("org.jetbrains:annotations"))
    }
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/**/module-info.class")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
