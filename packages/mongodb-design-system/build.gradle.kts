plugins {
    id("com.mongodb.intellij.isolated-module")
    id("org.jetbrains.compose") version ("1.7.3")
    id("org.jetbrains.kotlin.plugin.compose") version ("2.0.21")
}

repositories {
    google()
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
}

kotlin {
    sourceSets {
        jvmMain {
            dependencies {
                compileOnly(compose.runtime)
                compileOnly(compose.foundation)

                implementation("org.jetbrains.jewel:jewel-ide-laf-bridge-243:0.27.0")
                compileOnly("androidx.lifecycle:lifecycle-viewmodel:2.8.5")
                compileOnly("androidx.lifecycle:lifecycle-runtime:2.8.5")
                // Do not bring in Material (we use Jewel) and Coroutines (the IDE has its own)
                compileOnly(compose.desktop.currentOs) {
                    exclude(group = "org.jetbrains.compose.material")
                    exclude(group = "org.jetbrains.kotlinx")
                }

                compileOnly(libs.kotlinx.coroutines.swing)
                // api(compose.desktop.currentOs) {
                //     exclude(group = "org.jetbrains.compose.material")
                //     exclude(group = "org.jetbrains.kotlinx")
                // }
                //
                // compileOnly(compose.ui)
                // compileOnly(compose.components.uiToolingPreview)
                // compileOnly(compose.components.resources)
            }
        }
    }
}
