package com.campusute.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.campusute.app.core.designsystem.theme.CampusTheme
import com.campusute.app.core.security.TokenStore
import com.campusute.app.feature.appshell.CampusApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var tokenStore: TokenStore

    private var signedIn by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signedIn = tokenStore.accessToken() != null
        setContent {
            CampusTheme {
                CampusApp(
                    signedIn = signedIn,
                    onSessionEnded = { signedIn = false },
                    onSessionStarted = { signedIn = true },
                )
            }
        }
    }
}
