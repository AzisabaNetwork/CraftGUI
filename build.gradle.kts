import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.4.21" apply false
    id("com.gradleup.shadow") version "9.6.1" apply false
}

allprojects {
    group = "net.azisaba"
    version = "1.2.1"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.azisaba.net/repository/maven-public/")
        maven("https://maven.azisaba.net/releases")
        maven("https://mvn.lumine.io/repository/maven-public/")
    }

    val targetJavaVersion = property("javaVersion").toString().toInt()
    configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    configure<KotlinJvmProjectExtension> {
        jvmToolchain(targetJavaVersion)
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
        }
    }

    configure<PublishingExtension> {
        repositories {
            maven {
                name = "repo"
                credentials(PasswordCredentials::class)
                url = uri(
                    if (project.version.toString().endsWith("-SNAPSHOT"))
                        "https://maven.azisaba.net/snapshots"
                    else
                        "https://maven.azisaba.net/releases"
                )
            }
        }

        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
            }
        }
    }
}
