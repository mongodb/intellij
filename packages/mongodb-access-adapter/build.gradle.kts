plugins {
    id("com.mongodb.intellij.isolated-module")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":packages:mongodb-mql-model"))
            }
        }
    }
}
