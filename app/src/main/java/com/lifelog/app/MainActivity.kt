package com.lifelog.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifelog.app.data.ThemeMode
import com.lifelog.app.ui.LifelogRoot
import com.lifelog.app.ui.MainViewModel
import com.lifelog.app.ui.screens.OnboardingScreen
import com.lifelog.app.ui.theme.LifelogTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as LifelogApp).container
        setContent {
            val main: MainViewModel = viewModel(factory = viewModelFactory { initializer { MainViewModel(container) } })
            val state by main.state.collectAsStateWithLifecycle()
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { main.onResume() }
            val settings = state.settings
            LifelogTheme(settings?.theme ?: ThemeMode.DARK) {
                when {
                    settings == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    !settings.onboardingAccepted || !state.hasUsageAccess && state.profile == null ->
                        OnboardingScreen(state, onAccept = main::acceptOnboarding, onRecheck = main::onResume)
                    else -> LifelogRoot(container, main, state)
                }
            }
        }
    }
}
