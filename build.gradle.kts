plugins {
    kotlin("jvm")
}

group = "com.ageofmc"
version = "1.0.0"

description = "Folia 26.1.2 world download protection (WorldTools-aware)"

val ageJavaToolchain = providers.gradleProperty("age.java.toolchain").map(String::toInt).orElse(25)
val ageJvmTarget = providers.gradleProperty("age.jvm.target").map(String::toInt).orElse(25)

repositories {
    mavenCentral()
    mavenLocal()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly(libs.paper.api.character)
    compileOnly(project(":age-lib"))
}

tasks {
    jar {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        archiveFileName.set("WorldDownloadPreventer.jar")
    }
    compileKotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(ageJvmTarget.get().toString()))
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(ageJavaToolchain.get())) }
}
