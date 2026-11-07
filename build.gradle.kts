import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.20.0"
}

group = "io.github.leodias"
version = "0.1.8"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        val localIde = providers.gradleProperty("localIdePath").orNull
        if (localIde != null) local(localIde) else intellijIdea("2026.2.4")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
        testBundledPlugin("com.intellij.plugins.eclipsekeymap")
    }
    testImplementation(kotlin("stdlib"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.12.2")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.12.2")
}

kotlin {
    jvmToolchain(25)
    compilerOptions { jvmDefault = org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY }
}
tasks.test { useJUnitPlatform() }

intellijPlatform {
    buildSearchableOptions = false
    pluginConfiguration {
        name = "HTTPie"
        ideaVersion {
            sinceBuild = "262.11879.100"
            untilBuild = provider { null }
        }
    }
    pluginVerification {
        ides { current() }
    }
}
