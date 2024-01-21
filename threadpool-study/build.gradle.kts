plugins {
    kotlin("jvm") version "1.9.0"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api(platform("org.slf4j:slf4j-bom:2.1.0-alpha1"))
    api("org.slf4j:slf4j-api")
    implementation("com.google.guava:guava:33.0.0-jre")

    testImplementation(kotlin("test"))
    testImplementation("org.slf4j:slf4j-simple")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("MainKt")
}