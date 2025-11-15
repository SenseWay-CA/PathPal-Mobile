package com.example.sensewayapp1.ui.nav

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.example.sensewayapp1.ui.auth.LoginScreen
import com.example.sensewayapp1.ui.console.DeviceConsoleScreen
import com.example.sensewayapp1.ui.home.HomeScreen

fun NavGraphBuilder.authGraph(nav: NavHostController) {
    composable("auth") {
        val ctx = LocalContext.current
        LoginScreen(
            onLoggedIn = {
                nav.navigate("home") { popUpTo("auth") { inclusive = true } }
            },
            onRegister = {
                ctx.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://YOUR-WEBSITE/register"))
                )
            }
        )
    }
}

fun NavGraphBuilder.homeGraph(nav: NavHostController) {
    composable("home") {
        HomeScreen(
            onOpenConsole = { nav.navigate("console") },
            onOpenSettings = { /* TODO: settings screen */ }
        )
    }
}

fun NavGraphBuilder.consoleGraph(nav: NavHostController) {
    composable("console") { DeviceConsoleScreen() }
}
