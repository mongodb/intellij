plugins {
    id("com.mongodb.intellij.isolated-module")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":packages:mongodb-mql-engines"))
                implementation(project(":packages:mongodb-mql-model"))
            }
        }

        jvmMain {
            dependencies {
                implementation(libs.bson.kotlin)
            }
        }
    }
}
