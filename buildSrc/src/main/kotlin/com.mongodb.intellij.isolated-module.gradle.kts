import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.mongodb.intellij.base-module")
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
    mavenLocal()
}

val libs = the<LibrariesForLibs>()

kotlin {
    jvm {
        val main by compilations.getting
    }

    js(IR) {
        moduleName = project.name
        version = project.version

        useEsModules()
        generateTypeScriptDefinitions()
        browser {
            testTask {
                useKarma {
                    useFirefox()
                }
            }
        }

        nodejs {
            testTask {
                useMocha()
            }
        }

        binaries.library()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                compileOnly(libs.kotlin.stdlib)
                compileOnly(libs.kotlin.coroutines.core)
                compileOnly(libs.kotlin.reflect)
                implementation(libs.kotlin.collections)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.testing.kotlin.test)
                implementation(libs.testing.kotlin.coroutines)
            }
        }

        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
                implementation(libs.testing.jupiter.engine)
                implementation(libs.testing.jupiter.params)
                implementation(libs.testing.jupiter.vintage.engine)

                implementation(libs.testing.mockito.core)
                implementation(libs.testing.mockito.kotlin)
                implementation(libs.testing.kotlin.coroutines)
            }
        }

        val jsMain by getting {
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlin.coroutines.core)
            }
        }

        val jsTest by getting
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()

        extensions.configure(JacocoTaskExtension::class) {
            isJmx = true
            includes = listOf("com.mongodb.*")
            isIncludeNoLocationClasses = true
        }

        jacoco {
            toolVersion = libs.versions.jacoco.get()
            isScanForTestClasses = true
        }

        jvmArgs(
          listOf(
            "--add-opens=java.base/java.lang=ALL-UNNAMED"
          )
        )
    }
}
