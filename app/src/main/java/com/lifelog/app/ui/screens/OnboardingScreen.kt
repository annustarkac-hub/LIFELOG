package com.lifelog.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.lifelog.app.ui.MainState
import com.lifelog.app.ui.components.Eyebrow
import com.lifelog.app.ui.components.NoticeBanner
import com.lifelog.app.ui.components.SectionCard
import com.lifelog.app.ui.openUsageAccessSettings
import com.lifelog.app.ui.theme.LocalProvenance

@Composable
fun OnboardingScreen(state: MainState, onAccept: () -> Unit, onRecheck: () -> Unit) {
    val ctx = LocalContext.current
    val accepted = state.settings?.onboardingAccepted == true
    var ticked by rememberSaveable { mutableStateOf(accepted) }
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Eyebrow("Welcome to", MaterialTheme.colorScheme.primary)
        Text("LIFELOG", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Understand and preserve the phone-usage history that Android makes available, and keep recording supported usage from today.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionCard {
            Text("What LIFELOG can read", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Bullets(
                "When apps move to the foreground and background (per app, not what you do inside them)",
                "When the screen becomes interactive or non-interactive, and lock/unlock moments",
                "Android startup/shutdown markers",
                "Android's own daily, monthly and yearly usage summaries",
                "Battery level samples taken when LIFELOG runs",
            )
        }
        SectionCard {
            Text("What LIFELOG cannot and will not read", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Bullets(
                "Messages, notifications, passwords, keystrokes, touches or screen contents",
                "Anything inside other apps",
                "Your full history since you bought the phone — Android only keeps recent detail (often a few days) and coarser summaries",
                "Your purchase date — you may enter it yourself; it is always marked unverified",
            )
        }
        NoticeBanner(
            "Stored only on this phone",
            "No account, no cloud, no internet permission. Uninstalling the app or resetting the phone deletes the data unless you export a backup from the Vault.",
            LocalProvenance.current.recorded,
        )
        SectionCard {
            Text("Why Usage Access?", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Android only shares usage statistics with apps you allow in Settings › Usage access. You can revoke it at any time; LIFELOG then pauses and keeps what it already recorded.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(ticked, onCheckedChange = { ticked = it; if (it) onAccept() })
            Text("I understand what is and isn't collected", style = MaterialTheme.typography.bodyMedium)
        }
        Button(
            onClick = { onAccept(); openUsageAccessSettings(ctx) },
            enabled = ticked,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) { Text("Open Usage Access settings") }
        if (accepted) {
            OutlinedButton(onClick = onRecheck, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (state.hasUsageAccess) "Access granted — continue" else "I've granted access — check again")
            }
            if (!state.hasUsageAccess) {
                Text(
                    "Find LIFELOG in the list and turn on \"Permit usage access\", then return here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bullets(vararg items: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            Row {
                Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(16.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
