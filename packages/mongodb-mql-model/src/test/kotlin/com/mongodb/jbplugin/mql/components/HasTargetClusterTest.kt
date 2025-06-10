package com.mongodb.jbplugin.mql.components

import io.github.z4kn4fein.semver.Version
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HasTargetClusterTest {
    @Test
    fun can_parse_a_default_version() {
        val cluster = HasTargetCluster(Version.parse("8.0.0"))
        assertEquals("8.0.0", cluster.version.toString())
    }
}
