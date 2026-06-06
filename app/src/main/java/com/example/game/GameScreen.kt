package com.example.game

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.atan2

// Helper extension to convert Vector2D to Compose Offset
fun Vector2D.asOffset(): Offset = Offset(x, y)

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isUpgradeOpen by viewModel.isUpgradeMenuOpen.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07040C)) // Outer ambient background
    ) {
        when (uiState) {
            GameUiState.MainMenu -> {
                MainMenuLayout(
                    onStartGame = { viewModel.startGame() },
                    onResetGame = { viewModel.resetGame() }
                )
            }
            GameUiState.InGame -> {
                GameplayLayout(viewModel)
            }
            GameUiState.GameOver -> {
                GameOverLayout(
                    onRestart = { viewModel.resetGame() }
                )
            }
            GameUiState.GameVictory -> {
                GameVictoryLayout(
                    onReset = { viewModel.resetGame() }
                )
            }
        }

        // Pylon/Ruins Upgrade Modal overlay
        if (isUpgradeOpen && uiState == GameUiState.InGame) {
            UpgradeMenuModal(viewModel)
        }
    }
}

@Composable
fun MainMenuLayout(onStartGame: () -> Unit, onResetGame: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            // Cool elegant badge icon
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFE8DEF8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "MINISHOOT\nADVENTURE",
                fontSize = 38.sp,
                color = Color(0xFF1D1B20),
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                lineHeight = 42.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.testTag("game_title")
            )

            Text(
                text = "3D-ELEVATED RETRO BULLET-HELL",
                fontSize = 12.sp,
                color = Color(0xFF6750A4),
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
                    .testTag("play_button")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("START ADVENTURE", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onResetGame,
                border = BorderStroke(1.5.dp, Color(0xFFB3261E)),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFB3261E)),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(50.dp)
                    .testTag("reset_button")
            ) {
                Text("RESET SAVE & PROGRESS", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(36.dp))

            // Game Instructions list
            Surface(
                color = Color(0xFFE8DEF8),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("HOW TO PLAY", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.5.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    BulletPointText("Fly through 9 giant sectors using the virtual stick.")
                    BulletPointText("Your starship auto-aims and fires at nearby enemies.")
                    BulletPointText("Press BOOST or action keys for speed and invincibility.")
                    BulletPointText("Shatter emerald crystals to gain shards for upgrades.")
                    BulletPointText("Locate the Green Keycard in Sector (1,1) Ruins to unlock the Gate.")
                    BulletPointText("Defeat the Colossal Void Core in Sector (2,2) to win!")
                }
            }
        }
    }
}

@Composable
fun BulletPointText(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("⚡", color = Color(0xFF6750A4), fontSize = 11.sp, modifier = Modifier.padding(end = 6.dp))
        Text(text, color = Color(0xFF1D1B20), fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
fun GameplayLayout(viewModel: GameViewModel) {
    val enemies by viewModel.enemies.collectAsStateWithLifecycle()
    val bullets by viewModel.bullets.collectAsStateWithLifecycle()
    val collectibles by viewModel.collectibles.collectAsStateWithLifecycle()
    val obstacles by viewModel.obstacles.collectAsStateWithLifecycle()
    val particles by viewModel.particles.collectAsStateWithLifecycle()
    val screenShake by viewModel.screenShake.collectAsStateWithLifecycle()
    val rawNotification by viewModel.hudNotification.collectAsStateWithLifecycle()

    val currentSector = viewModel.sectorsMap[Pair(viewModel.roomGridX, viewModel.roomGridY)]
        ?: Sector(0, 0, "Unknown Abyss", Color.Gray, Color.DarkGray, Color.LightGray)

    // Dynamic virtual stick touch variables
    var showVirtualStick by remember { mutableStateOf(false) }
    var stickCenter by remember { mutableStateOf(Offset.Zero) }
    var stickPosition by remember { mutableStateOf(Offset.Zero) }

    // Read flows for HUD
    val health by viewModel.playerHealth.collectAsStateWithLifecycle()
    val maxHealth by viewModel.playerMaxHealth.collectAsStateWithLifecycle()
    val shards by viewModel.playerShards.collectAsStateWithLifecycle()
    val level by viewModel.playerLevel.collectAsStateWithLifecycle()
    val xp by viewModel.playerXp.collectAsStateWithLifecycle()
    val keycard by viewModel.hasGreenKeycard.collectAsStateWithLifecycle()
    val nextXpThreshold = level * 120

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. TOP HEADER PANEL ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LEVEL $level",
                    fontSize = 12.sp,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = currentSector.name,
                    fontSize = 22.sp,
                    color = Color(0xFF1D1B20),
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Keycard icon inside top header
                if (keycard) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFFE8DEF8), CircleShape)
                            .testTag("green_key_icon"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Keycard",
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // "?" Help info icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFE8DEF8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "?",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D192B)
                    )
                }

                // Gear Settings shrine upgrade trigger
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFFEADDFF), CircleShape)
                        .clickable { viewModel.openUpgradeMenu() }
                        .testTag("gear_shrine_trigger"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Upgrades",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // --- 2. CENTER GAME BOX (Canvas within bounded frame) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFF1D1B20))
                .border(4.dp, Color(0xFFE8DEF8), RoundedCornerShape(32.dp))
                .pointerInput(Unit) {
                    // Touch events specifically within the gameplay bounds
                    detectDragGestures(
                        onDragStart = { offset ->
                            showVirtualStick = true
                            stickCenter = offset
                            stickPosition = offset
                        },
                        onDrag = { change, dragAmount ->
                            if (showVirtualStick) {
                                change.consume()
                                val rawPosition = stickPosition + dragAmount
                                val distWithVelocity = rawPosition - stickCenter
                                val maxRadius = 130f
                                val distance = distWithVelocity.getDistance()

                                val clampedDir = if (distance > maxRadius) {
                                    Offset(distWithVelocity.x / distance * maxRadius, distWithVelocity.y / distance * maxRadius)
                                } else {
                                    distWithVelocity
                                }
                                stickPosition = stickCenter + clampedDir

                                // Set movement input normalized
                                val normDir = Offset(clampedDir.x / maxRadius, clampedDir.y / maxRadius)
                                viewModel.onMoveInput(normDir.x, normDir.y)
                            }
                        },
                        onDragEnd = {
                            showVirtualStick = false
                            viewModel.onMoveInput(0f, 0f)
                        },
                        onDragCancel = {
                            showVirtualStick = false
                            viewModel.onMoveInput(0f, 0f)
                        }
                    )
                }
        ) {
            // Draw grid in Canvas!
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("game_canvas")
            ) {
                val scaleFactorX = size.width / GameParams.ROOM_SIZE
                val scaleFactorY = size.height / GameParams.ROOM_SIZE

                // Center game coordinate scaling proportionally to maintain aspect
                val minScale = minOf(scaleFactorX, scaleFactorY)
                val gameWidthOnScreen = GameParams.ROOM_SIZE * minScale
                val gameHeightOnScreen = GameParams.ROOM_SIZE * minScale
                val offsetX = (size.width - gameWidthOnScreen) / 2f
                val offsetY = (size.height - gameHeightOnScreen) / 2f

                withTransform({
                    // Camera screen shakes offset translation
                    translate(
                        offsetX + screenShake.x,
                        offsetY + screenShake.y
                    )
                    scale(minScale, minScale, pivot = Offset.Zero)
                }) {
                    // 1. Draw Ground Base
                    drawRect(color = currentSector.groundColor)

                    // 2. Grid lines
                    val spacing = 80f
                    for (j in 0..(GameParams.ROOM_SIZE / spacing).toInt()) {
                        drawLine(
                            color = currentSector.gridColor.copy(alpha = 0.45f),
                            start = Offset(0f, j * spacing),
                            end = Offset(GameParams.ROOM_SIZE, j * spacing),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = currentSector.gridColor.copy(alpha = 0.45f),
                            start = Offset(j * spacing, 0f),
                            end = Offset(j * spacing, GameParams.ROOM_SIZE),
                            strokeWidth = 2f
                        )
                    }

                    // 3. Draw Gate indicators in cardinal walls
                    drawGatewaysOnScreen(currentSector, keycard)

                    // 4. DRAW DROPSHADOWS FOR 3D ELEVATION VIEW
                    val shadowOffset = Offset(13f, 13f)

                    // Obstacles Shadows
                    for (obs in obstacles) {
                        drawCircle(
                            color = Color(0x3F000000),
                            radius = obs.radius,
                            center = obs.position.asOffset() + shadowOffset
                        )
                    }
                    // Enemies Shadows
                    for (ene in enemies) {
                        drawCircle(
                            color = Color(0x35000000),
                            radius = ene.size,
                            center = ene.position.asOffset() + shadowOffset
                        )
                    }
                    // Starship Player Shadow
                    drawPlayerShipShadow(viewModel.playerPos.asOffset() + shadowOffset, viewModel.playerAngle)

                    // 5. DRAW COLLECTIBLES
                    for (col in collectibles) {
                        drawCollectible(col)
                    }

                    // 6. DRAW REAL INTERACTIVE ENTITIES
                    for (obs in obstacles) {
                        drawObstacleEntity(obs)
                    }
                    for (bullet in bullets) {
                        drawBulletEntity(bullet)
                    }
                    for (ene in enemies) {
                        drawEnemyEntity(ene)
                    }

                    // Starship Player Core
                    drawPlayerShip(viewModel)

                    // 7. PARTICLES AND COMBAT FLYING OVERLAYS
                    for (part in particles) {
                        drawParticleSpecial(part)
                    }
                }
            }

            // Dynamic virtual joystick overlay drawn above canvas if user drags on screen
            if (showVirtualStick) {
                VirtualJoystickVisual(stickCenter, stickPosition)
            }

            // --- PROGRESS BARS AND STATS OVERLAY INTEGRATED INSIDE THE CANVAS FRAME ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top-left progress bars
                Column(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Health Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Integrity",
                            tint = Color(0xFFB3261E),
                            modifier = Modifier.size(16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(128.dp)
                                .height(8.dp)
                                .background(Color(0xFF49454F), CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((health / maxHealth.coerceAtLeast(1f)).coerceIn(0f, 1f))
                                    .background(Color(0xFFB3261E), CircleShape)
                            )
                        }
                    }

                    // XP Energy Bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "XP Bar",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(16.dp)
                        )
                        Box(
                            modifier = Modifier
                                .width(128.dp)
                                .height(8.dp)
                                .background(Color(0xFF49454F), CircleShape)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth((xp.toFloat() / nextXpThreshold).coerceIn(0f, 1f))
                                    .background(Color(0xFFD0BCFF), CircleShape)
                            )
                        }
                    }
                }

                // Top-right XP pill tracker in translucent glassmorphism
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(Color(0x99000000), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(Color(0xFFFFD600), CircleShape)
                        )
                        Text(
                            text = "$shards SHARDS",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.testTag("shards_text")
                        )
                    }
                }
            }

            // Slide-in active notification alert banner inside canvas frame as overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = rawNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp)
            ) {
                rawNotification?.let {
                    NotificationBanner(it)
                }
            }
        }

        // --- 3. BOTTOM CONTROLLER PANEL ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual controller Joystick mirroring the actual ship movement
            val animatedOffset = if (showVirtualStick) {
                val dx = stickPosition.x - stickCenter.x
                val dy = stickPosition.y - stickCenter.y
                Offset(dx / 130f * 36f, dy / 130f * 36f)
            } else {
                Offset.Zero
            }

            Box(
                modifier = Modifier
                    .size(110.dp)
                    .background(Color(0xFFE8DEF8), CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Dashed outer ring
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, Color(0x30CAC4D0), CircleShape)
                )

                // Animated knob
                Box(
                    modifier = Modifier
                        .offset(x = animatedOffset.x.dp, y = animatedOffset.y.dp)
                        .size(45.dp)
                        .shadow(4.dp, CircleShape)
                        .background(Color(0xFF6750A4), CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color.White.copy(alpha = 0.25f), CircleShape)
                            .align(Alignment.Center)
                    )
                }
            }

            // Right-side action triggers
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // B Button (Dashes starship)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEADDFF))
                                .clickable { viewModel.triggerDash() }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "B",
                                color = Color(0xFF21005D),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }

                        // A Button (Opens upgrades)
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFEADDFF))
                                .clickable { viewModel.openUpgradeMenu() }
                                .padding(2.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "A",
                                color = Color(0xFF21005D),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        text = "ACTIONS",
                        fontSize = 10.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Primary Large "BOOST" Button
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .shadow(6.dp, RoundedCornerShape(24.dp))
                        .background(Color(0xFF6750A4))
                        .clickable { viewModel.triggerDash() }
                        .testTag("dash_button_overlay")
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BOOST",
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }

                    // Dash cooldown indicator overlay sweep
                    if (viewModel.dashCooldown > 0f) {
                        val progress = viewModel.dashCooldown / GameParams.DASH_COOLDOWN
                        CircularProgressIndicator(
                            progress = { progress },
                            color = Color.White.copy(alpha = 0.4f),
                            strokeWidth = 4.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // Handle bar
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .width(48.dp)
                .height(4.dp)
                .background(Color(0xFF49454F).copy(alpha = 0.2f), CircleShape)
        )
    }
}

// Draw cardinal sector gateway archways
private fun DrawScope.drawGatewaysOnScreen(sector: Sector, hasKey: Boolean) {
    val gateColor = sector.themeColor
    val thick = 16f
    val halfSize = GameParams.ROOM_SIZE / 2f
    val gateWidth = 150f

    // Gate North
    if (sector.gridY > 0) {
        drawRect(
            color = gateColor.copy(alpha = 0.8f),
            topLeft = Offset(halfSize - gateWidth / 2f, 0f),
            size = Size(gateWidth, thick)
        )
    }
    // Gate South
    if (sector.gridY < 2) {
        drawRect(
            color = gateColor.copy(alpha = 0.8f),
            topLeft = Offset(halfSize - gateWidth / 2f, GameParams.ROOM_SIZE - thick),
            size = Size(gateWidth, thick)
        )
    }
    // Gate West
    if (sector.gridX > 0) {
        drawRect(
            color = gateColor.copy(alpha = 0.8f),
            topLeft = Offset(0f, halfSize - gateWidth / 2f),
            size = Size(thick, gateWidth)
        )
    }
    // Gate East (Requires Key card if transition is from room 1,0 to 2,0)
    if (sector.gridX < 2) {
        val eastColor = if (sector.gridX == 1 && sector.gridY == 0 && !hasKey) {
            Color.Red // Locked gate warning crimson!
        } else {
            gateColor
        }
        drawRect(
            color = eastColor.copy(alpha = 0.8f),
            topLeft = Offset(GameParams.ROOM_SIZE - thick, halfSize - gateWidth / 2f),
            size = Size(thick, gateWidth)
        )
    }
}

private fun DrawScope.drawCollectiblesAndShadow(col: Collectible) {
    // shadow
    drawCircle(
        color = Color(0x2E000000),
        radius = 12f,
        center = col.position.asOffset() + Offset(6f,6f)
    )
}

private fun DrawScope.drawCollectible(col: Collectible) {
    val color = if (col.type == Collectible.Type.SHARD) {
        Color(0xFFFFD600) // Gold
    } else {
        Color(0xFFFF1744) // Heart ruby red
    }

    // Rotating pulse radius
    val beatAngle = (System.currentTimeMillis() % 1000) / 1000f * 2 * Math.PI.toFloat()
    val pulseSize = 10f + sin(beatAngle) * 3f

    if (col.type == Collectible.Type.SHARD) {
        // Star diamond path drawing
        val path = Path().apply {
            moveTo(col.position.x, col.position.y - pulseSize)
            lineTo(col.position.x + pulseSize, col.position.y)
            lineTo(col.position.x, col.position.y + pulseSize)
            lineTo(col.position.x - pulseSize, col.position.y)
            close()
        }
        drawPath(path, color)
        // Outer core gleam
        drawPath(path, Color.White, style = Stroke(width = 2f))
    } else {
        // Small heart geometry
        val path = Path().apply {
            moveTo(col.position.x, col.position.y - 4f)
            cubicTo(col.position.x - 8f, col.position.y - 10f, col.position.x - 12f, col.position.y, col.position.x, col.position.y + 10f)
            cubicTo(col.position.x + 12f, col.position.y, col.position.x + 8f, col.position.y - 10f, col.position.x, col.position.y - 4f)
            close()
        }
        drawPath(path, color)
    }
}

private fun DrawScope.drawPlayerShipShadow(pos: Offset, angle: Float) {
    withTransform({
        translate(pos.x, pos.y)
        rotate(angle + 90f, pivot = Offset.Zero) // point northwards facing
    }) {
        val path = Path().apply {
            moveTo(0f, -22f)
            lineTo(18f, 18f)
            lineTo(0f, 8f)
            lineTo(-18f, 18f)
            close()
        }
        drawPath(path, Color(0x35000000))
    }
}

private fun DrawScope.drawPlayerShip(viewModel: GameViewModel) {
    val pos = viewModel.playerPos
    val angle = viewModel.playerAngle
    val invTimer = viewModel.playerInvincibilityTimer
    val isDashing = viewModel.isDashing

    // Handle hit flash intervals
    if (invTimer > 0f) {
        val flashInterval = (invTimer * 10).toInt()
        if (flashInterval % 2 == 0) return // Skip rendering to simulate retro flickering
    }

    withTransform({
        translate(pos.x, pos.y)
        rotate(angle + 90f, pivot = Offset.Zero)
    }) {
        // Jet exhaust embers engine animation
        val exhaustFactor = if (isDashing) 2.5f else 1f
        val pulse = (System.currentTimeMillis() % 200) / 200f
        val exhaustLen = (30f + pulse * 14f) * exhaustFactor

        val exhaustPath = Path().apply {
            moveTo(-8f, 10f)
            lineTo(0f, 10f + exhaustLen)
            lineTo(8f, 10f)
            close()
        }
        drawPath(
            exhaustPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF9100), Color(0x00FF3D00)),
                startY = 10f,
                endY = 10f + exhaustLen
            )
        )

        // Arrowhead ship plate path
        val shipPath = Path().apply {
            moveTo(0f, -22f) // Nose cone
            lineTo(18f, 16f) // Right wing
            lineTo(0f, 7f)  // Rear thruster notch
            lineTo(-18f, 16f) // Left wing
            close()
        }

        // Dashing cyan shields, active gold base hulls
        val coreColor = if (isDashing) Color(0xFF00E5FF) else Color(0xFFFFEB3B)
        val metallicShadow = if (isDashing) Color(0xBB00E5FF) else Color(0xFFE65100)

        drawPath(shipPath, coreColor)
        drawPath(shipPath, metallicShadow, style = Stroke(width = 2.5f))

        // Center flight cockpit windshield panel
        drawCircle(
            color = Color(0xFF0D47A1),
            radius = 6f,
            center = Offset(0f, -3f)
        )
    }
}

private fun DrawScope.drawObstacleEntity(obs: Obstacle) {
    val center = Offset(obs.position.x, obs.position.y)
    when (obs.type) {
        Obstacle.Type.TREE_GREEN -> {
            // Triple layered green branches
            drawCircle(color = Color(0xFF1B5E20), radius = obs.radius * 0.9f, center = center)
            drawCircle(color = Color(0xFF2E7D32), radius = obs.radius * 0.7f, center = center)
            drawCircle(color = Color(0xFF4CAF50), radius = obs.radius * 0.45f, center = center)
            // Accent highlights
            drawCircle(color = Color(0xFF81C784), radius = 5f, center = center - Offset(obs.radius * 0.2f, obs.radius * 0.2f))
        }
        Obstacle.Type.FOREST_ROCK -> {
            drawCircle(color = Color(0xFF4E342E), radius = obs.radius, center = center)
            // Rock cleavage geometric facets
            val facet = Path().apply {
                moveTo(obs.position.x - obs.radius * 0.7f, obs.position.y - obs.radius * 0.2f)
                lineTo(obs.position.x + obs.radius * 0.6f, obs.position.y - obs.radius * 0.7f)
                lineTo(obs.position.x, obs.position.y + obs.radius * 0.8f)
                close()
            }
            drawPath(facet, Color(0x33FFFFFF))
            drawCircle(color = Color(0xFF3E2723), radius = obs.radius, center = center, style = Stroke(width = 3f))
        }
        Obstacle.Type.RUINED_COLUMN -> {
            // Antique ruined temple marble columns
            drawRect(
                color = Color(0xFFCFD8DC),
                topLeft = center - Offset(obs.radius, obs.radius),
                size = Size(obs.radius * 2, obs.radius * 2)
            )
            drawRect(
                color = Color(0xFF90A4AE),
                topLeft = center - Offset(obs.radius, obs.radius),
                size = Size(obs.radius * 2, obs.radius * 2),
                style = Stroke(width = 4f)
            )
            // Cracks
            drawLine(
                color = Color(0xFF455A64),
                start = center - Offset(obs.radius * 0.5f, obs.radius * 0.5f),
                end = center + Offset(obs.radius * 0.4f, obs.radius * 0.4f),
                strokeWidth = 2f
            )
        }
        Obstacle.Type.SHRINE_BEACON -> {
            // High dimensional upgrade portal shrine block
            val spinAngle = (System.currentTimeMillis() % 4000) / 4000f * 2 * Math.PI.toFloat()
            drawCircle(color = Color(0xFF311B92), radius = obs.radius, center = center)
            drawCircle(
                color = Color(0xFF00E5FF),
                radius = obs.radius,
                center = center,
                style = Stroke(width = 3.5f)
            )

            // Inner glowing hover crystal prism
            val cSize = 14f
            val hY = center.y + sin(spinAngle * 3) * 8f - 8f // Floating hovering animation!
            val crystalPath = Path().apply {
                moveTo(center.x, hY - cSize)
                lineTo(center.x + cSize, hY)
                lineTo(center.x, hY + cSize)
                lineTo(center.x - cSize, hY)
                close()
            }
            drawPath(crystalPath, Color(0xFFE040FB))
            drawPath(crystalPath, Color.White, style = Stroke(width = 2f))
        }
        Obstacle.Type.GLOWING_CRYSTAL -> {
            // Glowing emerald crystal. Health indicator scale
            val progress = obs.health / obs.maxHealth
            val scaleRadius = obs.radius * (0.6f + progress * 0.4f)
            val crysColor = Color(0xFF00E676)

            // Diamond layout path
            val path = Path().apply {
                moveTo(obs.position.x, obs.position.y - scaleRadius)
                lineTo(obs.position.x + scaleRadius * 0.8f, obs.position.y)
                lineTo(obs.position.x, obs.position.y + scaleRadius)
                lineTo(obs.position.x - scaleRadius * 0.8f, obs.position.y)
                close()
            }
            drawPath(path, crysColor)
            drawPath(path, Color.White, style = Stroke(width = 2f))

            // Pulse sheen circle
            val shin = (System.currentTimeMillis() % 1500) / 1500f * scaleRadius
            drawCircle(color = Color(0x33FFFFFF), radius = shin, center = center)
        }
        Obstacle.Type.LOCKED_GATE -> {
            // Big red metal heavy barrier blocks access east
            drawRect(
                color = Color(0xFFB71C1C),
                topLeft = center - Offset(obs.radius * 0.2f, obs.radius),
                size = Size(obs.radius * 0.4f, obs.radius * 2)
            )
            // Warning warning visual signs
            drawRect(
                color = Color.Yellow,
                topLeft = center - Offset(obs.radius * 0.08f, obs.radius * 0.5f),
                size = Size(obs.radius * 0.16f, obs.radius)
            )
        }
        Obstacle.Type.ALTAR_CHEST -> {
            // Golden artifacts box protecting key cards
            val healthRatio = obs.health / obs.maxHealth
            drawRect(
                color = Color(0xFFFFD54F),
                topLeft = center - Offset(obs.radius * 0.8f, obs.radius * 0.5f),
                size = Size(obs.radius * 1.6f, obs.radius)
            )
            // Iron borders
            drawRect(
                color = Color(0xFF37474F),
                topLeft = center - Offset(obs.radius * 0.8f, obs.radius * 0.5f),
                size = Size(obs.radius * 1.6f, obs.radius),
                style = Stroke(width = 3.5f)
            )
            // Health percentage overlay
            drawRect(
                color = Color.Red,
                topLeft = center - Offset(obs.radius * 0.8f, obs.radius * 0.9f),
                size = Size(obs.radius * 1.6f * healthRatio, 6f)
            )
        }
        Obstacle.Type.LAVA_POOL -> {
            // Slow hazard toxic puddle
            val rippleRadius = obs.radius * (0.85f + 0.15f * sin((System.currentTimeMillis() % 3000) / 3000f * 2 * Math.PI.toFloat()))
            drawCircle(color = Color(0x99FF3D00), radius = obs.radius, center = center)
            drawCircle(color = Color(0xDDFF9100), radius = rippleRadius, center = center, style = Stroke(width = 4f))
        }
    }
}

private fun DrawScope.drawBulletEntity(bullet: Bullet) {
    // Lead trail
    var idx = 0
    for (t in bullet.trail) {
        val alpha = (1f - idx / 6f) * 0.4f
        drawCircle(
            color = bullet.color.copy(alpha = alpha),
            radius = bullet.radius * (1f - idx / 6f),
            center = Offset(t.x, t.y)
        )
        idx++
    }

    // Core projectile
    drawCircle(
        color = bullet.color,
        radius = bullet.radius,
        center = Offset(bullet.position.x, bullet.position.y)
    )
    drawCircle(
        color = Color.White,
        radius = bullet.radius * 0.45f,
        center = Offset(bullet.position.x, bullet.position.y)
    )
}

private fun DrawScope.drawEnemyEntity(ene: Enemy) {
    val center = Offset(ene.position.x, ene.position.y)
    when (ene.type) {
        Enemy.Type.SLIME -> {
            // Slime bounce squish expansion animation
            val anim = (System.currentTimeMillis() + ene.id.hashCode() % 1000) % 1200 / 1200f
            val radAngle = anim * 2 * Math.PI.toFloat()
            val wOffset = sin(radAngle) * 5f
            val hOffset = cos(radAngle) * 3f

            val rX = ene.size + wOffset
            val rY = ene.size - hOffset

            drawCircle(
                color = Color(0xFF4CAF50),
                radius = rX,
                center = center
            )
            // Face eyes
            drawCircle(color = Color.Black, radius = 3f, center = center - Offset(8f, 4f))
            drawCircle(color = Color.Black, radius = 3f, center = center + Offset(8f, -4f))
        }
        Enemy.Type.DRONE -> {
            // Mechanized laser eye drone
            drawCircle(color = Color(0xFF37474F), radius = ene.size, center = center)
            drawCircle(
                color = Color(0xFF00E676),
                radius = ene.size * 0.45f,
                center = center
            )
            drawCircle(
                color = Color(0xFF00E676),
                radius = ene.size,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // Shell spinning visual arcs
            val angle = (System.currentTimeMillis() % 2000) / 2000f * 360f
            withTransform({
                translate(ene.position.x, ene.position.y)
                rotate(angle, pivot = Offset.Zero)
            }) {
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(ene.size - 4f, -4f),
                    size = Size(8f, 8f)
                )
                drawRect(
                    color = Color(0xFF00E676),
                    topLeft = Offset(-ene.size - 4f, -4f),
                    size = Size(8f, 8f)
                )
            }
        }
        Enemy.Type.SCUTTLER -> {
            // Crawly legs
            val crawlAngle = (System.currentTimeMillis() % 500) / 500f * 2 * Math.PI.toFloat()
            val legExtent = 10f * sin(crawlAngle)

            // Draw legs lines
            for (i in 0..5) {
                val legAngle = (Math.PI / 3 * i).toFloat()
                val extX = cos(legAngle) * (ene.size + legExtent)
                val extY = sin(legAngle) * (ene.size + legExtent)
                drawLine(
                    color = Color(0xFFFF5252),
                    start = center,
                    end = center + Offset(extX, extY),
                    strokeWidth = 3f
                )
            }

            // Main Core
            drawCircle(color = Color(0xFFD50000), radius = ene.size, center = center)
            drawCircle(color = Color.Black, radius = ene.size * 0.65f, center = center)
        }
        Enemy.Type.STATIONARY_TURRET -> {
            // Armored heavy plant fire bud
            drawCircle(color = Color(0xFFCFD8DC), radius = ene.size, center = center)
            drawCircle(color = Color(0xFFFF9100), radius = ene.size * 0.7f, center = center)

            // Draw metal vents around nozzle
            val sections = 4
            val angTurn = (System.currentTimeMillis() % 6000) / 6000f * 360f
            withTransform({
                translate(ene.position.x, ene.position.y)
                rotate(angTurn, pivot = Offset.Zero)
            }) {
                for (i in 0 until sections) {
                    val sAngle = (360f / sections) * i
                    withTransform({
                        rotate(sAngle, pivot = Offset.Zero)
                    }) {
                        drawRect(
                            color = Color(0xFF37474F),
                            topLeft = Offset(-5f, -ene.size * 1.1f),
                            size = Size(10f, 15f)
                        )
                    }
                }
            }
        }
        Enemy.Type.COLOSSAL_BOSS -> {
            // Multi staged obsidian Void Boss Core!
            val spinReactor = (System.currentTimeMillis() % 16000) / 16000f * 360f

            // Inner dark fusion reactor core
            drawCircle(color = Color(0xFF1A0033), radius = ene.size, center = center)
            drawCircle(color = Color(0xFFD500F9), radius = ene.size * 0.65f, center = center)

            // Health fraction glow
            val hpFrac = ene.health / ene.maxHealth
            drawCircle(
                color = Color.White,
                radius = ene.size * 0.65f * (0.8f + 0.2f * sin((System.currentTimeMillis() % 400) / 400f * 2 * Math.PI.toFloat())),
                center = center,
                style = Stroke(width = 4f)
            )

            // Rotating Shield generator plates
            withTransform({
                translate(ene.position.x, ene.position.y)
                rotate(spinReactor, pivot = Offset.Zero)
            }) {
                val plateCount = 6
                for (p in 0 until plateCount) {
                    val plateAngle = (360f / plateCount) * p
                    withTransform({
                        rotate(plateAngle, pivot = Offset.Zero)
                    }) {
                        // Drawing thick rounded plates orbiting the core
                        drawArc(
                            color = Color(0xFF4A148C),
                            startAngle = -20f,
                            sweepAngle = 40f,
                            useCenter = false,
                            topLeft = Offset(-ene.size * 1f, -ene.size * 1f),
                            size = Size(ene.size * 2f, ene.size * 2f),
                            style = Stroke(width = 16f)
                        )
                        // Glowing crystal spike on plates
                        drawCircle(
                            color = Color(0xFFD500F9),
                            radius = 12f,
                            center = Offset(0f, -ene.size)
                        )
                    }
                }
            }

            // Draw a giant health bar floating right above the boss inside coordinates!
            val barW = 160f
            val barH = 10f
            val bX = ene.position.x - barW / 2
            val bY = ene.position.y - ene.size - 30f

            drawRect(
                color = Color.DarkGray,
                topLeft = Offset(bX, bY),
                size = Size(barW, barH)
            )
            drawRect(
                color = Color(0xFFD500F9),
                topLeft = Offset(bX, bY),
                size = Size(barW * hpFrac, barH)
            )
            drawRect(
                color = Color.White,
                topLeft = Offset(bX, bY),
                size = Size(barW, barH),
                style = Stroke(width = 1.5f)
            )
        }
    }

    // Standard floating red health bars for non-bosses who took damage
    if (ene.type != Enemy.Type.COLOSSAL_BOSS && ene.health < ene.maxHealth) {
        val pct = ene.health / ene.maxHealth
        val bW = ene.size * 1.5f
        val bH = 6f
        val sX = ene.position.x - bW / 2f
        val sY = ene.position.y - ene.size - 14f

        drawRect(color = Color.DarkGray, topLeft = Offset(sX, sY), size = Size(bW, bH))
        drawRect(color = Color.Red, topLeft = Offset(sX, sY), size = Size(bW * pct, bH))
    }
}

private fun DrawScope.drawParticleSpecial(part: Particle) {
    val lifePct = part.life / part.maxLife
    val alpha = lifePct.coerceIn(0f, 1f)

    when (part.type) {
        Particle.Type.SPARK -> {
            drawCircle(
                color = part.color.copy(alpha = alpha),
                radius = part.size * (0.3f + 0.7f * lifePct),
                center = Offset(part.position.x, part.position.y)
            )
        }
        Particle.Type.ENGINE_TRAIL -> {
            drawCircle(
                color = part.color.copy(alpha = alpha * 0.45f),
                radius = part.size * lifePct,
                center = Offset(part.position.x, part.position.y)
            )
        }
        Particle.Type.EXPLOSION -> {
            // Growing fire rings
            drawCircle(
                color = part.color.copy(alpha = alpha),
                radius = part.size * (1f - lifePct),
                center = Offset(part.position.x, part.position.y),
                style = Stroke(width = 3f)
            )
        }
        Particle.Type.FLOATING_TEXT -> {
            // Use native canvas to draw text overlays
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = part.color.copy(alpha = alpha).run {
                        android.graphics.Color.argb(
                            (red * 255).toInt(),
                            (green * 255).toInt(),
                            (blue * 255).toInt(),
                            (alpha * 255).toInt()
                        )
                    }
                    textSize = part.size * 2f // Scale coordinate text
                    typeface = android.graphics.Typeface.MONOSPACE
                    textAlign = android.graphics.Paint.Align.CENTER
                    isFakeBoldText = true
                }
                drawText(part.text, part.position.x, part.position.y, paint)
            }
        }
    }
}

@Composable
fun VirtualJoystickVisual(center: Offset, position: Offset) {
    val density = LocalDensity.current
    val cxDp = with(density) { center.x.toDp() }
    val cyDp = with(density) { center.y.toDp() }
    val pxDp = with(density) { position.x.toDp() }
    val pyDp = with(density) { position.y.toDp() }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Outer Stick bounds ring
        Box(
            modifier = Modifier
                .offset(x = cxDp - 65.dp, y = cyDp - 65.dp)
                .size(130.dp)
                .background(Color(0x3AFFFFFF), CircleShape)
                .align(Alignment.TopStart)
                .testTag("joystick_bounds")
        )

        // Inner stick knob core draggable
        Box(
            modifier = Modifier
                .offset(x = pxDp - 30.dp, y = pyDp - 30.dp)
                .size(60.dp)
                .background(
                    Brush.radialGradient(listOf(Color(0xFFE040FB), Color(0xFF0D47A1))),
                    CircleShape
                )
                .align(Alignment.TopStart)
                .testTag("joystick_knob")
        )
    }
}

@Composable
fun GameplayHudPanel(viewModel: GameViewModel, currentSector: Sector) {
    val health by viewModel.playerHealth.collectAsStateWithLifecycle()
    val maxHealth by viewModel.playerMaxHealth.collectAsStateWithLifecycle()
    val shards by viewModel.playerShards.collectAsStateWithLifecycle()
    val level by viewModel.playerLevel.collectAsStateWithLifecycle()
    val xp by viewModel.playerXp.collectAsStateWithLifecycle()
    val keycard by viewModel.hasGreenKeycard.collectAsStateWithLifecycle()

    val nextXpThreshold = level * 120

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top Overlay Stats Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ship Integrity Health Blocks
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Integrity",
                    tint = Color(0xFFFF1744),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))

                // Custom segmented lifebar cubes
                Row {
                    val fullHearts = health.toInt()
                    val totalBlocks = maxHealth.toInt()
                    for (i in 0 until totalBlocks) {
                        val colorBlock = if (i < fullHearts) Color(0xFFFF1744) else Color(0x3AFFFFFF)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(width = 14.dp, height = 18.dp)
                                .background(colorBlock, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            // Map and Save Menu toggles
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Keycard Indicator
                if (keycard) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Keycard",
                        tint = Color(0xFF00E676),
                        modifier = Modifier
                            .size(24.dp)
                            .padding(end = 6.dp)
                            .testTag("green_key_icon")
                    )
                }

                // Coins/Shards Counter
                Surface(
                    color = Color(0x770D1117),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Shards",
                            tint = Color(0xFFFFD600),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = shards.toString(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("shards_text")
                        )
                    }
                }

                // Shrine Upgrade trigger gear clicking (Accessible directly for ease!)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF311B92), CircleShape)
                        .clickable { viewModel.openUpgradeMenu() }
                        .testTag("gear_shrine_trigger"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Upgrades",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Sector Title Overlay (Centered top)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 54.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = currentSector.name,
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Map Grid coords indicator e.g. "Sector [1,1]"
            Text(
                text = "Grid Coord: [${viewModel.roomGridX}, ${viewModel.roomGridY}]",
                color = currentSector.themeColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // XP Levels Progress Bar Bottom Center
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.6f)
                .padding(bottom = 74.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "LEVEL $level",
                    color = Color.Cyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "$xp / $nextXpThreshold XP",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { xp.toFloat() / nextXpThreshold },
                color = Color.Cyan,
                trackColor = Color(0x3300FFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }

        // RIGHT ACTION BOOSTER DASH BUTTON
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 40.dp, bottom = 44.dp)
                .testTag("dash_button_overlay")
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFE040FB), Color(0xFFC51162))),
                        CircleShape
                    )
                    .clickable { viewModel.triggerDash() },
                contentAlignment = Alignment.Center
            ) {
                // Dash Icon or symbol
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Dash",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        "BOOST",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Cooldown overlay sweep indicator
                if (viewModel.dashCooldown > 0f) {
                    val progress = viewModel.dashCooldown / GameParams.DASH_COOLDOWN
                    CircularProgressIndicator(
                        progress = { progress },
                        color = Color.White.copy(alpha = 0.5f),
                        strokeWidth = 4.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationBanner(text: String) {
    Surface(
        color = Color(0xEE110729),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.5.dp, Color(0xFFE040FB)),
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .shadow(12.dp, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = text,
                color = Color.White,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
fun UpgradeMenuModal(viewModel: GameViewModel) {
    val shards by viewModel.playerShards.collectAsStateWithLifecycle()
    val healthLv by viewModel.upgradeHealthLevel.collectAsStateWithLifecycle()
    val speedLv by viewModel.upgradeSpeedLevel.collectAsStateWithLifecycle()
    val dmgLv by viewModel.upgradeDamageLevel.collectAsStateWithLifecycle()
    val rateLv by viewModel.upgradeFireRateLevel.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = { viewModel.closeUpgradeMenu() }) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF7FF)),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(2.dp, Color(0xFFE8DEF8)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("upgrade_modal_card")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Text(
                    text = "RUIN BEACON UPGRADE",
                    fontSize = 18.sp,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.testTag("upgrade_modal_title")
                )

                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD600),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$shards Shards Available",
                        fontSize = 14.sp,
                        color = Color(0xFF1D1B20),
                        fontWeight = FontWeight.Bold
                    )
                }

                HorizontalDivider(color = Color(0xFFE8DEF8), modifier = Modifier.padding(vertical = 8.dp))

                // Stats rows
                UpgradeItemRow("Max Integrity (HP)", healthLv, shards, "health", viewModel)
                UpgradeItemRow("Move Accelerator", speedLv, shards, "speed", viewModel)
                UpgradeItemRow("Plasma Blasters Damage", dmgLv, shards, "damage", viewModel)
                UpgradeItemRow("Capacitors Fire Rate", rateLv, shards, "firerate", viewModel)

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { viewModel.closeUpgradeMenu() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("close_terminal_button")
                ) {
                    Text("CLOSE UPGRADE TERMINAL", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun UpgradeItemRow(
    name: String,
    level: Int,
    availableShards: Int,
    upgradeKey: String,
    viewModel: GameViewModel
) {
    val cost = when (level) {
        1 -> 15
        2 -> 35
        3 -> 70
        4 -> 120
        else -> 0
    }
    val cannotAfford = availableShards < cost || level >= 5

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, color = Color(0xFF1D1B20), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row {
                for (i in 1..5) {
                    val starColor = if (i <= level) Color(0xFF6750A4) else Color(0x1F1D1B20)
                    Text(
                        text = "★",
                        color = starColor,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                }
            }
        }

        if (level >= 5) {
            Text(
                "MAX",
                color = Color(0xFF6750A4),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        } else {
            Button(
                onClick = { viewModel.purchaseUpgrade(upgradeKey) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (cannotAfford) Color(0x1F1D1B20) else Color(0xFF6750A4),
                    contentColor = if (cannotAfford) Color(0x661D1B20) else Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                enabled = !cannotAfford,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.testTag("buy_${upgradeKey}_button")
            ) {
                Text(
                    text = "$cost ❖",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun GameOverLayout(onRestart: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(Color(0xFFF9DEDC), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFB3261E),
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "STARSHIP MELTDOWN",
                color = Color(0xFF1D1B20),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("game_over_header")
            )

            Text(
                text = "Your core integrity was compromised.",
                color = Color(0xFF6750A4),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 8.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
                    .testTag("retry_button")
            ) {
                Text("RESTART FROM BASE", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GameVictoryLayout(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFFEADDFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "VICTORY ACHIEVED!",
                color = Color(0xFF1D1B20),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("victory_header")
            )

            Text(
                text = "The Colossal Void Reactor Cell is deactivated.\nYou escaped the system bounds!",
                color = Color(0xFF6750A4),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            Button(
                onClick = onReset,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4), contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(56.dp)
                    .testTag("win_restart_button")
            ) {
                Text("NEW GAME + EXPLORE", fontWeight = FontWeight.Bold)
            }
        }
    }
}
