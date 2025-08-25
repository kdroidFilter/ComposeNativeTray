import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
    id("io.github.kdroidfilter.compose.linux.packagedeps") version "0.2.1"
}

kotlin {
    jvmToolchain(17)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.desktop.currentOs)
            implementation(compose.components.resources)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(libs.kermit)
            implementation(libs.platformtools.darkmodedetector)
        }
    }
}


compose.desktop {
    application {
        mainClass = "com.kdroid.composetray.demo.DynamicTrayMenuKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Dmg)
            packageName = "tray-demo"
            packageVersion = "1.0.0"
        }
        buildTypes.release.proguard {
            isEnabled = true
            obfuscate.set(false)
            optimize.set(true)
            configurationFiles.from(project.file("proguard-rules.pro"))
        }
    }
}

// Task to build native libraries and run the demo
tasks.register("buildAndRunDemo") {
    // Depend on the buildNativeLibraries task from the root project
    dependsOn(rootProject.tasks.named("buildNativeLibraries"))
    
    // This task doesn't do anything by itself, it just depends on buildNativeLibraries
    // and will be followed by the run task
    doLast {
        println("Native libraries built successfully. Starting demo application...")
    }
    
    // Make sure the run task is executed after this task
    finalizedBy(tasks.named("run"))
    
    // Description for the task
    description = "Builds the native libraries and then runs the demo application"
    group = "application"
}

linuxDebConfig {
    startupWMClass.set("com.kdroid.composetray.demo.DynamicTrayMenuKt")
}