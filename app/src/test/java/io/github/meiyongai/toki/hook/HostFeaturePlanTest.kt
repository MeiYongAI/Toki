package io.github.meiyongai.toki.hook

import io.github.meiyongai.toki.provider.ConfigSchema
import io.github.meiyongai.toki.provider.ConfigSnapshot
import java.util.Properties
import org.junit.Assert.*
import org.junit.Test

class HostFeaturePlanTest {
    @Test fun downloadRepairRequiresVerifiedSymbolsButPathOnlyDoesNot() {
        val repair = HostFeaturePlan(config("download_force_no_watermark" to true))
        assertTrue(repair.requiresScan)
        assertEquals(setOf(HostSymbol.DOWNLOAD_SOURCE), repair.symbols)
        val pathOnly = HostFeaturePlan(config("download_path_enabled" to true))
        assertFalse(pathOnly.requiresScan)
        assertEquals(setOf("DownloadHook"), pathOnly.directHostFeatures)
        val requested = config("download_path_enabled" to true, "download_force_no_watermark" to true)
        assertTrue(pathOnly.requiresRestart("DownloadHook", requested))
        assertFalse(pathOnly.apply(requested).boolean("download_force_no_watermark"))
    }

    private fun config(vararg values: Pair<String, Any>, revision: Long = 1) = ConfigSnapshot(mapOf(*values), revision)

    @Test fun platformAndDirectFeaturesNeverRequireDexAdaptation() {
        for (feature in HostFeaturePlan.platform + HostFeaturePlan.directHost) {
            val key = ConfigSchema.featureSwitches.getValue(feature).first()
            val plan = HostFeaturePlan(config(key to true))
            assertEquals(feature, setOf(feature), plan.features)
            assertFalse(feature, plan.requiresScan)
        }
    }

    @Test fun cleanOwnsProgressEvenWhenProgressPreservationIsOff() {
        val plan = HostFeaturePlan(config("clean_mode_on_play" to true))
        assertEquals(setOf("ProgressBarHook", "AutoCleanModeHook"), plan.features)
        assertTrue(plan.symbols.containsAll(setOf(HostSymbol.SEEK_BAR, HostSymbol.SEEK_CONTROLLER, HostSymbol.PLAYER_CONTROLLER)))
        assertFalse(plan.symbols.contains(HostSymbol.SPEED_OPTIONS))
        assertTrue(HostFeaturePlan(config("clean_mode_show_progress_bar" to true)).features.isEmpty())
    }

    @Test fun lateCleanAndImmersiveRequestsCannotPartiallyActivateExistingHooks() {
        val progress = HostFeaturePlan(config("always_show_progress_bar" to true))
        val requested = config("always_show_progress_bar" to true, "clean_mode_on_play" to true,
            "clean_mode_show_progress_bar" to true, revision = 2)
        assertFalse(progress.apply(requested).boolean("clean_mode_on_play"))
        assertFalse(progress.apply(requested).boolean("clean_mode_show_progress_bar"))
        assertTrue(progress.requiresRestart("AutoCleanModeHook", requested))
        val clean = HostFeaturePlan(config("clean_mode_on_play" to true))
        val immersive = config("clean_mode_on_play" to true, "immersive_full_screen" to true)
        assertFalse(clean.apply(immersive).boolean("immersive_full_screen"))
        assertTrue(clean.requiresRestart("ImmersiveFullScreenHook", immersive))
    }

    @Test fun startupSettingsKeepAppliedStateAndReportRestartOnlyForRelevantFields() {
        val plan = HostFeaturePlan(config("language_spoof_enabled" to true, "custom_language" to "ja", revision = 3))
        val disabled = config("language_spoof_enabled" to false, "custom_language" to "en", revision = 4)
        assertTrue(plan.apply(disabled).boolean("language_spoof_enabled"))
        assertEquals("ja", plan.apply(disabled).string("custom_language"))
        assertTrue(plan.requiresRestart("LocaleHook", disabled))
        assertEquals(3L, plan.appliedRevision("LocaleHook", disabled))
        val unrelated = config("language_spoof_enabled" to true, "custom_language" to "ja", "feed_remove_live" to true, revision = 5)
        assertFalse(plan.requiresRestart("LocaleHook", unrelated))
    }

    @Test fun installedLiveFeaturesCanDisableAndReenableWithinTheirSession() {
        val plan = HostFeaturePlan(config("feed_remove_ads" to true, "clean_mode_on_play" to true))
        val disabled = config("feed_remove_ads" to false, "clean_mode_on_play" to false)
        assertFalse(plan.apply(disabled).boolean("feed_remove_ads"))
        assertFalse(plan.apply(disabled).boolean("clean_mode_on_play"))
        assertFalse(plan.requiresRestart("FeedFilterHook", disabled))
        assertTrue(plan.apply(config("feed_remove_live" to true)).boolean("feed_remove_live"))
    }

    @Test fun fixedSpeedAndMenuHaveSeparateSymbolContracts() {
        val fixed = HostFeaturePlan(config("fixed_speed_enabled" to true))
        assertEquals(setOf(HostSymbol.PLAYER_CONTROLLER, HostSymbol.PLAYER_MANAGER, HostSymbol.SPEED_MANAGER), fixed.symbols)
        assertFalse(fixed.apply(config("fixed_speed_enabled" to true, "speed_expand_enabled" to true)).boolean("speed_expand_enabled"))
        val menu = HostFeaturePlan(config("speed_expand_enabled" to true))
        assertFalse(menu.symbols.contains(HostSymbol.PLAYER_CONTROLLER))
        assertTrue(menu.symbols.contains(HostSymbol.THREE_TIMES_SPEED))
    }

    @Test fun speedMemoryCannotClaimAnImportFromOutsideItsStartupSession() {
        val original = ConfigSnapshot(mapOf("fixed_speed_enabled" to true), revision = 3L, importRevision = 2L)
        val imported = ConfigSnapshot(mapOf("fixed_speed_enabled" to true), revision = 4L, importRevision = 4L)
        val plan = HostFeaturePlan(original)
        assertEquals(2L, plan.apply(imported).importRevision)
        assertTrue(plan.requiresRestart("PlaybackSpeedHook", imported))
        assertTrue(plan.requiresRestart("SpeedOptions", imported))
        assertEquals(3L, plan.appliedRevision("PlaybackSpeedHook", imported))
    }

    @Test fun unavailableStartupHasNoInstallationOrLateActivation() {
        val plan = HostFeaturePlan(null)
        assertTrue(plan.features.isEmpty())
        val later = config("sim_spoof_enabled" to true)
        assertFalse(plan.apply(later).boolean("sim_spoof_enabled"))
        assertTrue(plan.requiresRestart("SimHook", later))
    }

    @Test fun failedInstallationRemovesItsEffectiveScopeAndDependentCleanState() {
        val requested = config("clean_mode_on_play" to true, "always_show_progress_bar" to true)
        val cleanFailed = HostFeaturePlan(requested)
        cleanFailed.reject("AutoCleanModeHook")
        assertFalse(cleanFailed.apply(requested).boolean("clean_mode_on_play"))
        assertTrue(cleanFailed.apply(requested).boolean("always_show_progress_bar"))
        val progressFailed = HostFeaturePlan(requested)
        progressFailed.reject("ProgressBarHook")
        assertFalse(progressFailed.apply(requested).boolean("clean_mode_on_play"))
        assertFalse(progressFailed.apply(requested).boolean("always_show_progress_bar"))
    }

    @Test fun selectedRulesExcludeUnrelatedSymbolsAndCoverageIsExplicit() {
        val rules = javaClass.getResourceAsStream("/toki-host-rules.tsv")!!.bufferedReader().use { it.readText() }
        val targets = setOf(HostSymbol.COMMENT_TRANSLATION, HostSymbol.TRANSLATION_REVERSE)
        val selected = HostSymbols.selectRules(rules, targets)
        assertEquals(targets.map { it.name }.toSet(), selected.lineSequence().filter { it.isNotBlank() }.map { it.substringBefore('\t') }.toSet())
        assertThrows(IllegalStateException::class.java) { HostSymbols.selectRules(selected, setOf(HostSymbol.CLEAN)) }
        assertThrows(IllegalStateException::class.java) { HostSymbols.coverage(Properties()) }
        assertEquals(targets, HostSymbols.coverage(Properties().apply {
            setProperty("cache.symbols", targets.joinToString(",") { it.name })
        }))
    }

    @Test fun failedRequiredSymbolCannotSatisfyCacheCoverage() {
        val stored = Properties().apply {
            setProperty("cache.symbols", HostSymbol.COMMENT_TRANSLATION.name)
            setProperty("error.${HostSymbol.COMMENT_TRANSLATION.name}", "候选数量=0")
        }
        val coverage = HostSymbols.coverage(stored)
        assertFalse(HostSymbols.cacheUsable(stored, coverage, setOf(HostSymbol.COMMENT_TRANSLATION)))
        stored.remove("error.${HostSymbol.COMMENT_TRANSLATION.name}")
        assertFalse(HostSymbols.cacheUsable(stored, coverage, setOf(HostSymbol.COMMENT_TRANSLATION)))
        stored.setProperty(HostSymbol.COMMENT_TRANSLATION.name, "com.example.Resolved")
        assertTrue(HostSymbols.cacheUsable(stored, coverage, setOf(HostSymbol.COMMENT_TRANSLATION)))
    }

    @Test fun packagedRulesHaveFiveColumnsUniqueContractsAndEveryLogicalSymbol() {
        val rows = javaClass.getResourceAsStream("/toki-host-rules.tsv")!!.bufferedReader().use { reader ->
            reader.readLines().filter { it.isNotBlank() && !it.startsWith('#') }.map { it.split('\t') }
        }
        assertTrue(rows.isNotEmpty())
        assertTrue(rows.all { it.size == 5 && it.none(String::isEmpty) })
        assertEquals(rows.size, rows.distinct().size)
        assertEquals(HostSymbol.entries.map { it.name }.toSet(), rows.map { it[0] }.toSet())
        val digest = Regex("[0-9a-f]{64}")
        assertTrue(rows.all { row -> (1..3).all { digest.matches(row[it]) } })
    }
}
