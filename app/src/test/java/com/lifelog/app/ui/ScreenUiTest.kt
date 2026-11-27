package com.lifelog.app.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifelog.app.AppContainer
import com.lifelog.app.collect.Collector
import com.lifelog.app.collect.FakeUsage
import com.lifelog.app.data.AppSettings
import com.lifelog.app.data.ThemeMode
import com.lifelog.app.domain.RawUsageEvent
import com.lifelog.app.domain.UsageEventCodes as C
import com.lifelog.app.platform.AppInfo
import com.lifelog.app.platform.AppInfoSource
import com.lifelog.app.platform.BatteryReading
import com.lifelog.app.platform.BatterySource
import com.lifelog.app.platform.DeviceInfo
import com.lifelog.app.platform.DeviceInfoSource
import com.lifelog.app.ui.screens.HomeScreen
import com.lifelog.app.ui.screens.OnboardingScreen
import com.lifelog.app.ui.screens.VaultScreen
import com.lifelog.app.ui.screens.VaultViewModel
import com.lifelog.app.ui.theme.LifelogTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real screens under Robolectric. Usage data shown in the Home screenshot is a TEST FIXTURE written through
 * the real Collector from synthetic events; it is not device data.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenUiTest {
    @get:Rule val compose = createComposeRule()
    private val ctx: Application = ApplicationProvider.getApplicationContext()

    private fun shot(name: String) {
        val dir = File(System.getProperty("user.dir"), "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun mainState(access: Boolean, accepted: Boolean) = MainState(settings = AppSettings(onboardingAccepted = accepted), hasUsageAccess = access)

    @Test fun onboardingDisclosesLimitsAndGatesTheSettingsButton() {
        compose.setContent { LifelogTheme(ThemeMode.DARK) { OnboardingScreen(mainState(false, false), {}, {}) } }
        compose.onNodeWithText("What LIFELOG cannot and will not read").assertIsDisplayed()
        compose.onNodeWithText("Open Usage Access settings").assertIsNotEnabled()
        compose.onAllNodesWithText("all phone activity", substring = true, ignoreCase = true).fetchSemanticsNodes().let { check(it.isEmpty()) { "deceptive wording found" } }
        shot("01_onboarding")
    }

    @Test fun homeShowsSeparateMetricsAndPermissionBanner() {
        val container = AppContainer(ctx)
        val now = System.currentTimeMillis()
        val usage = FakeUsage().apply {
            fun s(p: String, a: Long, b: Long) { events += RawUsageEvent(a, C.ACTIVITY_RESUMED, p, "$p.M"); events += RawUsageEvent(b, C.ACTIVITY_PAUSED, p, "$p.M") }
            val m = 60_000L
            for (d in 0..6) {
                val base = now - d * 86_400_000L - 6 * 3_600_000L
                s("com.example.reader", base, base + (35 + d * 7) * m)
                s("com.example.chat", base + 50 * m, base + (70 + d * 4) * m)
                events += RawUsageEvent(base - m, C.SCREEN_INTERACTIVE, null, null)
                events += RawUsageEvent(base + (90 + d * 5) * m, C.SCREEN_NON_INTERACTIVE, null, null)
            }
            retentionStart = now - 7 * 86_400_000L
        }
        val collector = Collector(
            container.repo, usage,
            object : AppInfoSource { override fun info(packageName: String) = AppInfo(packageName, packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() } + " (fixture)", true) },
            object : BatterySource { override fun read() = BatteryReading(64, "DISCHARGING", "UNPLUGGED") },
            object : DeviceInfoSource { override fun read() = DeviceInfo("Fixture", "Test", "15", 35); override fun isInteractive() = true },
            container.clock,
        ) { true }
        runBlocking { collector.collect() }
        val vm = HomeViewModel(container)
        val state = MainState(settings = AppSettings(onboardingAccepted = true), hasUsageAccess = false, checkpoint = runBlocking { container.repo.checkpoint() }, profile = runBlocking { container.repo.profile() })
        compose.setContent {
            LifelogTheme(ThemeMode.DARK) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    HomeScreen(vm, state, PaddingValues(16.dp), { null }, {}, {}, {}, {})
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Screen-on today", ignoreCase = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Usage Access is off — collection paused").assertIsDisplayed()
        compose.onNodeWithText("Screen-on today", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("App foreground today", ignoreCase = true).assertIsDisplayed()
        shot("02_home_fixture_no_access")
    }

    @Test fun vaultOffersExplicitBackupAndDelete() {
        val vm = VaultViewModel(AppContainer(ctx))
        compose.setContent { LifelogTheme(ThemeMode.DARK) { Box(Modifier.background(MaterialTheme.colorScheme.background)) { VaultScreen(vm, PaddingValues(16.dp)) } } }
        compose.onNodeWithText("Export unencrypted backup").assertIsDisplayed()
        compose.onRoot().performScrollToNode(hasText("Delete all local data"))
        shot("03_vault")
    }
}
