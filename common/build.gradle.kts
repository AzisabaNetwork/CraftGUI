plugins {
    `java-library`
}

dependencies {
    api(project(":api"))
    compileOnly("com.destroystokyo.paper:paper-api:1.13.2-R0.1-SNAPSHOT")
    compileOnly("net.azisaba:ItemStash:2.2.3")
    compileOnly("xyz.acrylicstyle:StorageBox:1.8.0")
}
