package com.cfks.goosedroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.cfks.goosedroid.ui.screens.ChatScreen
import com.cfks.goosedroid.ui.screens.EditorScreen
import com.cfks.goosedroid.ui.screens.PlaygroundScreen
import com.cfks.goosedroid.ui.theme.GooseDesktopTheme
import com.cfks.goosedroid.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermissions()
        setContent {
            GooseDesktopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(mainViewModel, this)
                }
            }
        }
    }
    
    private fun checkPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel, activity: ComponentActivity) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "playground") {
        composable("playground") {
            PlaygroundScreen(
                viewModel = viewModel,
                onNavigateToEditor = { charId -> 
                    if (charId != null) {
                        navController.navigate("editor?id=$charId")
                    } else {
                        navController.navigate("editor")
                    }
                },
                onNavigateToChat = { name -> navController.navigate("chat/$name") },
                onLaunchOverlay = { character ->
                    if (Settings.canDrawOverlays(activity)) {
                        com.cfks.goosedroid.brain.CharacterRegistry.registerCharacterData(character.id, character.spriteSheetData)
                        val intent = Intent(activity, OverlayService::class.java).apply { 
                            action = OverlayService.ACTION_SPAWN
                            putExtra("id", character.id)
                            putExtra("name", character.spriteSheetData.name)
                            putExtra("columns", character.spriteSheetData.columns)
                            putExtra("rows", character.spriteSheetData.rows)
                            putExtra("sprite_uri", character.spriteSheetData.uri)
                        }
                        activity.startForegroundService(intent)
                    } else {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                        activity.startActivity(intent)
                    }
                }
            )
        }
        composable(
            "editor?id={id}",
            arguments = listOf(navArgument("id") { nullable = true; defaultValue = null })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id")
            val editTarget = if (id != null) viewModel.getCharacterById(id) else null
            
            // Collect all existing names except the one we are editing
            // Wait, we need all characters from DB to check for name collisions
            val masterList = com.cfks.goosedroid.data.CharacterRepository.loadCharacters(activity)
            val existingNames = masterList.filter { it.id != id }.map { it.spriteSheetData.name }

            EditorScreen(
                existingNames = existingNames,
                editTarget = editTarget,
                onNavigateBack = { navController.popBackStack() },
                onSpawn = { character ->
                    viewModel.spawnCharacter(character)
                    navController.popBackStack()
                }
            )
        }
        composable("chat/{name}") { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Character"
            ChatScreen(
                characterName = name,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
