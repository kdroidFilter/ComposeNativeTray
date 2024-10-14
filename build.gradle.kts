import com.vanniktech.maven.publish.SonatypeHost

plugins {
    kotlin("jvm") version "2.0.20"
    id("com.vanniktech.maven.publish") version "0.29.0"
}

group = "com.kdroid.composenativetray"
version = "0.1.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna:5.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("io.github.kdroidfilter:kmplog:0.1.0")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.kdroidfilter",
        artifactId = "composenativetray",
        version = version.toString()
    )

    // Configure POM metadata for the published artifact
    pom {
        name.set("Compose Native Tray")
        description.set("ComposeTray is a Kotlin library that provides a simple way to create system tray applications with native support for Linux and Windows. This library allows you to add a system tray icon, tooltip, and menu with various options in a Kotlin DSL-style syntax.")
        inceptionYear.set("2024")
        url.set("https://github.com/kdroidFilter/ComposeNativeTray")

        licenses {
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        // Specify developers information
        developers {
            developer {
                id.set("kdroidfilter")
                name.set("Elyahou Hadass")
                email.set("elyahou.hadass@gmail.com")
            }
        }

        // Specify SCM information
        scm {
            url.set("https://github.com/kdroidFilter/ComposeNativeTray")
        }
    }

    // Configure publishing to Maven Central
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)


    // Enable GPG signing for all publications
    signAllPublications()
}