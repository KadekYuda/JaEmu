package com.emulator.j2me

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.emulator.j2me.core.DexTranslator
import com.emulator.j2me.data.GameDatabase
import com.emulator.j2me.data.GameModel
import com.emulator.j2me.data.GameSettings
import com.emulator.j2me.data.GameSettingsStore
import com.emulator.j2me.data.Thumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.zip.ZipFile

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            J2meEmulatorTheme {
                MainScreen()
            }
        }
    }
}

// A beautiful Dark Cyberpunk/Retro Theme using Material 3
@Composable
fun J2meEmulatorTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFFBB86FC),
        secondary = Color(0xFF03DAC6),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onPrimary = Color.Black,
        onSecondary = Color.Black,
        onBackground = Color.White,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF2C2C2C),
        outline = Color(0xFF3E3E3E)
    )

    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography(),
        content = content
    )
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { GameDatabase(context) }
    val settingsStore = remember { GameSettingsStore(context) }
    
    var gamesList by remember { mutableStateOf(emptyList<GameModel>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var activeGameForDetail by remember { mutableStateOf<GameModel?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf("") }
    
    // Load games on start
    LaunchedEffect(Unit) {
        gamesList = db.loadGames()
    }

    val pickJarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                showImportDialog = true
                importStatus = "Membaca file JAR..."
                val success = importJar(context, it, db, 
                    onProgress = { importStatus = it },
                    onComplete = {
                        gamesList = db.loadGames()
                        showImportDialog = false
                        Toast.makeText(context, "Game berhasil diimpor!", Toast.LENGTH_SHORT).show()
                    },
                    onError = { err ->
                        showImportDialog = false
                        Toast.makeText(context, "Gagal impor: $err", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                modifier = Modifier.blur(0.dp) // Glassmorphic base
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.SportsEsports, contentDescription = "Games") },
                    label = { Text("Games") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Info, contentDescription = "About") },
                    label = { Text("About") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = { pickJarLauncher.launch("application/java-archive") },
                    containerColor = MaterialTheme.colorScheme.secondary,
                    shape = CircleShape
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Import Game", tint = Color.Black)
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0C1B),
                            Color(0xFF121212)
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> GameLibraryTab(
                    games = gamesList,
                    onGameClick = { activeGameForDetail = it },
                    onGameLaunch = { launchGame(context, it) }
                )
                1 -> GlobalSettingsTab()
                2 -> AboutTab()
            }

            // Game details sheet dialog
            activeGameForDetail?.let { game ->
                val settings = remember(game.id) { settingsStore.loadOrDefault(game) }
                GameDetailDialog(
                    game = game,
                    settings = settings,
                    onDismiss = { activeGameForDetail = null },
                    onLaunch = { 
                        activeGameForDetail = null
                        launchGame(context, game) 
                    },
                    onDelete = {
                        db.removeGame(game.id)
                        settingsStore.remove(game.id)
                        // Delete files
                        File(game.jarPath).delete()
                        File(game.dexPath).delete()
                        game.iconPath?.let { File(it).delete() }
                        Thumbnails.file(context, game.id).delete()
                        gamesList = db.loadGames()
                        activeGameForDetail = null
                        Toast.makeText(context, "Game dihapus", Toast.LENGTH_SHORT).show()
                    },
                    onSaveSettings = { updatedSettings ->
                        settingsStore.save(game.id, updatedSettings)
                        activeGameForDetail = null
                    }
                )
            }

            // Import progress dialog
            if (showImportDialog) {
                Dialog(onDismissRequest = {}) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Mengimpor Game Java",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = importStatus,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GameLibraryTab(
    games: List<GameModel>,
    onGameClick: (GameModel) -> Unit,
    onGameLaunch: (GameModel) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Retro J2ME Library",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (games.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Belum ada game.",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "Sentuh tombol + untuk mengimpor JAR.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(games) { game ->
                    GameCard(
                        game = game,
                        onClick = { onGameClick(game) },
                        onDoubleTap = { onGameLaunch(game) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GameCard(
    game: GameModel,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onClick,
                onDoubleClick = onDoubleTap
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val context = LocalContext.current
            // Prefer the gameplay thumbnail (captured on first frame); fall back to
            // the JAR icon, then a generic placeholder.
            val previewModel = remember(game.id) {
                val thumb = Thumbnails.file(context, game.id)
                when {
                    thumb.exists() -> thumb
                    game.iconPath != null -> File(game.iconPath)
                    else -> null
                }
            }
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 108.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2E2E2E)),
                contentAlignment = Alignment.Center
            ) {
                if (previewModel != null) {
                    AsyncImage(
                        model = previewModel,
                        contentDescription = game.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Gamepad,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = game.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = game.vendor,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${"%.1f".format(game.sizeBytes / 1024.0 / 1024.0)} MB",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun GameDetailDialog(
    game: GameModel,
    settings: GameSettings,
    onDismiss: () -> Unit,
    onLaunch: () -> Unit,
    onDelete: () -> Unit,
    onSaveSettings: (GameSettings) -> Unit
) {
    var widthText by remember { mutableStateOf(settings.targetWidth.toString()) }
    var heightText by remember { mutableStateOf(settings.targetHeight.toString()) }
    var scaleMode by remember { mutableStateOf(settings.scaleMode) }
    var smoothScaling by remember { mutableStateOf(settings.smoothScaling) }
    var opacity by remember { mutableFloatStateOf(settings.keypadOpacity) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2E2E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (game.iconPath != null) {
                            AsyncImage(
                                model = File(game.iconPath),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(Icons.Filled.Gamepad, null, tint = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(game.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${game.vendor} • v${game.version}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Settings
                Text("Resolusi Canvas Game", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        label = { Text("Lebar (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { heightText = it },
                        label = { Text("Tinggi (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text("Skala Tampilan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("FIT", "STRETCH", "ORIGINAL").forEach { mode ->
                        FilterChip(
                            selected = scaleMode == mode,
                            onClick = { scaleMode = mode },
                            label = { Text(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Penghalusan (Smoothing)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            if (smoothScaling) "Halus / bilinear" else "Tajam / nearest-neighbor",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(checked = smoothScaling, onCheckedChange = { smoothScaling = it })
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Transparansi Keypad: ${(opacity * 100).toInt()}%", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Slider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 0.1f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.secondary,
                        activeTrackColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onDelete,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Game")
                    }

                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Batal")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val w = widthText.toIntOrNull() ?: 240
                                val h = heightText.toIntOrNull() ?: 320
                                onSaveSettings(settings.copy(
                                    targetWidth = w,
                                    targetHeight = h,
                                    scaleMode = scaleMode,
                                    smoothScaling = smoothScaling,
                                    keypadOpacity = opacity
                                ))
                            }
                        ) {
                            Text("Simpan")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onLaunch,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Mainkan", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlobalSettingsTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Pengaturan Emulator",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Performance & Audio", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Aktifkan Audio (MIDI/MP3)", modifier = Modifier.weight(1f))
                    var audioEnabled by remember { mutableStateOf(true) }
                    Switch(checked = audioEnabled, onCheckedChange = { audioEnabled = it })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Getaran Virtual Keypad", modifier = Modifier.weight(1f))
                    var vibeEnabled by remember { mutableStateOf(true) }
                    Switch(checked = vibeEnabled, onCheckedChange = { vibeEnabled = it })
                }
            }
        }
    }
}

@Composable
fun AboutTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SportsEsports,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "J2ME Native Emulator",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        Text(
            text = "Versi 1.0.0",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Emulator game Java berkinerja tinggi. Dengan melakukan kompilasi bytecode (.class) JAR ke Dalvik Executable (.dex) secara langsung di perangkat Anda, game dapat dijalankan langsung di atas Android Runtime (ART) dengan performa native tanpa lag.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
                lineHeight = 20.sp
            )
        }
    }
}

private fun launchGame(context: Context, game: GameModel) {
    val settings = GameSettingsStore(context).loadOrDefault(game)
    val intent = Intent(context, EmulatorActivity::class.java).apply {
        putExtra("GAME_ID", game.id)
        putExtra("GAME_NAME", game.name)
        putExtra("JAR_PATH", game.jarPath)
        putExtra("DEX_PATH", game.dexPath)
        putExtra("MAIN_CLASS", game.mainClass)
        putExtra("TARGET_WIDTH", settings.targetWidth)
        putExtra("TARGET_HEIGHT", settings.targetHeight)
        putExtra("SCALE_MODE", settings.scaleMode)
        putExtra("SMOOTH_SCALING", settings.smoothScaling)
        putExtra("OPACITY", settings.keypadOpacity)
    }
    context.startActivity(intent)
}

// Thread-safe copy & translate task
private suspend fun importJar(
    context: Context,
    uri: Uri,
    db: GameDatabase,
    onProgress: (String) -> Unit,
    onComplete: () -> Unit,
    onError: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    try {
        val gameId = UUID.randomUUID().toString()
        val gameDir = File(context.filesDir, "games/$gameId").apply { mkdirs() }
        
        // Copy JAR to internal files
        val jarFile = File(gameDir, "game.jar")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(jarFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Gagal membuka file stream")

        // Parse Manifest
        onProgress("Menganalisis file MANIFEST...")
        var gameName = ""
        var gameVendor = "Unknown"
        var gameVersion = "1.0.0"
        var mainClass = ""
        var iconName: String? = null

        ZipFile(jarFile).use { zip ->
            val manifestEntry = zip.getEntry("META-INF/MANIFEST.MF")
                ?: throw Exception("File ini bukan game J2ME (MANIFEST.MF tidak ditemukan)")
            
            manifestEntry.let { entry ->
                zip.getInputStream(entry).bufferedReader().useLines { lines ->
                    var currentKey = ""
                    var currentValue = ""
                    for (line in lines) {
                        if (line.startsWith(" ")) {
                            currentValue += line.substring(1)
                        } else {
                            val colonIndex = line.indexOf(":")
                            if (colonIndex > 0) {
                                if (currentKey.isNotEmpty()) {
                                    when (currentKey) {
                                        "MIDlet-Name" -> gameName = currentValue
                                        "MIDlet-Vendor" -> gameVendor = currentValue
                                        "MIDlet-Version" -> gameVersion = currentValue
                                        "MIDlet-1" -> {
                                            val parts = currentValue.split(",")
                                            if (parts.size >= 3) {
                                                iconName = parts[1].trim()
                                                mainClass = parts[2].trim()
                                            }
                                        }
                                    }
                                }
                                currentKey = line.substring(0, colonIndex).trim()
                                currentValue = line.substring(colonIndex + 1).trim()
                            }
                        }
                    }
                    // Capture last entry
                    if (currentKey.isNotEmpty()) {
                        when (currentKey) {
                            "MIDlet-Name" -> gameName = currentValue
                            "MIDlet-Vendor" -> gameVendor = currentValue
                            "MIDlet-Version" -> gameVersion = currentValue
                            "MIDlet-1" -> {
                                val parts = currentValue.split(",")
                                if (parts.size >= 3) {
                                    iconName = parts[1].trim()
                                    mainClass = parts[2].trim()
                                }
                            }
                        }
                    }
                }
            }

            // Extract Icon if available
            iconName?.let { iconPath ->
                val cleanIconPath = iconPath.trimStart('/')
                val iconEntry = zip.getEntry(cleanIconPath)
                if (iconEntry != null) {
                    val iconFile = File(gameDir, "icon.png")
                    zip.getInputStream(iconEntry).use { input ->
                        FileOutputStream(iconFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        if (gameName.isEmpty()) {
            // Fallback to filename
            gameName = File(uri.path ?: "Game").nameWithoutExtension
        }
        if (mainClass.isEmpty()) {
            throw Exception("MIDlet main class tidak ditemukan di MANIFEST.MF")
        }

        // Translate J2ME JAR bytecode to Android DEX bytecode
        onProgress("Menerjemahkan bytecode J2ME ke Android DEX (ini butuh beberapa detik)...")
        val dexFile = File(gameDir, "classes.dex")
        val translationSuccess = DexTranslator.translateJarToDex(jarFile, dexFile)
        
        if (!translationSuccess) {
            throw Exception("Gagal mengompilasi bytecode ke DEX format")
        }

        // Make dex file read-only for Android 14+ security compliance
        dexFile.setReadOnly()
        // Make JAR read-only so DexClassLoader injection succeeds on Android 14+
        jarFile.setReadOnly()

        // Save metadata
        val iconFile = File(gameDir, "icon.png")
        val finalIconPath = if (iconFile.exists()) iconFile.absolutePath else null
        
        val newGame = GameModel(
            id = gameId,
            name = gameName,
            vendor = gameVendor,
            version = gameVersion,
            mainClass = mainClass,
            jarPath = jarFile.absolutePath,
            dexPath = dexFile.absolutePath,
            iconPath = finalIconPath,
            sizeBytes = jarFile.length()
        )

        db.addGame(newGame)
        
        withContext(Dispatchers.Main) {
            onComplete()
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        withContext(Dispatchers.Main) {
            onError(e.message ?: "Unknown error")
        }
        false
    }
}
