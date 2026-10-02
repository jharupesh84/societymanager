package com.society.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.society.app.ui.screens.LoginScreen
import com.society.app.ui.screens.MainScreen
import com.society.app.ui.screens.WelcomeScreen
import com.society.app.ui.theme.SocietyAppTheme
import com.society.app.ui.viewmodel.SocietyViewModel
import com.society.app.ui.viewmodel.SocietyViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: SocietyViewModel by viewModels {
        SocietyViewModelFactory((application as SocietyApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SocietyAppTheme {
                // Always require login on app launch
                var isLoggedIn by remember { mutableStateOf(false) }
                // After login, show Category / Fund selection
                var showSelectionScreen by remember { mutableStateOf(true) }

                fun performLogout() {
                    isLoggedIn = false
                    showSelectionScreen = true
                }

                if (!isLoggedIn) {
                    // Step 1: Admin Login Screen
                    LoginScreen(
                        onLoginSuccess = { _ ->
                            isLoggedIn = true
                            showSelectionScreen = true
                        }
                    )
                } else if (showSelectionScreen) {
                    // Step 2: Category / Fund Selection Screen
                    WelcomeScreen(
                        viewModel = viewModel,
                        onEnterApp = {
                            showSelectionScreen = false
                        },
                        onLogout = { performLogout() }
                    )
                } else {
                    // Step 3: Main Dashboard (Collections, Expenses, Reports for selected Fund)
                    MainScreen(
                        viewModel = viewModel,
                        onBackToWelcome = { showSelectionScreen = true },
                        onLogout = { performLogout() }
                    )
                }
            }
        }
    }
}
