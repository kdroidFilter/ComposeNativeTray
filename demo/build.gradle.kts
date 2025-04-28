import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose)
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
            implementation(libs.kmp.log)
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
