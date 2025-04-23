plugins {
    id("com.mongodb.intellij.isolated-module")
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":packages:mongodb-mql-model"))
                implementation(project(":packages:mongodb-access-adapter"))
            }
        }
    }

    js {
        compilations["main"].packageJson {
            customField(
                "repository",
                mapOf(
                    "type" to "git",
                    "url" to "git+https://github.com/mongodb/intellij.git"
                )
            )
            customField("homepage", "https://github.com/mongodb/intellij")
            customField("license", "Apache-2.0")
        }
    }
}

task<Exec>("publishNpm") {
    dependsOn("jsNodeProductionLibraryDistribution")
    workingDir(layout.buildDirectory.dir("dist/js/productionLibrary"))

    commandLine("npm", "publish")
}
