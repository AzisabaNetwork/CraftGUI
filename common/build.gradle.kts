plugins {
    `java-library`
}

dependencies {
    api(project(":api"))
    compileOnly("io.papermc.paper:paper-api:${property("paperApiVersion")}")
    compileOnly("net.azisaba:ItemStash:2.2.8")
    compileOnly("xyz.acrylicstyle:StorageBox:1.8.0")
    compileOnly("io.lumine:Mythic-Dist:5.14.0")
}
