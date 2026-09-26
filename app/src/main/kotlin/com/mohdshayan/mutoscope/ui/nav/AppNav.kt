package com.mohdshayan.mutoscope.ui.nav

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mohdshayan.mutoscope.ui.editor.EditorScreen
import com.mohdshayan.mutoscope.ui.preview.PreviewScreen
import com.mohdshayan.mutoscope.ui.projects.ProjectsScreen
import com.mohdshayan.mutoscope.ui.settings.SettingsScreen
import com.mohdshayan.mutoscope.ui.theme.LocalReducedMotion
import kotlinx.serialization.Serializable

@Serializable
object Projects

@Serializable
data class Editor(val projectId: Long)

@Serializable
data class Preview(val projectId: Long)

@Serializable
object Settings

@Composable
fun AppNav() {
    val navController = rememberNavController()
    val reduced = LocalReducedMotion.current
    val enter: EnterTransition = if (reduced) EnterTransition.None else fadeIn(tween(180))
    val exit: ExitTransition = if (reduced) ExitTransition.None else fadeOut(tween(120))
    NavHost(
        navController = navController,
        startDestination = Projects,
        enterTransition = { enter },
        exitTransition = { exit },
        popEnterTransition = { enter },
        popExitTransition = { exit },
    ) {
        composable<Projects> {
            ProjectsScreen(
                onOpen = { id -> navController.navigate(Editor(id)) { launchSingleTop = true } },
                onSettings = { navController.navigate(Settings) { launchSingleTop = true } },
            )
        }
        composable<Editor> {
            EditorScreen(
                onBack = { navController.back() },
                onPreview = { id -> navController.navigate(Preview(id)) { launchSingleTop = true } },
            )
        }
        composable<Preview> {
            PreviewScreen(onBack = { navController.back() })
        }
        composable<Settings> {
            SettingsScreen(onBack = { navController.back() })
        }
    }
}

/** Pops one screen, but never the Projects start screen: a double tap on Back would leave a blank window. */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}
