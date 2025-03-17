plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.2" // Consider upgrading to 1.18.0 if needed
}

group = "com.kuzco"
version = "1.1.0" // Matches plugin.xml

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.2.2") // Base version for development
    type.set("PY") // Targets both PyCharm Community and Professional
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    patchPluginXml {
        sinceBuild.set("223.7571") // Compatible with 2022.3
        untilBuild.set("243.*") // Compatible up to 2024.x
    }
    buildSearchableOptions {
        enabled = false
    }
}