plugins {
    id("com.mongodb.intellij.isolated-module")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                // implementation(libs.owasp.encoder)
                implementation(libs.semver.parser)
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.bson.kotlin)
            }
        }
    }
}
