import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "1.7.10"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.pi4j"
version = "0.2"

application {
    mainClass.set("I2CExampleKt")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.pi4j:pi4j-ktx:2.4.0") // Kotlin DSL
    implementation("com.pi4j:pi4j-core:2.3.0")
    implementation("com.pi4j:pi4j-plugin-raspberrypi:2.3.0")
    implementation("com.pi4j:pi4j-plugin-linuxfs:2.3.0")
    implementation("com.pi4j:pi4j-plugin-mock:2.3.0")
    implementation("com.pi4j:pi4j-plugin-pigpio:2.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.3")
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    testImplementation(kotlin("test"))
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}
tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "I2CExampleKt"
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}