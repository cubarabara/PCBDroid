@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pcbdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pcbdroid.ui.screens.PcbEditorScreen
import com.pcbdroid.ui.screens.ProjectHomeScreen
import com.pcbdroid.ui.theme.PCBDroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PCBDroidTheme {
                PCBDroidNavHost()
            }
        }
    }
}

@Composable
fun PCBDroidNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController    = navController,
        startDestination = "home"
    ) {
        composable("home") {
            // ProjectHomeScreen pakai onOpenEditor(PcbProject)
            ProjectHomeScreen(
                onOpenEditor = { project ->
                    navController.navigate("editor/${project.id}")
                }
            )
        }
        composable("editor/{projectId}") {
            PcbEditorScreen()
        }
    }
}
