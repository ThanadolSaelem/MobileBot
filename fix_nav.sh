sed -i 's/fun EditorScreen()/fun EditorScreen(onNavigateBack: () -> Unit)/g' app/src/main/java/com/cfks/goosedroid/ui/screens/EditorScreen.kt
sed -i 's/IconButton(onClick = { \/\* Save action \*\/ }) {/IconButton(onClick = onNavigateBack) {/g' app/src/main/java/com/cfks/goosedroid/ui/screens/EditorScreen.kt
sed -i 's/Icon(Icons.Default.Save, contentDescription = "Save", tint = TdsmText)/Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TdsmText)/g' app/src/main/java/com/cfks/goosedroid/ui/screens/EditorScreen.kt
sed -i 's/EditorScreen()/EditorScreen(onNavigateBack = { navController.popBackStack() })/g' app/src/main/java/com/cfks/goosedroid/MainActivity.kt
