import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    kotlin("jvm") version "2.1.10"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.10"
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jetbrains.dokka")  version "2.0.0"
}

group = "com.kdroid.composenativetray"
version = "0.5.4"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()

}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set("Compose Native Tray Library")
    offlineMode.set(true)
}

dependencies {
    implementation("net.java.dev.jna:jna:5.16.0")
    implementation("net.java.dev.jna:jna-platform:5.16.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("io.github.kdroidfilter:kmplog:0.3.0")
    implementation(compose.desktop.currentOs)
    testImplementation("org.jetbrains.compose.material3:material3-desktop:1.8.0+dev2139")
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

compose.desktop {
    application {
        mainClass = "com.kdroid.composetray.demo.ComposeAppKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tray-demo"
            packageVersion = version.toString()
        }
    }
}
