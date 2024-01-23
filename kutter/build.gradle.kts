import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "1.9.20"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jetbrains.compose") version "1.5.11"
}

group = "pl.toboche"
version = "0.2"

repositories {
    mavenCentral()
}

dependencies {
    //to run on mac:
    implementation(compose.desktop.currentOs)
    // to run on rpi:
//    implementation(compose.desktop.linux_arm64)
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
    runtimeOnly("org.jetbrains.skiko:skiko:0.7.90")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-swing
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.6.3")

}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}