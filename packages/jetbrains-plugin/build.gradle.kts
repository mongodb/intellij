import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    id("com.mongodb.intellij.plugin-component")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

repositories {
    google()

    intellijPlatform {
        defaultRepositories()
        intellijDependencies()
        releases()
    }

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

configurations {
    runtimeOnly {
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
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

    compileOnly(compose.ui)
    compileOnly(compose.runtime)
    compileOnly(compose.foundation)
    compileOnly(compose.desktop.common)
    compileOnly(compose.desktop.currentOs)

    implementation(libs.compose.jewel.laf.bridge)
    implementation(libs.compose.jewel.laf.standalone) {
        exclude(group = "org.jetbrains.kotlinx")
    }

    implementation(libs.mongodb.driver)
    implementation(libs.segment)
    implementation(libs.semver.parser)

    testImplementation(compose.runtime)
    testImplementation(compose.foundation)
    testImplementation(compose.desktop.common)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.compose.jewel.laf.bridge)
    testImplementation(libs.compose.jewel.laf.standalone)
    @OptIn(ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest) {
        exclude(group = "org.jetbrains.kotlinx")
    }
    testImplementation(libs.kotlin.coroutines.core)
    testImplementation(libs.testing.kotlin.coroutines.test)
}
