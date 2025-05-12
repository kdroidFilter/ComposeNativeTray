import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.vannitktech.maven.publish)
    alias(libs.plugins.dokka)
}

group = "com.kdroid.composenativetray"
val ref = System.getenv("GITHUB_REF") ?: ""
val version = if (ref.startsWith("refs/tags/")) {
    val tag = ref.removePrefix("refs/tags/")
    if (tag.startsWith("v")) tag.substring(1) else tag
} else "dev"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()

}

tasks.withType<DokkaTask>().configureEach {
    moduleName.set("Compose Native Tray Library")
    offlineMode.set(true)
}



kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(libs.jna)
            implementation(libs.jna.platform)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kmp.log)
            implementation(libs.platformtools.core)
        }
    }

}

val buildWin: TaskProvider<Exec> = tasks.register<Exec>("buildNativeWin") {
    onlyIf { System.getProperty("os.name").startsWith("Windows") }
    workingDir(rootDir.resolve("winlib"))
    commandLine("cmd", "/c", "build.bat")
}

tasks.register("buildNativeLibraries") {
    dependsOn(buildWin)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.kdroidfilter",
        artifactId = "composenativetray",
        version = version
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

