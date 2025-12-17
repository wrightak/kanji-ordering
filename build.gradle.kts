import org.gradle.api.tasks.compile.JavaCompile

plugins {
    kotlin("jvm") version "2.1.21"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("BuildKanjiFromComponentsKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.run {
}

kotlin {
    compilerOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}
