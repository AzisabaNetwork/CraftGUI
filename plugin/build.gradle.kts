plugins {
    id("com.gradleup.shadow")
}

dependencies {
    implementation(project(":api"))
    implementation(project(":common"))
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
