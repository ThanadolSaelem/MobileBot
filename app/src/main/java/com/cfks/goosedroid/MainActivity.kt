package com.cfks.goosedroid

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import androidx.navigation.navDeepLink
import com.cfks.goosedroid.ui.screens.ChatHubScreen
import com.cfks.goosedroid.ui.screens.ChatScreen
import com.cfks.goosedroid.ui.screens.ConversationsScreen
import com.cfks.goosedroid.ui.screens.EditorScreen
import com.cfks.goosedroid.ui.screens.PlaygroundScreen
import com.cfks.goosedroid.ui.screens.AiSettingsScreen
import com.cfks.goosedroid.ui.theme.GooseDesktopTheme
import com.cfks.goosedroid.ui.alert.AppAlertHost
import com.cfks.goosedroid.notify.SystemNotifier
import com.cfks.goosedroid.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SystemNotifier.ensureChannels(this)
        checkPermissions()
        handleIntent(intent)
        
        setContent {
            GooseDesktopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigation(mainViewModel, this@MainActivity)
                        AppAlertHost(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(horizontal = 16.dp, vertical = 48.dp)
                        )
                    }
                }
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            
            val contentToSend = sharedText ?: sharedUri?.toString()
            
            if (contentToSend != null) {
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_RECEIVE_SHARE
                    putExtra("shared_text", contentToSend)
                }
                startForegroundService(serviceIntent)
                finish() // Close the activity after sharing to keep it running in the background
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
                onNavigateToSettings = { navController.navigate("settings") },
                onNavigateToChatHub = { navController.navigate("chat_hub") },
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
        composable("settings") {
            AiSettingsScreen(navController = navController, viewModel = viewModel)
        }
        composable(
            "chat/{name}?convId={convId}",
            arguments = listOf(
                navArgument("convId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(
                navDeepLink { uriPattern = "goosedroid://chat/{name}?convId={convId}" }
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Character"
            val convId = backStackEntry.arguments?.getString("convId")?.toLongOrNull()
            ChatScreen(
                characterName = name,
                onNavigateBack = { navController.popBackStack() },
                onOpenConversations = { navController.navigate("chats/$name") },
                conversationId = convId
            )
        }

        composable("chats/{name}") { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Character"
            ConversationsScreen(
                characterName = name,
                onNavigateBack = { navController.popBackStack() },
                onOpenConversation = { id ->
                    navController.navigate("chat/$name?convId=$id")
                }
            )
        }

        composable("chat_hub") {
            ChatHubScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenChat = { name -> navController.navigate("chat/$name") }
            )
        }
    }
}
