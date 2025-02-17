plugins {
    id("com.mongodb.intellij.plugin-component")
}

pluginBundle {
    enableBundle = true
}

tasks {
    register("buildProperties", WriteProperties::class) {
        group = "build"

        val segmentApiKey = System.getenv("BUILD_SEGMENT_API_KEY") ?: throw GradleException(
            "Environment variable 'BUILD_SEGMENT_API_KEY' is not set. For local builds set it to empty."
        )

        destinationFile.set(
            project.layout.projectDirectory.file("src/main/resources/META-INF/build.properties")
        )
        property("pluginVersion", rootProject.version)
        property("segmentApiKey", segmentApiKey)
    }

    processResources {
        dependsOn("buildProperties")
    }
}

dependencies {
    implementation(project(":packages:mongodb-access-adapter"))
    implementation(project(":packages:mongodb-access-adapter:datagrip-access-adapter"))
    implementation(project(":packages:mongodb-mql-engines"))
    implementation(project(":packages:mongodb-dialects"))
    implementation(project(":packages:mongodb-dialects:java-driver"))
    implementation(project(":packages:mongodb-dialects:spring-criteria"))
    implementation(project(":packages:mongodb-dialects:spring-@query"))
    implementation(project(":packages:mongodb-dialects:mongosh"))
    implementation(project(":packages:mongodb-mql-model"))

    implementation(libs.mongodb.driver)
    implementation(libs.segment)
    implementation(libs.semver.parser)
}
