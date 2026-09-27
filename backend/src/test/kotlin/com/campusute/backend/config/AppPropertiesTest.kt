package com.campusute.backend.config

import kotlin.test.Test
import kotlin.test.assertFalse

class AppPropertiesTest {
    @Test
    fun `demo mode is disabled unless explicitly enabled`() {
        assertFalse(AppProperties().demoMode)
    }
}
