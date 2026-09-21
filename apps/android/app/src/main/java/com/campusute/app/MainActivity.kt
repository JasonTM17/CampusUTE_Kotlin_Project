package com.campusute.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.campusute.app.core.designsystem.theme.CampusTheme
import com.campusute.app.core.data.SessionEvents
import com.campusute.app.core.data.SessionRepository
import com.campusute.app.core.security.TokenStore
import com.campusute.app.feature.appshell.CampusApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    @Inject
    lateinit var sessionRepository: SessionRepository

    @Inject
    lateinit var sessionEvents: SessionEvents

    private var signedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signedIn = tokenStore.accessToken() != null
        // A refresh that fails three layers below the UI clears storage but cannot reach any
        // composable; without this collector the shell stays on screen issuing requests it can
        // no longer authorise, and every tab reports an empty list.
        lifecycleScope.launch {
            sessionEvents.ended.collect { reason -> if (reason != null) signedIn = false }
        }
        setContent {
            val ended by sessionEvents.ended.collectAsStateWithLifecycle()
            CampusTheme {
                CampusApp(
                    signedIn = signedIn,
                    onSessionEnded = {
                        onSessionEnded()
                    },
                    onSessionStarted = {
                        sessionEvents.consume()
                        signedIn = true
                    },
                    sessionRepository = sessionRepository,
                    bouncedReason = ended?.message,
                )
            }
        }
    }

    private fun onSessionEnded() {
        sessionEvents.consume()
        signedIn = false
    }
}
