package com.emulator.j2me

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.emulator.j2me.core.CrashLogger
import com.emulator.j2me.core.EmulatorEngine
import com.emulator.j2me.data.ButtonLayout
import com.emulator.j2me.data.GameModel
import com.emulator.j2me.data.GameSettings
import com.emulator.j2me.data.GameSettingsStore
import com.emulator.j2me.data.Thumbnails
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import javax.microedition.lcdui.Canvas as J2meCanvas
import javax.microedition.lcdui.Displayable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.content.res.Configuration
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EmulatorActivity : ComponentActivity() {

    private var targetWidth = 240
    private var targetHeight = 320
    private var scaleMode = "FIT"
    private var smoothScaling = false
    private var opacity = 0.6f
    // Incremented each time the back gesture fires; observed by EmulatorScreen to open the menu
    private var externalMenuTrigger by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Keep screen on during gameplay
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val gameId = intent.getStringExtra("GAME_ID") ?: ""
        val gameName = intent.getStringExtra("GAME_NAME") ?: "Java Game"
        val jarPath = intent.getStringExtra("JAR_PATH") ?: ""
        val dexPath = intent.getStringExtra("DEX_PATH") ?: ""
        val mainClass = intent.getStringExtra("MAIN_CLASS") ?: ""
        
        targetWidth = intent.getIntExtra("TARGET_WIDTH", 240)
        targetHeight = intent.getIntExtra("TARGET_HEIGHT", 320)
        scaleMode = intent.getStringExtra("SCALE_MODE") ?: "FIT"
        smoothScaling = intent.getBooleanExtra("SMOOTH_SCALING", false)
        opacity = intent.getFloatExtra("OPACITY", 0.6f)

        val game = GameModel(
            id = gameId,
            name = gameName,
            vendor = "",
            version = "",
            mainClass = mainClass,
            jarPath = jarPath,
            dexPath = dexPath,
            iconPath = null,
            sizeBytes = 0,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            keypadOpacity = opacity,
            scaleMode = scaleMode,
            smoothScaling = smoothScaling
        )

        CrashLogger.install(this, game.name)

        // Intercept back gesture/button — open quick menu instead of immediately exiting.
        // This prevents the left-edge swipe (analog stick area) from accidentally closing the game.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { externalMenuTrigger++ }
        })

        setContent {
            J2meEmulatorTheme {
                EmulatorScreen(game = game, onBack = { finish() }, externalMenuTrigger = externalMenuTrigger)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop current game engine when closing activity
        EmulatorEngine.stopGame {}
    }
}

// Load typography fonts
val RajdhaniFont = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.rajdhani)
)
val ShareTechMonoFont = FontFamily(
    androidx.compose.ui.text.font.Font(R.font.share_tech_mono)
)

@Composable
fun EmulatorScreen(game: GameModel, onBack: () -> Unit, externalMenuTrigger: Int = 0) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var activeDisplayable by remember { mutableStateOf<Displayable?>(null) }
    var isMenuOpen by remember { mutableStateOf(false) }
    var gameStarted by remember { mutableStateOf(false) }

    // Live display settings, editable from the in-game Quick Menu.
    var scaleMode by remember { mutableStateOf(game.scaleMode) }
    var smoothScaling by remember { mutableStateOf(game.smoothScaling) }
    // Render FPS cap (0 == unlimited), editable from the in-game Quick Menu.
    var fpsLimit by remember { mutableIntStateOf(GameSettingsStore(context).load(game.id)?.fpsLimit ?: 0) }
    
    // Tracks active Nokia key value from analog stick (1 to 9, default 5)
    var activeAnalogKey by remember { mutableIntStateOf(5) }
    // Real FPS reported by the active J2meGameView
    var gameFps by remember { mutableIntStateOf(0) }
    // Ref to the active view so the LaunchedEffect can poll currentFps
    var gameViewRef: J2meGameView? by remember { mutableStateOf(null) }
    // Scope for persisting settings off the main thread.
    val settingsScope = rememberCoroutineScope()

    // ── Button layout customization ──────────────────────────────────────────
    val settingsStore = remember { GameSettingsStore(context) }
    val initialLayout = remember { settingsStore.load(game.id)?.buttonLayout ?: ButtonLayout() }
    var layoutEditMode by remember { mutableStateOf(false) }
    var analogOffset by remember { mutableStateOf(Offset(initialLayout.analogDx, initialLayout.analogDy)) }
    var dpadOffset by remember { mutableStateOf(Offset(initialLayout.dpadDx, initialLayout.dpadDy)) }
    var softkeysOffset by remember { mutableStateOf(Offset(initialLayout.softkeysDx, initialLayout.softkeysDy)) }

    fun persistButtonLayout() {
        val snapshot = ButtonLayout(
            analogDx = analogOffset.x, analogDy = analogOffset.y,
            dpadDx = dpadOffset.x, dpadDy = dpadOffset.y,
            softkeysDx = softkeysOffset.x, softkeysDy = softkeysOffset.y
        )
        settingsScope.launch(Dispatchers.IO) {
            val current = settingsStore.load(game.id) ?: GameSettings.fromGameModel(game)
            current.buttonLayout = snapshot
            settingsStore.save(game.id, current)
        }
    }

    fun persistFpsLimit(limit: Int) {
        settingsScope.launch(Dispatchers.IO) {
            val current = settingsStore.load(game.id) ?: GameSettings.fromGameModel(game)
            current.fpsLimit = limit
            settingsStore.save(game.id, current)
        }
    }

    // Poll the view's currentFps twice per second
    LaunchedEffect(gameViewRef) {
        while (gameViewRef != null) {
            kotlinx.coroutines.delay(500L)
            val v = gameViewRef ?: break
            val fps = v.currentFps
            if (gameFps != fps) gameFps = fps
        }
    }

    // Open quick menu when the back gesture/button fires from the Activity
    LaunchedEffect(externalMenuTrigger) {
        if (externalMenuTrigger > 0) isMenuOpen = true
    }

    // Start Emulator Engine when this screen loads
    LaunchedEffect(Unit) {
        EmulatorEngine.startGame(
            context = context,
            game = game,
            onDisplayChanged = { next ->
                activeDisplayable = next
            },
            onFinished = {
                onBack()
            }
        )
        gameStarted = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14)) // Premium Dark Gaming Theme background
    ) {
        if (isLandscape && gameStarted && activeDisplayable is J2meCanvas) {
            LandscapeEmulatorContent(
                canvas = activeDisplayable as J2meCanvas,
                game = game,
                scaleMode = scaleMode,
                smoothScaling = smoothScaling,
                fpsLimit = fpsLimit,
                activeAnalogKey = activeAnalogKey,
                onKeyChanged = { activeAnalogKey = it },
                gameFps = gameFps,
                onFpsUpdate = { gameViewRef = it },
                onMenuOpen = { isMenuOpen = true },
                onBack = onBack
            )
        } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Topbar — nama ROM, status dot, tombol settings/menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status Dot (Neon Green)
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Color(0xFF00E5A0), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = game.name,
                            color = Color.White,
                            fontFamily = RajdhaniFont,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1
                        )
                        Text(
                            text = "RUNTIME v3.4.1",
                            color = Color(0xFF00E5A0).copy(alpha = 0.8f),
                            fontFamily = ShareTechMonoFont,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                
                // Topbar Settings/Menu Quick buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Settings icon button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161A24))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable { isMenuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                    // Menu icon button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF161A24))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable { isMenuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Layar game — aspect ratio 4:3, efek scanline, FPS badge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(2.dp, Color(0xFF1E222D), RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (gameStarted && activeDisplayable is J2meCanvas) {
                    AndroidView(
                        factory = { ctx ->
                            J2meGameView(
                                context = ctx,
                                j2meCanvas = activeDisplayable as J2meCanvas,
                                targetW = game.targetWidth,
                                targetH = game.targetHeight,
                                initialScaleMode = game.scaleMode,
                                initialSmoothScaling = game.smoothScaling,
                                gameId = game.id,
                                initialFpsLimit = fpsLimit
                            ).also { gameViewRef = it }
                        },
                        update = {
                            it.updateDisplaySettings(scaleMode, smoothScaling)
                            it.updateFpsLimit(fpsLimit)
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // FPS Badge (Neon Green)
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF00E5A0).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "$gameFps\nFPS",
                            color = Color(0xFF00E5A0),
                            fontFamily = ShareTechMonoFont,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 10.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF00E5A0))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Memuat canvas game...",
                            color = Color.Gray,
                            fontFamily = RajdhaniFont,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Softkeys row: LSK | * | 0 | # | RSK
            if (activeDisplayable is J2meCanvas) {
                val canvas = activeDisplayable as J2meCanvas
                DraggableControl(
                    editMode = layoutEditMode,
                    offset = softkeysOffset,
                    onDrag = { softkeysOffset += it }
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftKeyButton(
                        label = "LSK",
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_SOFTKEY_LEFT) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_SOFTKEY_LEFT) },
                        modifier = Modifier.weight(1.4f)
                    )
                    SoftKeyButton(
                        label = "*",
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_STAR) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_STAR) },
                        modifier = Modifier.weight(1f)
                    )
                    SoftKeyButton(
                        label = "0",
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_NUM0) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_NUM0) },
                        modifier = Modifier.weight(1f)
                    )
                    SoftKeyButton(
                        label = "#",
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_POUND) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_POUND) },
                        modifier = Modifier.weight(1f)
                    )
                    SoftKeyButton(
                        label = "RSK",
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_SOFTKEY_RIGHT) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_SOFTKEY_RIGHT) },
                        modifier = Modifier.weight(1.4f)
                    )
                }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 4. Baris tengah: analog stick kiri + D-pad kanan + tombol A/B
            if (activeDisplayable is J2meCanvas) {
                val canvas = activeDisplayable as J2meCanvas
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Analog Stick
                    DraggableControl(
                        editMode = layoutEditMode,
                        offset = analogOffset,
                        onDrag = { analogOffset += it }
                    ) {
                        AnalogStick(
                            canvas = canvas,
                            activeKey = activeAnalogKey,
                            onKeyChanged = { activeAnalogKey = it }
                        )
                    }
                    
                    // Middle: Real-time keypad feedback badge & 3x3 grid
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "ANALOG → KEY",
                            color = Color.White.copy(alpha = 0.4f),
                            fontFamily = RajdhaniFont,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        // 5 KEY active badge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .width(52.dp)
                                .height(52.dp)
                                .background(Color(0xFF161A24), RoundedCornerShape(10.dp))
                                .border(1.5.dp, Color(0xFF00E5A0).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        ) {
                            Text(
                                text = activeAnalogKey.toString(),
                                fontFamily = ShareTechMonoFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = Color(0xFF00E5A0)
                            )
                            Text(
                                text = "KEY",
                                fontFamily = RajdhaniFont,
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        // 3x3 Mini Grid
                        MiniGrid3x3(activeKey = activeAnalogKey)
                    }

                    // Right: D-pad + A/B Buttons
                    DraggableControl(
                        editMode = layoutEditMode,
                        offset = dpadOffset,
                        onDrag = { dpadOffset += it }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            GameDpad(canvas = canvas)
                            Spacer(modifier = Modifier.height(12.dp))
                            // Action Buttons A and B
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularActionButton(
                                    label = "A",
                                    color = Color(0xFFA855F7), // Purple color
                                    onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_SELECT_FIRE) },
                                    onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_SELECT_FIRE) }
                                )
                                CircularActionButton(
                                    label = "B",
                                    color = Color(0xFFF59E0B), // Yellow/Orange color
                                    onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_NUM0) },
                                    onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_NUM0) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 5. Bottom bar — Load ROM, Save, Stop
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomButton(
                    label = "Load ROM",
                    icon = Icons.Filled.FolderOpen,
                    onClick = { onBack() },
                    modifier = Modifier.weight(1f)
                )
                BottomButton(
                    label = "Save",
                    icon = Icons.Filled.Save,
                    onClick = {
                        Toast.makeText(context, "Game State Saved!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                )
                BottomButton(
                    label = "Stop",
                    icon = Icons.Filled.Cancel,
                    onClick = {
                        EmulatorEngine.stopGame {
                            onBack()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        } // end portrait else

        // Layout-edit banner: lets the player reset/finish repositioning controls.
        if (layoutEditMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xFF0D0F18).copy(alpha = 0.92f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Edit Tata Letak — geser kontrol",
                    color = Color(0xFF00E5A0),
                    fontFamily = RajdhaniFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            analogOffset = Offset.Zero
                            dpadOffset = Offset.Zero
                            softkeysOffset = Offset.Zero
                            persistButtonLayout()
                        }
                    ) { Text("Reset", fontSize = 12.sp) }
                    Button(
                        onClick = {
                            layoutEditMode = false
                            persistButtonLayout()
                        }
                    ) { Text("Selesai", fontSize = 12.sp) }
                }
            }
        }

        // Translucent Quick Menu Overlay
        if (isMenuOpen) {
            QuickMenuOverlay(
                scaleMode = scaleMode,
                smoothScaling = smoothScaling,
                fpsLimit = fpsLimit,
                onFpsLimitChange = { limit ->
                    fpsLimit = limit
                    persistFpsLimit(limit)
                },
                onEditLayout = {
                    isMenuOpen = false
                    layoutEditMode = true
                },
                onScaleModeChange = { mode ->
                    scaleMode = mode
                    game.scaleMode = mode
                    settingsScope.launch(Dispatchers.IO) {
                        persistDisplaySettings(context, game, mode, smoothScaling)
                    }
                },
                onSmoothScalingChange = { smooth ->
                    smoothScaling = smooth
                    game.smoothScaling = smooth
                    settingsScope.launch(Dispatchers.IO) {
                        persistDisplaySettings(context, game, scaleMode, smooth)
                    }
                },
                onDismiss = { isMenuOpen = false },
                onReset = {
                    isMenuOpen = false
                    EmulatorEngine.stopGame {
                        EmulatorEngine.startGame(
                            context = context,
                            game = game,
                            onDisplayChanged = { activeDisplayable = it },
                            onFinished = onBack
                        )
                    }
                },
                onExit = {
                    isMenuOpen = false
                    EmulatorEngine.stopGame {
                        onBack()
                    }
                }
            )
        }
    }
}

// Custom Outlined/Colored Circular Action Button (Purple/Orange)
@Composable
fun CircularActionButton(
    label: String,
    color: Color,
    onClickPress: () -> Unit,
    onClickRelease: () -> Unit
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer {
                scaleX = if (isPressed) 0.9f else 1.0f
                scaleY = if (isPressed) 0.9f else 1.0f
            }
            .background(
                if (isPressed) color.copy(alpha = 0.8f) else Color.Transparent,
                CircleShape
            )
            .border(2.dp, color, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        context.currentView()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClickPress()
                        tryAwaitRelease()
                        isPressed = false
                        onClickRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color.Black else color,
            fontFamily = ShareTechMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

/**
 * Wraps an on-screen control cluster so it can be repositioned by the player.
 * The cluster is visually translated by [offset]. While [editMode] is on, a
 * dashed highlight + drag handle overlays the cluster and consumes drags,
 * reporting deltas via [onDrag]; outside edit mode the wrapper is transparent
 * and the underlying control behaves normally.
 */
@Composable
fun DraggableControl(
    editMode: Boolean,
    offset: Offset,
    onDrag: (Offset) -> Unit,
    content: @Composable () -> Unit
) {
    val latestOnDrag by rememberUpdatedState(onDrag)
    Box(
        modifier = Modifier.offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
    ) {
        content()
        if (editMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF00E5A0).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .border(2.dp, Color(0xFF00E5A0).copy(alpha = 0.8f), RoundedCornerShape(10.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            latestOnDrag(dragAmount)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.OpenWith,
                    contentDescription = "Geser kontrol",
                    tint = Color(0xFF00E5A0),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// Custom D-Pad Composable with center OK and directions
@Composable
fun GameDpad(
    canvas: J2meCanvas,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(115.dp)
            .background(Color(0xFF161A24), CircleShape)
            .border(1.5.dp, Color.White.copy(alpha = 0.08f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        DpadDirectionButton(
            icon = Icons.Filled.KeyboardArrowUp,
            onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_UP_ARROW) },
            onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_UP_ARROW) },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
        )
        DpadDirectionButton(
            icon = Icons.Filled.KeyboardArrowDown,
            onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_DOWN_ARROW) },
            onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_DOWN_ARROW) },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
        )
        DpadDirectionButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_LEFT_ARROW) },
            onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_LEFT_ARROW) },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp)
        )
        DpadDirectionButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_RIGHT_ARROW) },
            onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_RIGHT_ARROW) },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
        )
        
        // Center Fire/OK button (Neon Green)
        var isOkPressed by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .size(38.dp)
                .graphicsLayer {
                    scaleX = if (isOkPressed) 0.9f else 1.0f
                    scaleY = if (isOkPressed) 0.9f else 1.0f
                }
                .background(
                    if (isOkPressed) Color(0xFF00E5A0).copy(alpha = 0.8f) else Color(0xFF00E5A0),
                    CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isOkPressed = true
                            context.currentView()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            canvas.postKeyPressed(J2meCanvas.KEY_SELECT_FIRE)
                            tryAwaitRelease()
                            isOkPressed = false
                            canvas.postKeyReleased(J2meCanvas.KEY_SELECT_FIRE)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "OK",
                color = Color.Black,
                fontFamily = ShareTechMonoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun DpadDirectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClickPress: () -> Unit,
    onClickRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .size(30.dp)
            .graphicsLayer {
                scaleX = if (isPressed) 0.85f else 1.0f
                scaleY = if (isPressed) 0.85f else 1.0f
            }
            .clip(CircleShape)
            .background(if (isPressed) Color.White.copy(alpha = 0.15f) else Color.Transparent)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        context.currentView()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClickPress()
                        tryAwaitRelease()
                        isPressed = false
                        onClickRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isPressed) Color(0xFF00E5A0) else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(22.dp)
        )
    }
}

// Draggable 8-directional Analog Stick with Spring Animation
@Composable
fun AnalogStick(
    canvas: J2meCanvas,
    activeKey: Int,
    onKeyChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val dragRadius = 50.dp
    val dragRadiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { dragRadius.toPx() }
    
    val animOffsetX = remember { Animatable(0f) }
    val animOffsetY = remember { Animatable(0f) }
    
    val context = LocalContext.current
    var lastSentKey by remember { mutableIntStateOf(-1) }
    
    fun getCanvasKey(nokiaKey: Int): Int {
        return when (nokiaKey) {
            1 -> J2meCanvas.KEY_NUM1
            2 -> J2meCanvas.KEY_NUM2
            3 -> J2meCanvas.KEY_NUM3
            4 -> J2meCanvas.KEY_NUM4
            5 -> J2meCanvas.KEY_NUM5
            6 -> J2meCanvas.KEY_NUM6
            7 -> J2meCanvas.KEY_NUM7
            8 -> J2meCanvas.KEY_NUM8
            9 -> J2meCanvas.KEY_NUM9
            else -> -1
        }
    }
    
    fun getAnalogKey(dx: Float, dy: Float, radius: Float): Int {
        val dist = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (dist < radius * 0.25f) {
            return 5
        }
        var angleDeg = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
        if (angleDeg < 0) angleDeg += 360.0
        
        // Normalize to [0, 8) sectors by adding 22.5 degrees offset
        val sector = (((angleDeg + 22.5) % 360.0) / 45.0).toInt()
        return when (sector) {
            0 -> 6 // Right
            1 -> 9 // Down-Right  (screen Y+ = down = numpad bottom-right)
            2 -> 8 // Down        (screen Y+ = down = numpad 8)
            3 -> 7 // Down-Left   (screen Y+ = down = numpad bottom-left)
            4 -> 4 // Left
            5 -> 1 // Up-Left     (screen Y- = up   = numpad top-left)
            6 -> 2 // Up          (screen Y- = up   = numpad 2)
            7 -> 3 // Up-Right    (screen Y- = up   = numpad top-right)
            else -> 5
        }
    }

    fun updateKey(dx: Float, dy: Float) {
        val nokiaKey = getAnalogKey(dx, dy, dragRadiusPx)
        onKeyChanged(nokiaKey)  // visual feedback (mini grid shows 5 at center)

        // Treat center (5) as "no direction" — never fire KEY_NUM5 from the analog.
        // KEY_NUM5 maps to FIRE/SELECT in J2ME which opens context menus unintentionally.
        val effectiveKey = if (nokiaKey == 5) -1 else nokiaKey

        if (effectiveKey != lastSentKey) {
            // Release last directional key
            if (lastSentKey != -1) {
                val canvasKey = getCanvasKey(lastSentKey)
                if (canvasKey != -1) canvas.postKeyReleased(canvasKey)
            }
            // Press new directional key (center is a no-op)
            if (effectiveKey != -1) {
                val canvasKey = getCanvasKey(effectiveKey)
                if (canvasKey != -1) {
                    context.currentView()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    canvas.postKeyPressed(canvasKey)
                }
            }
            lastSentKey = effectiveKey
        }
    }
    
    Box(
        modifier = modifier
            .size(130.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF1E222F), Color(0xFF0F111A))
                ),
                shape = CircleShape
            )
            .border(2.dp, Color(0xFF00E5A0).copy(alpha = 0.3f), CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        // Drag started
                    },
                    onDragEnd = {
                        // Return to center with animation
                        coroutineScope.launch {
                            if (lastSentKey != -1) {
                                val canvasKey = getCanvasKey(lastSentKey)
                                if (canvasKey != -1) {
                                    canvas.postKeyReleased(canvasKey)
                                }
                                lastSentKey = -1
                                onKeyChanged(5)
                            }
                            launch { animOffsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                            launch { animOffsetY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            if (lastSentKey != -1) {
                                val canvasKey = getCanvasKey(lastSentKey)
                                if (canvasKey != -1) {
                                    canvas.postKeyReleased(canvasKey)
                                }
                                lastSentKey = -1
                                onKeyChanged(5)
                            }
                            launch { animOffsetX.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                            launch { animOffsetY.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val newX = animOffsetX.value + dragAmount.x
                        val newY = animOffsetY.value + dragAmount.y
                        
                        val dist = Math.hypot(newX.toDouble(), newY.toDouble()).toFloat()
                        if (dist <= dragRadiusPx) {
                            coroutineScope.launch {
                                animOffsetX.snapTo(newX)
                                animOffsetY.snapTo(newY)
                            }
                            updateKey(newX, newY)
                        } else {
                            val ratio = dragRadiusPx / dist
                            val constrainedX = newX * ratio
                            val constrainedY = newY * ratio
                            coroutineScope.launch {
                                animOffsetX.snapTo(constrainedX)
                                animOffsetY.snapTo(constrainedY)
                            }
                            updateKey(constrainedX, constrainedY)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size / 2f
            drawCircle(
                color = Color(0xFF00E5A0).copy(alpha = 0.1f),
                radius = dragRadiusPx,
                style = Stroke(width = 1.dp.toPx())
            )
            
            // Subtle 8-directional ticks
            val directions = listOf(0, 45, 90, 135, 180, 225, 270, 315)
            for (angle in directions) {
                val rad = Math.toRadians(angle.toDouble())
                val startX = center.width + (dragRadiusPx - 8.dp.toPx()) * Math.cos(rad).toFloat()
                val startY = center.height + (dragRadiusPx - 8.dp.toPx()) * Math.sin(rad).toFloat()
                val endX = center.width + dragRadiusPx * Math.cos(rad).toFloat()
                val endY = center.height + dragRadiusPx * Math.sin(rad).toFloat()
                drawLine(
                    color = Color(0xFF00E5A0).copy(alpha = 0.4f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        
        // Draggable Thumb knob
        Box(
            modifier = Modifier
                .offset(
                    x = with(androidx.compose.ui.platform.LocalDensity.current) { animOffsetX.value.toDp() },
                    y = with(androidx.compose.ui.platform.LocalDensity.current) { animOffsetY.value.toDp() }
                )
                .size(46.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5A0), Color(0xFF009e6e))
                    ),
                    shape = CircleShape
                )
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )
        }
    }
}

// 3x3 Mini Grid highlighting active direction
@Composable
fun MiniGrid3x3(activeKey: Int) {
    val gridLayout = listOf(
        listOf(1, 2, 3),
        listOf(4, 5, 6),
        listOf(7, 8, 9)
    )
    
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in gridLayout) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (keyVal in row) {
                    val isActive = keyVal == activeKey
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                color = if (isActive) Color(0xFF00E5A0) else Color(0xFF1E222D),
                                shape = RoundedCornerShape(2.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f),
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
        }
    }
}

// Wide SoftKey Button (LSK/RSK)
@Composable
fun SoftKeyButton(
    label: String,
    onClickPress: () -> Unit,
    onClickRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer {
                scaleX = if (isPressed) 0.95f else 1.0f
                scaleY = if (isPressed) 0.95f else 1.0f
            }
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPressed) Color(0xFF1F2430) else Color(0xFF161A24))
            .border(
                width = 1.dp,
                color = if (isPressed) Color(0xFF00E5A0) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        context.currentView()?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClickPress()
                        tryAwaitRelease()
                        isPressed = false
                        onClickRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isPressed) Color(0xFF00E5A0) else Color.White,
            fontFamily = ShareTechMonoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}

// Action Button in Bottom Bar (Load ROM, Save, Stop)
@Composable
fun BottomButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(46.dp)
            .graphicsLayer {
                scaleX = if (isPressed) 0.95f else 1.0f
                scaleY = if (isPressed) 0.95f else 1.0f
            }
            .clip(RoundedCornerShape(10.dp))
            .background(if (isPressed) Color(0xFF1F2430) else Color(0xFF161A24))
            .border(
                width = 1.dp,
                color = if (isPressed) Color(0xFF00E5A0) else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp)
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isPressed) Color(0xFF00E5A0) else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isPressed) Color(0xFF00E5A0) else Color.White,
                fontFamily = RajdhaniFont,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

// Custom View wrapping the J2ME double-buffered paint Canvas
class J2meGameView(
    context: Context,
    private val j2meCanvas: J2meCanvas,
    private val targetW: Int,
    private val targetH: Int,
    initialScaleMode: String,
    initialSmoothScaling: Boolean = false,
    private val gameId: String = "",
    initialFpsLimit: Int = 0
) : View(context) {

    @Volatile private var scaleMode: String = initialScaleMode

    // Max frames per second the render thread will produce; 0 == unlimited.
    @Volatile private var fpsLimit: Int = initialFpsLimit

    /** Update the render FPS cap live (e.g. from the in-game Quick Menu). */
    fun updateFpsLimit(limit: Int) {
        fpsLimit = limit
    }

    private val offscreenBitmap: Bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
    private val offscreenCanvas: android.graphics.Canvas = android.graphics.Canvas(offscreenBitmap)
    private val j2meGraphics: javax.microedition.lcdui.Graphics = javax.microedition.lcdui.Graphics()
    private val destRect = android.graphics.Rect()

    // Paint used to blit the framebuffer onto the view. isFilterBitmap toggles
    // bilinear smoothing (true) vs nearest-neighbor crisp pixels (false).
    private val scalePaint = android.graphics.Paint().apply { isFilterBitmap = initialSmoothScaling }

    /** Apply scale mode / smoothing changes live (e.g. from the in-game Quick Menu). */
    fun updateDisplaySettings(scaleMode: String, smoothScaling: Boolean) {
        this.scaleMode = scaleMode
        scalePaint.isFilterBitmap = smoothScaling
        postInvalidate()
    }

    // Render thread state
    @Volatile private var renderRunning = true
    private var renderThread: Thread? = null

    // Track whether we have a valid frame to show
    @Volatile private var hasValidFrame = false
    @Volatile private var lastPaintSucceeded = false

    // Lock to protect offscreenBitmap access between render thread and UI thread
    private val bitmapLock = Any()

    // Latch that gates the render thread until the game calls repaint() for the first time
    private val firstRepaintLatch = CountDownLatch(1)

    // Real FPS measured by the render thread (updated once per second)
    @Volatile var currentFps: Int = 0

    // Guard so the library thumbnail is only written once per view.
    @Volatile private var thumbnailCaptured = false

    /** Copy the current framebuffer and persist it as this game's library thumbnail. */
    private fun captureThumbnail() {
        if (gameId.isEmpty()) return
        val snapshot = synchronized(bitmapLock) {
            offscreenBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        try {
            java.io.FileOutputStream(Thumbnails.file(context, gameId)).use { out ->
                snapshot.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            android.util.Log.w("J2ME-Render", "thumbnail capture failed: ${e.message}")
        } finally {
            snapshot.recycle()
        }
    }

    init {
        j2meCanvas.setRepaintListener(object : J2meCanvas.RepaintListener {
            override fun onRequestRepaint() {
                // Signal that the game is ready to be painted (first repaint)
                firstRepaintLatch.countDown()
                // Game asked for repaint — wake the render thread only if last frame succeeded
                if (lastPaintSucceeded) {
                    renderThread?.interrupt()
                }
                postInvalidate()
            }
            override fun onFullScreenModeChanged(fullScreen: Boolean) {
                postInvalidate()
            }
        })
        j2meGraphics.setCanvas(offscreenCanvas)

        // Start dedicated render thread
        renderThread = Thread {
            // Wait for the game to signal it is ready (via first repaint() call).
            // This prevents paint() from being called before internal arrays are initialized.
            try {
                firstRepaintLatch.await(15, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {}

            // Give the game a 500ms grace period after its first repaint() to
            // finish allocating internal buffers before we start calling paint().
            try { Thread.sleep(500) } catch (_: InterruptedException) {}

            var backoffMs = 100L
            var consecutiveFailures = 0
            var frameCount = 0
            var totalFrames = 0
            var lastFpsMs = System.currentTimeMillis()
            var lastFrameStartMs = 0L
            while (renderRunning) {
                // FPS limiter: hold back the next frame until the minimum
                // inter-frame interval has elapsed (0 == unlimited). Interrupts
                // (used to wake on repaint) are swallowed so the cap still holds.
                val limit = fpsLimit
                if (limit > 0 && lastFrameStartMs != 0L) {
                    val minIntervalMs = 1000L / limit
                    var remaining = minIntervalMs - (System.currentTimeMillis() - lastFrameStartMs)
                    while (renderRunning && remaining > 0) {
                        try { Thread.sleep(remaining) } catch (_: InterruptedException) {}
                        remaining = minIntervalMs - (System.currentTimeMillis() - lastFrameStartMs)
                    }
                }
                lastFrameStartMs = System.currentTimeMillis()
                try {
                    synchronized(bitmapLock) {
                        j2meCanvas.paint(j2meGraphics)
                    }
                    hasValidFrame = true
                    lastPaintSucceeded = true
                    postInvalidate()          // ask UI thread to blit the bitmap
                    // Interrupt-driven: sleep until game calls repaint() (max 100ms keepalive)
                    backoffMs = 100L
                    consecutiveFailures = 0
                    // FPS counting
                    frameCount++
                    totalFrames++
                    // Capture the library thumbnail once the game has drawn a few
                    // frames (skips the initial blank/splash frame).
                    if (!thumbnailCaptured && totalFrames >= 8) {
                        thumbnailCaptured = true
                        captureThumbnail()
                    }
                    val nowMs = System.currentTimeMillis()
                    if (nowMs - lastFpsMs >= 1000L) {
                        currentFps = frameCount
                        frameCount = 0
                        lastFpsMs = nowMs
                    }
                } catch (e: Throwable) {
                    consecutiveFailures++
                    lastPaintSucceeded = false
                    // Exponential backoff: 100ms → 200 → 400 → 800 → 2000ms max
                    backoffMs = minOf(100L * (1L shl minOf(consecutiveFailures - 1, 4)), 2000L)
                    android.util.Log.w("J2ME-Render",
                        "paint() failed #$consecutiveFailures (retry in ${backoffMs}ms): ${e.javaClass.simpleName}: ${e.message}", e)
                    // Log fatal errors (Errors, not Exceptions) to crash file
                    if (e is Error) {
                        try {
                            CrashLogger.save(context, j2meCanvas.javaClass.simpleName, Thread.currentThread(), e)
                        } catch (_: Exception) {}
                    }
                }
                try {
                    if (backoffMs > 0) Thread.sleep(backoffMs)
                } catch (_: InterruptedException) {
                    backoffMs = 0L
                }
            }
        }.also {
            it.name = "J2ME-RenderThread"
            it.isDaemon = true
            it.start()
        }
    }

    override fun onDetachedFromWindow() {
        renderRunning = false
        renderThread?.interrupt()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0 || viewH <= 0) return

        if (!hasValidFrame) {
            canvas.drawColor(android.graphics.Color.BLACK)
            return
        }

        synchronized(bitmapLock) {
            when (scaleMode) {
                "STRETCH" -> {
                    destRect.set(0, 0, width, height)
                    canvas.drawBitmap(offscreenBitmap, null, destRect, scalePaint)
                }
                "ORIGINAL" -> {
                    val left = ((width - targetW) / 2).toFloat()
                    val top = ((height - targetH) / 2).toFloat()
                    canvas.drawBitmap(offscreenBitmap, left, top, scalePaint)
                }
                "FIT" -> {
                    val scale = Math.min(viewW / targetW, viewH / targetH)
                    val destW = (targetW * scale).toInt()
                    val destH = (targetH * scale).toInt()
                    val left = (width - destW) / 2
                    val top = (height - destH) / 2
                    destRect.set(left, top, left + destW, top + destH)
                    canvas.drawBitmap(offscreenBitmap, null, destRect, scalePaint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        var mappedX = 0f
        var mappedY = 0f

        when (scaleMode) {
            "STRETCH" -> {
                mappedX = event.x / viewW * targetW
                mappedY = event.y / viewH * targetH
            }
            "ORIGINAL" -> {
                mappedX = event.x - (width - targetW) / 2f
                mappedY = event.y - (height - targetH) / 2f
            }
            "FIT" -> {
                val scale = Math.min(viewW / targetW, viewH / targetH)
                val destW = targetW * scale
                val destH = targetH * scale
                val left = (width - destW) / 2f
                val top = (height - destH) / 2f

                mappedX = (event.x - left) / scale
                mappedY = (event.y - top) / scale
            }
        }

        val px = mappedX.toInt()
        val py = mappedY.toInt()

        if (px in 0 until targetW && py in 0 until targetH) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> j2meCanvas.postPointerPressed(px, py)
                MotionEvent.ACTION_UP -> j2meCanvas.postPointerReleased(px, py)
                MotionEvent.ACTION_MOVE -> j2meCanvas.postPointerDragged(px, py)
            }
        }
        return true
    }
}

@Composable
fun LandscapeEmulatorContent(
    canvas: J2meCanvas,
    game: GameModel,
    scaleMode: String,
    smoothScaling: Boolean,
    fpsLimit: Int,
    activeAnalogKey: Int,
    onKeyChanged: (Int) -> Unit,
    gameFps: Int = 0,
    onFpsUpdate: (J2meGameView) -> Unit = {},
    onMenuOpen: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Topbar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(Color(0xFF0D0F18))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(Color(0xFF00E5A0), CircleShape))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = game.name,
                        color = Color.White,
                        fontFamily = RajdhaniFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    Text(
                        text = "RUNTIME v3.4.1",
                        color = Color(0xFF00E5A0).copy(alpha = 0.75f),
                        fontFamily = ShareTechMonoFont,
                        fontSize = 8.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val btnMod = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF161A24))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                Box(btnMod.clickable { /* rotate */ }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ScreenRotation, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(14.dp))
                }
                Box(btnMod.clickable { /* fullscreen */ }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Fullscreen, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(14.dp))
                }
                Box(btnMod.clickable { onMenuOpen() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Settings, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(14.dp))
                }
                Box(btnMod.clickable { onMenuOpen() }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Menu, null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(14.dp))
                }
            }
        }

        // ── Body: 3 columns ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // ── Left column: Analog stick ─────────────────────────────────
            Column(
                modifier = Modifier
                    .width(152.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0D0F18))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "ANALOG → KEY",
                    color = Color.White.copy(alpha = 0.35f),
                    fontFamily = RajdhaniFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                AnalogStick(canvas = canvas, activeKey = activeAnalogKey, onKeyChanged = onKeyChanged)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF161A24), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF00E5A0).copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(activeAnalogKey.toString(), fontFamily = ShareTechMonoFont, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF00E5A0))
                        Text("KEY", fontFamily = RajdhaniFont, fontSize = 7.sp, color = Color.White.copy(alpha = 0.45f))
                    }
                    MiniGrid3x3(activeKey = activeAnalogKey)
                }
            }

            // ── Center column: soft keys + game screen + bottom bar ────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SoftKeyButton("LSK", { canvas.postKeyPressed(J2meCanvas.KEY_SOFTKEY_LEFT) }, { canvas.postKeyReleased(J2meCanvas.KEY_SOFTKEY_LEFT) }, modifier = Modifier.weight(1.3f))
                    SoftKeyButton("*",   { canvas.postKeyPressed(J2meCanvas.KEY_STAR)          }, { canvas.postKeyReleased(J2meCanvas.KEY_STAR)          }, modifier = Modifier.weight(1f))
                    SoftKeyButton("0",   { canvas.postKeyPressed(J2meCanvas.KEY_NUM0)          }, { canvas.postKeyReleased(J2meCanvas.KEY_NUM0)          }, modifier = Modifier.weight(1f))
                    SoftKeyButton("#",   { canvas.postKeyPressed(J2meCanvas.KEY_POUND)         }, { canvas.postKeyReleased(J2meCanvas.KEY_POUND)         }, modifier = Modifier.weight(1f))
                    SoftKeyButton("RSK", { canvas.postKeyPressed(J2meCanvas.KEY_SOFTKEY_RIGHT) }, { canvas.postKeyReleased(J2meCanvas.KEY_SOFTKEY_RIGHT) }, modifier = Modifier.weight(1.3f))
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(Color.Black, RoundedCornerShape(10.dp))
                        .border(2.dp, Color(0xFF1E222D), RoundedCornerShape(10.dp))
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            J2meGameView(
                                context = ctx,
                                j2meCanvas = canvas,
                                targetW = game.targetWidth,
                                targetH = game.targetHeight,
                                initialScaleMode = game.scaleMode,
                                initialSmoothScaling = game.smoothScaling,
                                gameId = game.id,
                                initialFpsLimit = fpsLimit
                            ).also { onFpsUpdate(it) }
                        },
                        update = {
                            it.updateDisplaySettings(scaleMode, smoothScaling)
                            it.updateFpsLimit(fpsLimit)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                            .border(1.dp, Color(0xFF00E5A0).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text("$gameFps\nFPS", color = Color(0xFF00E5A0), fontFamily = ShareTechMonoFont, fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 9.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BottomButton("Load ROM", Icons.Filled.FolderOpen, { onBack() }, modifier = Modifier.weight(1f))
                    BottomButton("Save", Icons.Filled.Save, { Toast.makeText(context, "Game State Saved!", Toast.LENGTH_SHORT).show() }, modifier = Modifier.weight(1f))
                    BottomButton("Stop", Icons.Filled.Cancel, { EmulatorEngine.stopGame { onBack() } }, modifier = Modifier.weight(1f))
                }
            }

            // ── Right column: D-pad + A/B + START/SEL ─────────────────────
            Column(
                modifier = Modifier
                    .width(152.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF0D0F18))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("D-PAD", color = Color.White.copy(alpha = 0.35f), fontFamily = RajdhaniFont, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                GameDpad(canvas = canvas)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularActionButton(
                        label = "A",
                        color = Color(0xFFA855F7),
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_SELECT_FIRE) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_SELECT_FIRE) }
                    )
                    CircularActionButton(
                        label = "B",
                        color = Color(0xFFF59E0B),
                        onClickPress = { canvas.postKeyPressed(J2meCanvas.KEY_NUM0) },
                        onClickRelease = { canvas.postKeyReleased(J2meCanvas.KEY_NUM0) }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SoftKeyButton("START", { canvas.postKeyPressed(J2meCanvas.KEY_SELECT_FIRE) }, { canvas.postKeyReleased(J2meCanvas.KEY_SELECT_FIRE) }, modifier = Modifier.weight(1f))
                    SoftKeyButton("SEL",   { canvas.postKeyPressed(J2meCanvas.KEY_CLEAR)        }, { canvas.postKeyReleased(J2meCanvas.KEY_CLEAR)        }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun QuickMenuOverlay(
    scaleMode: String,
    smoothScaling: Boolean,
    fpsLimit: Int,
    onScaleModeChange: (String) -> Unit,
    onSmoothScalingChange: (Boolean) -> Unit,
    onFpsLimitChange: (Int) -> Unit,
    onEditLayout: () -> Unit,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1C24)),
            modifier = Modifier
                .width(280.dp)
                .wrapContentHeight()
                .padding(16.dp)
                .clickable(enabled = false, onClick = {}) // block touch
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Quick Menu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary
                )

                // ── Display settings ─────────────────────────────────────────
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Skala Tampilan",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("FIT", "STRETCH", "ORIGINAL").forEach { mode ->
                            FilterChip(
                                selected = scaleMode == mode,
                                onClick = { onScaleModeChange(mode) },
                                label = { Text(mode, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Penghalusan",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Text(
                                if (smoothScaling) "Halus (bilinear)" else "Tajam (nearest)",
                                fontSize = 10.sp,
                                fontFamily = ShareTechMonoFont,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = smoothScaling,
                            onCheckedChange = { onSmoothScalingChange(it) }
                        )
                    }

                    Text(
                        "Batas FPS",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 0 == unlimited; remaining values cap the render thread.
                        listOf(0 to "Off", 60 to "60", 30 to "30", 15 to "15").forEach { (value, label) ->
                            FilterChip(
                                selected = fpsLimit == value,
                                onClick = { onFpsLimitChange(value) },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kembali Bermain")
                }

                OutlinedButton(
                    onClick = onEditLayout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.OpenWith,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Edit Tata Letak")
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Restart Game")
                }

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Keluar Game", color = Color.White)
                }
            }
        }
    }
}

// Persist the live display settings to the per-game settings store, preserving
// any other stored fields (the in-memory GameModel here is built from a partial
// set of Intent extras, so it is only used to seed defaults on first write).
private fun persistDisplaySettings(
    context: Context,
    game: GameModel,
    scaleMode: String,
    smoothScaling: Boolean
) {
    val store = GameSettingsStore(context)
    val current = store.load(game.id) ?: GameSettings.fromGameModel(game)
    store.save(game.id, current.copy(scaleMode = scaleMode, smoothScaling = smoothScaling))
}

private fun Context.currentView(): View? {
    if (this is ComponentActivity) {
        return this.window.decorView.findViewById(android.R.id.content)
    }
    return null
}
