package com.mongodb.jbplugin.meta

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BuildInformationTest {
    @Test
    fun `loads all build information from the resource file`() {
        assertEquals("9.21.22", BuildInformation.pluginVersion)
        assertEquals("SOME_KEY", BuildInformation.segmentApiKey)
    }
}
