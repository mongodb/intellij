package com.mongodb.jbplugin.meta

import java.util.*

/**
 * This provides access to the build information generated at build time in Gradle.
 * To add new fields to this object, do the following:
 * 1. Go to packages/jetbrains-plugin/build.gradle.kts
 * 2. Go to the task "buildProperties"
 * 3. Add the new property there. Use an existing one as a sample.
 * 4. Add the new property here. Use an existing one as a sample.
 * 5. Add a fake value into packages/jetbrains-plugin/src/test/resources/META-INF/build.properties
 * 6. Add the new property to the test in BuildInformationTest.kt
 */
object BuildInformation {
    private val properties: Properties = Properties().also {
        val buildProps = BuildInformation::class.java.getResourceAsStream(
            "/META-INF/build.properties"
        )
        if (buildProps != null) {
            it.load(buildProps)
        } else {
            it["pluginVersion"] = "<unk>"
            it["segmentApiKey"] = "<unk>"
        }
    }
    val pluginVersion: String by properties
    val segmentApiKey: String by properties
}
