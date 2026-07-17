plugins {
    java
    `maven-publish`
    id("com.gradleup.shadow") version "8.3.0"
}

group = "net.azisaba"
version = "1.15.2+1.1.1"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://repo.azisaba.net/repository/maven-public/") {
        name = "azisaba-repo"
    }
    maven("https://mvn.lumine.io/repository/maven-public/") {
        name = "Lumine Releases"
    }
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.15.2-R0.1-SNAPSHOT")
    compileOnly("net.azisaba:ItemStash:2.2.3")
    compileOnly("xyz.acrylicstyle:StorageBox:1.5.6+1.15.2")
    compileOnly("io.lumine.xikage:MythicMobs:4.12.0")
}

val targetJavaVersion = 8
java {
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

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

publishing {
    repositories {
        maven {
            name = "repo"
            credentials(PasswordCredentials::class)
            url = uri(if (project.version.toString().endsWith("SNAPSHOT")) {
                project.findProperty("deploySnapshotURL")
                    ?: System.getProperty("deploySnapshotURL", "https://repo.azisaba.net/repository/maven-snapshots/")
            } else {
                project.findProperty("deployReleasesURL")
                    ?: System.getProperty("deployReleasesURL", "https://repo.azisaba.net/repository/maven-releases/")
            },
            )
        }
    }
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
        }
    }
}