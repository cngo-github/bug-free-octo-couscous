plugins {
    kotlin("jvm") version "2.0.0"
    id("com.ncorti.ktfmt.gradle") version "0.19.0"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.arrow-kt:arrow-core:1.2.4")
    implementation("io.arrow-kt:arrow-fx-coroutines:1.2.4")
    implementation("redis.clients:jedis:5.1.3")
    implementation("io.github.oshai:kotlin-logging:7.0.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.javamoney:moneta:1.4.4")
    implementation("org.xerial:sqlite-jdbc:3.46.0.0")
    implementation("com.google.guava:guava:33.2.1-jre")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-assertions-core-jvm:5.9.1")
    testImplementation("io.oden:embedded-redis:0.0.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}