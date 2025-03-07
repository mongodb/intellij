plugins {
    id("com.mongodb.intellij.plugin-component")
    id("org.jetbrains.compose") version ("1.7.3")
    id("org.jetbrains.kotlin.plugin.compose") version ("2.0.21")
}

repositories {
    google()
    maven("https://www.jetbrains.com/intellij-repository/releases/")
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    maven("https://packages.jetbrains.team/maven/p/kpm/public/")
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
    implementation(project(":packages:mongodb-design-system")) {
        exclude(group = "org.jetbrains.compose.material")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.jetbrains.jewel", module = "jewel-int-ui-standalone-243")
    }

    compileOnly("org.jetbrains.jewel:jewel-ide-laf-bridge-243:0.27.0")

    compileOnly(compose.desktop.common)
    compileOnly(libs.kotlinx.coroutines.swing)

    implementation(libs.mongodb.driver)
    implementation(libs.segment)
    implementation(libs.semver.parser)
}
