import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val modernJavaVersion = 21

java {
    val javaVersion = JavaVersion.toVersion(modernJavaVersion)
    sourceCompatibility = javaVersion
    targetCompatibility = javaVersion
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(modernJavaVersion)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(modernJavaVersion.toString()))
        freeCompilerArgs.set(listOf("-Xjdk-release=$modernJavaVersion"))
    }
}

dependencies {
    compileOnly(project(":common"))
    compileOnly("io.papermc.paper:paper-api:1.20.6-R0.1-SNAPSHOT")
}
