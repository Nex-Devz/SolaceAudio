plugins {
    java
    `maven-publish`
    alias(libs.plugins.lavalink)
}

version = System.getenv("VERSION") ?: "dev"

lavalinkPlugin {
    name = "solaceaudio-plugin"
    path = "com.solaceaudio.plugin"
    apiVersion = libs.versions.lavalink.api
    serverVersion = libs.versions.lavalink.server
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
    jar {
        mustRunAfter(":solaceaudio-main:compileTestJava")
    }
}

dependencies {
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    compileOnly("org.jetbrains:annotations:24.0.1")
    implementation(project(":solaceaudio-main"))
    compileOnly("com.github.topi314.lavalyrics:lavalyrics:1.0.0")
}

publishing {
    publications {
        register<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.github.Nex-Devz"
            artifactId = "SolaceAudio"
        }
        register<MavenPublication>("mavenPlugin") {
            from(components["java"])
            groupId = "com.github.Nex-Devz.SolaceAudio"
            artifactId = "plugin"
        }
    }
}
