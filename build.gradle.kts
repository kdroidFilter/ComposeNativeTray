import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    alias(libs.plugins.vannitktech.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
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
        jvmMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.platformtools.core)
            implementation(libs.nucleus.core.runtime)
            implementation(libs.nucleus.darkmode.detector)
        }
    }
}

// ── Native build tasks ──────────────────────────────────────────────────────────

val buildNativeMacOs by tasks.registering(Exec::class) {
    workingDir = file("src/native/macos")
    commandLine("bash", "build.sh")
    onlyIf { Os.isFamily(Os.FAMILY_MAC) }
}

val buildNativeWindows by tasks.registering(Exec::class) {
    workingDir = file("src/native/windows")
    commandLine("cmd", "/c", "build.bat")
    onlyIf { Os.isFamily(Os.FAMILY_WINDOWS) }
}

val buildNativeLinux by tasks.registering(Exec::class) {
    workingDir = file("src/native/linux")
    commandLine("bash", "build.sh")
    onlyIf { Os.isFamily(Os.FAMILY_UNIX) && !Os.isFamily(Os.FAMILY_MAC) }
}

tasks.register("buildNativeLibraries") {
    dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
}

tasks.named("jvmProcessResources") {
    dependsOn(buildNativeMacOs, buildNativeWindows, buildNativeLinux)
}

// ── Code quality ────────────────────────────────────────────────────────────────

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    ktlint {
        debug.set(false)
        verbose.set(true)
        android.set(false)
        outputToConsole.set(true)
        ignoreFailures.set(false)
        enableExperimentalRules.set(true)
        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }
}

// ── Maven publishing ────────────────────────────────────────────────────────────

mavenPublishing {
    coordinates(
        groupId = "io.github.kdroidfilter",
        artifactId = "composenativetray",
        version = version
    )

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

        developers {
            developer {
                id.set("kdroidfilter")
                name.set("Elyahou Hadass")
                email.set("elyahou.hadass@gmail.com")
            }
        }

        scm {
            url.set("https://github.com/kdroidFilter/ComposeNativeTray")
        }
    }

    publishToMavenCentral()
    signAllPublications()
}
