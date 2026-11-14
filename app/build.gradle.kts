import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(21)

    jvm("desktop")

    // Web version (GitHub Pages): ./gradlew :app:wasmJsBrowserDistribution
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("chirp-rea")
        browser {
            commonWebpackConfig { outputFileName = "chirp-rea.js" }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies { implementation(libs.kotlinx.browser) }
        }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.jserialcomm)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
    }
}

// Read-only handshake probe: ./gradlew :app:probe -Pport=COM6
tasks.register<JavaExec>("probe") {
    group = "chirp-rea"
    description = "Sends only identification handshakes to a radio and logs the replies."
    val main = kotlin.jvm("desktop").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("chirprea.app.tools.ProbeKt")
    workingDir = projectDir
    args(providers.gradleProperty("port").orElse("COM6").get())
}

// Draws the original logos shipped with the app: ./gradlew :app:generateLogos
tasks.register<JavaExec>("generateLogos") {
    group = "chirp-rea"
    description = "Renders the bundled power-on logos into src/desktopMain/resources/logos."
    val main = kotlin.jvm("desktop").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("chirprea.app.tools.LogoArtKt")
    workingDir = projectDir
    systemProperty("java.awt.headless", "true")
}

// Draws the application icon: ./gradlew :app:generateIcon
tasks.register<JavaExec>("generateIcon") {
    group = "chirp-rea"
    description = "Renders the CHIRP-REA icon (PNG sizes + Windows .ico)."
    val main = kotlin.jvm("desktop").compilations.getByName("main")
    dependsOn(main.compileTaskProvider)
    classpath = files(main.output.allOutputs, main.runtimeDependencyFiles)
    mainClass.set("chirprea.app.tools.AppIconKt")
    workingDir = projectDir
    systemProperty("java.awt.headless", "true")
}

compose.desktop {
    application {
        mainClass = "chirprea.app.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "CHIRP-REA"
            // Native installers require MAJOR > 0. Release builds pass the tag version:
            // ./gradlew :app:packageExe "-PappVersion=1.2.0"   (quotes needed in PowerShell)
            packageVersion = providers.gradleProperty("appVersion").orElse("1.0.0").get()
            description = "Programador de rádios compatível com o CHIRP"
            vendor = "CHIRP-REA"
            copyright = "GPL-3.0 — baseado no CHIRP (Dan Smith e colaboradores)"
            licenseFile.set(rootProject.file("LICENSE"))
            // JDK modules the app needs (from ./gradlew :app:suggestRuntimeModules), so the
            // bundled runtime stays small but complete.
            modules("java.instrument", "jdk.unsupported")

            windows {
                iconFile.set(project.file("packaging/chirp-rea.ico"))
                menuGroup = "CHIRP-REA"
                shortcut = true
                menu = true
                // Installs into the user's profile: no administrator rights needed.
                perUserInstall = true
                dirChooser = true
                // Must never change: lets new versions upgrade the installed one.
                upgradeUuid = "8e1c0f28-d499-40bd-8267-a3e0abe5d2ae"
            }
            linux {
                iconFile.set(project.file("packaging/chirp-rea-1024.png"))
            }
        }
    }
}
