package com.example.sensewayapp1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.sensewayapp1.ui.nav.authGraph
import com.example.sensewayapp1.ui.nav.consoleGraph
import com.example.sensewayapp1.ui.nav.homeGraph
import com.example.sensewayapp1.ui.theme.SenseWayTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SenseWayTheme {
                val nav = rememberNavController()
                Scaffold { paddingValues ->
                    NavHost(
                        navController = nav,
                        startDestination = "auth",
                        modifier = Modifier.padding(paddingValues)
                    ) {
                        authGraph(nav)
                        homeGraph(nav)
                        consoleGraph(nav)
                    }
                }
            }
        }
    }
}
