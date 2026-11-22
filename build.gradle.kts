plugins {
    java
}

group = "ru.playtimegate"
version = "1.0.2"
description = "Ограничение действий в приватах WorldGuard для новых игроков + статистика наигранного времени"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.3.build.+")

    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.+")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.+")
    // TAB — опциональная softdepend-зависимость: привязка идёт через рефлексию
    // (ru.playtimegate.tab.TabAfkBridge), jar в компиляции не нужен.
}

java {}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

tasks.processResources {
    filteringCharset = "UTF-8"
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("PlaytimeGate")
    archiveVersion.set(project.version.toString())
}
