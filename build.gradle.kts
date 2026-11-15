import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

plugins {
    id("com.gradleup.shadow") version "8.3.0" apply false
}

allprojects {
    group = "net.azisaba"
    version = "1.2.1"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc-repo"
        }
        maven("https://oss.sonatype.org/content/groups/public/") {
            name = "sonatype"
        }
        maven("https://repo.azisaba.net/repository/maven-public/") {
            name = "azisaba-public"
        }
        maven("https://maven.azisaba.net/releases") {
            name = "azisaba-repo"
        }
        maven("https://mvn.lumine.io/repository/maven-public/") {
            name = "Lumine Releases"
        }
    }

    val targetJavaVersion = 8
    configure<JavaPluginExtension> {
        val javaVersion = JavaVersion.toVersion(targetJavaVersion)
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
        if (JavaVersion.current() < javaVersion) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(targetJavaVersion))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        if (targetJavaVersion >= 10 || JavaVersion.current().isJava10Compatible) {
            options.release.set(targetJavaVersion)
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
