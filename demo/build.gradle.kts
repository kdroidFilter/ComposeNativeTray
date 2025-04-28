import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

group = "com.kdroid.composetray.demo"
version = "0.5.5"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    implementation(project(":"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("io.github.kdroidfilter:kmplog:0.6.2")
}

compose.desktop {
    application {
        mainClass = "com.kdroid.composetray.demo.DynamicTrayMenuKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Deb)
            packageName = "tray-demo"
            packageVersion = version.toString()
        }
    }
}