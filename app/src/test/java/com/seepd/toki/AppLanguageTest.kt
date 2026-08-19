package com.seepd.toki

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun storedLanguageValuesAreStable() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStoredValue(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromStoredValue("unknown"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromStoredValue("en"))
        assertEquals(AppLanguage.CHINESE, AppLanguage.fromStoredValue("zh"))
    }
}
