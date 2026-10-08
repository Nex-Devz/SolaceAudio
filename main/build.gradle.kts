plugins {
    java
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
    }
}

repositories {
    mavenCentral()
    maven("https://maven.lavalink.dev/releases")
    maven("https://maven.lavalink.dev/snapshots")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("dev.arbjerg:lavaplayer:2.0.4")
    compileOnly("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    compileOnly("org.jetbrains:annotations:24.0.1")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("com.github.topi314.lavalyrics:lavalyrics:1.0.0")

    testImplementation("dev.arbjerg:lavaplayer:2.0.4")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "solaceaudio-main"
        }
    }
}

