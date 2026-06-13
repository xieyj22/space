package com.example.game.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.domain.*
import com.example.ui.theme.*
import java.util.Random
import kotlin.math.*

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val shipHp by viewModel.shipHp
    val shipMaxHp by viewModel.shipMaxHp
    val score by viewModel.score
    val wave by viewModel.wave
    val waveTime by viewModel.waveTimeLeft
    val exp by viewModel.exp
    val expNeeded by viewModel.expNeeded
    val level by viewModel.techLevel
    val creditsEarned by viewModel.creditsEarned
    val bossActive by viewModel.bossSpawned
    val gameStatus by viewModel.gameState

    // Local state for Joystick drag center displacement
    var joystickOffset by remember { mutableStateOf(Offset.Zero) }
    val maxDragRadius = 140f // virtual pixels

    // Starfield particle background
    val ambientStars = remember {
        List(60) {
            val r = Random()
            Offset(r.nextFloat(), r.nextFloat()) to (r.nextFloat() * 1.5f + 0.8f) // (coord, radius)
        }
    }

    // Capture ticking scale multiplier for canvas ratio
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElegantBg)
    ) {
        // --- 1. CORE PHYSICS CANVAS ---
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(gameStatus) {
                    // Touch input processed in custom control overlay
                }
        ) {
            canvasSize = size
            val scaleX = size.width / viewModel.virtualWidth
            val scaleY = size.height / viewModel.virtualHeight

            withTransform({
                // Uniformly stretch/fit coordinates to local hardware width & height
                scale(scaleX, scaleY, pivot = Offset.Zero)
            }) {
                // A1. Strategic Dot Grid Simulation Backdrop
                val dotSpacing = 50f
                val dotColor = ElegantPrimary.copy(alpha = 0.05f)
                var dX = 25f
                while (dX < viewModel.virtualWidth) {
                    var dY = 25f
                    while (dY < viewModel.virtualHeight) {
                        drawCircle(
                            color = dotColor,
                            radius = 0.8f,
                            center = Offset(dX, dY)
                        )
                        dY += dotSpacing
                    }
                    dX += dotSpacing
                }

                // A2. Draw scrolling background cosmic elements
                for (star in ambientStars) {
                    val scrolledY = (star.first.y * viewModel.virtualHeight + viewModel.gameTime.value * 28f * star.second) % viewModel.virtualHeight
                    drawCircle(
                        color = Color.White.copy(alpha = 0.42f),
                        radius = star.second * 1.2f,
                        center = Offset(star.first.x * viewModel.virtualWidth, scrolledY)
                    )
                }

                // B. Draw EXP Gems and Coins
                for (gem in viewModel.expGems) {
                    if (gem.isGold) {
                        // Gold Coin Core
                        drawCircle(
                            color = ElegantAccentGold,
                            radius = 6.5f,
                            center = Offset(gem.x, gem.y)
                        )
                        drawCircle(
                            color = Color(0xFFFF8F00),
                            radius = 6.5f,
                            center = Offset(gem.x, gem.y),
                            style = Stroke(width = 1.2f)
                        )
                    } else {
                        // Blue exp crystal rhombuses
                        drawDiamondCrystal(gem.x, gem.y, Color(0xFF29B6F6))
                    }
                }

                // C. Draw Particles (Debris explosion sparks)
                for (p in viewModel.particles) {
                    val progress = p.currentLife.toFloat() / p.maxLife
                    val pAlpha = (1f - progress).coerceIn(0f, 1f)
                    drawCircle(
                        color = p.color.copy(alpha = pAlpha),
                        radius = p.size * (1f - progress * 0.5f),
                        center = Offset(p.x, p.y)
                    )
                }

                // D. Draw Enemy spaceships
                for (enemy in viewModel.enemies) {
                    drawEnemySpaceship(enemy)
                }

                // E. Draw Player Projectiles (Lasers and Bullets)
                for (bullet in viewModel.bullets) {
                    if (bullet.damage == 0f) {
                        // Custom Instant electric laser beam segments
                        drawLine(
                            color = bullet.color,
                            start = Offset(bullet.x, bullet.y),
                            end = Offset(bullet.x + bullet.vx * 10f, bullet.y + bullet.vy * 10f),
                            strokeWidth = bullet.radius
                        )
                    } else {
                        // General bullets structure
                        drawCircle(
                            color = bullet.color,
                            radius = bullet.radius,
                            center = Offset(bullet.x, bullet.y)
                        )
                        // Outer neon halo
                        drawCircle(
                            color = bullet.color.copy(alpha = 0.35f),
                            radius = bullet.radius + 3f,
                            center = Offset(bullet.x, bullet.y),
                            style = Stroke(width = 1.5f)
                        )
                    }
                }

                // F. Active Player Orbital Shield
                val shieldWeapon = viewModel.activeWeapons.find { it.type == WeaponType.ORBITAL_SHIELD }
                if (shieldWeapon != null) {
                    val lv = shieldWeapon.level
                    val numOrbs = if (lv >= 5) 5 else if (lv >= 4) 4 else if (lv >= 3) 3 else if (lv >= 2) 2 else 1
                    val baseAngle = viewModel.shieldAngle
                    val r = 105f + lv * 12f
                    for (i in 0 until numOrbs) {
                        val angle = baseAngle + (2 * Math.PI * i) / numOrbs
                        val ox = viewModel.shipX.value + cos(angle).toFloat() * r
                        val oy = viewModel.shipY.value + sin(angle).toFloat() * r

                        // Draw particle core
                        drawCircle(
                            color = Color(0xFFE040FB),
                            radius = 9f + lv * 0.8f,
                            center = Offset(ox, oy)
                        )
                        // Energy Orbit trail Ring
                        drawCircle(
                            color = Color(0xFFE040FB).copy(alpha = 0.18f),
                            radius = r,
                            center = Offset(viewModel.shipX.value, viewModel.shipY.value),
                            style = Stroke(width = 1f)
                        )
                    }
                }

                // G. Draw Player Space Fighter
                drawPlayerShip(
                    x = viewModel.shipX.value,
                    y = viewModel.shipY.value,
                    shipType = viewModel.activeShip.value,
                    gameTimeTick = viewModel.gameTime.value
                )
            }
        }

        // --- 2. FLOATING TEXTS OVERLAY ---
        Box(modifier = Modifier.fillMaxSize()) {
            val scaleX = canvasSize.width / viewModel.virtualWidth
            val scaleY = canvasSize.height / viewModel.virtualHeight

            for (text in viewModel.floatingTexts) {
                if (scaleX > 0 && scaleY > 0) {
                    val px = text.x * scaleX
                    val py = text.y * scaleY
                    Text(
                        text = text.text,
                        color = text.color.copy(alpha = (text.life / 40f).coerceIn(0f, 1f)),
                        fontSize = if (text.isBig) 15.sp else 10.sp,
                        fontWeight = if (text.isBig) FontWeight.Black else FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .offset { IntOffset(px.toInt() - 25, py.toInt() - 15) }
                    )
                }
            }
        }

        // --- 3. TOP TELEMETRY HUD (Active metrics panel) ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .align(Alignment.TopCenter)
        ) {
            // Level and Progress Bar Core
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "LV.${level}",
                    color = ElegantPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )

                // Glassmorphic EXP Bar Slider
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ElegantBorder.copy(alpha = 0.3f))
                ) {
                    val progress = if (expNeeded > 0) exp.toFloat() / expNeeded else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ElegantPrimary, ElegantSecondary)
                                )
                            )
                    )
                }

                // Pause Button Trigger
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(ElegantHeaderBg, RoundedCornerShape(8.dp))
                        .border(1.dp, ElegantBorder, RoundedCornerShape(8.dp))
                        .clickable {
                            if (gameStatus == GameStatus.RUNNING) viewModel.pauseGame()
                            else if (gameStatus == GameStatus.PAUSED) viewModel.resumeGame()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (gameStatus == GameStatus.RUNNING) "‖" else "▶",
                        color = ElegantPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main stats dashboard: HP, Score, Coins, Wave timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Player HP Tube
                Column(modifier = Modifier.width(180.dp)) {
                    Text(
                        text = "装甲结构 HP: ${shipHp.toInt()} / ${shipMaxHp.toInt()}",
                        color = ElegantTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElegantHeaderBg)
                            .border(1.dp, ElegantBorder, RoundedCornerShape(6.dp))
                    ) {
                        val hpPercent = if (shipMaxHp > 0) shipHp / shipMaxHp else 0f
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hpPercent.coerceIn(0f, 1f))
                                .background(
                                    if (hpPercent > 0.4f) ElegantAccentGreen else ElegantAccentRed
                                )
                        )
                    }
                }

                // Wave telemetry
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val timerColor = if (waveTime < 6f && !bossActive) ElegantAccentRed else ElegantPrimary
                    Text(
                        text = if (bossActive) "⚠️ BOSS CRITICAL WAR ⚠️" else "波次 WAVE ${wave}",
                        color = if (bossActive) ElegantAccentRed else ElegantTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (bossActive) "歼灭指挥母舰" else String.format("00:%02d", waveTime.toInt().coerceAtLeast(0)),
                        color = if (bossActive) ElegantAccentRed else timerColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Score stats count
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ProfileHUDMetric(label = "摧毁星级", valStr = "$score", tColor = ElegantSecondary)
                    ProfileHUDMetric(label = "收获母能金币", valStr = "💰 $creditsEarned", tColor = ElegantAccentGold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Wave Log Notifications Ticker panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
            ) {
                if (viewModel.activeLogList.isNotEmpty()) {
                    Text(
                        text = viewModel.activeLogList.last(),
                        color = if (viewModel.activeLogList.last().contains("⚠️") || viewModel.activeLogList.last().contains("警告")) ElegantAccentRed else ElegantAccentGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- 4. BOTTOM-LEFT TOUCH JOYSTICK CONTROLLER ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 56.dp, bottom = 44.dp)
                .size(160.dp)
                .clip(RoundedCornerShape(80.dp))
                .background(ElegantHeaderBg.copy(alpha = 0.6f))
                .border(2.dp, ElegantBorder, RoundedCornerShape(80.dp))
                .pointerInput(gameStatus) {
                    if (gameStatus != GameStatus.RUNNING) return@pointerInput
                    detectDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            joystickOffset = Offset.Zero
                            viewModel.onMoveInput(0f, 0f)
                        },
                        onDragCancel = {
                            joystickOffset = Offset.Zero
                            viewModel.onMoveInput(0f, 0f)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val potential = joystickOffset + dragAmount
                            val dist = sqrt(potential.x * potential.x + potential.y * potential.y)
                            if (dist <= maxDragRadius) {
                                joystickOffset = potential
                            } else {
                                joystickOffset = Offset(
                                    (potential.x / dist) * maxDragRadius,
                                    (potential.y / dist) * maxDragRadius
                                )
                            }
                            // Scale velocities
                            viewModel.onMoveInput(
                                joystickOffset.x / maxDragRadius,
                                joystickOffset.y / maxDragRadius
                            )
                        }
                    )
                }
        ) {
            // Drawn virtual Joystick outer details
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(30.dp)
                    .border(1.5.dp, ElegantBorder.copy(alpha = 0.3f), RoundedCornerShape(50.dp))
            )

            // Dynamic Center thumb knob handle
            val knobSize = 56.dp
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (80.dp.toPx() - knobSize.toPx() / 2 + joystickOffset.x).toInt(),
                            (80.dp.toPx() - knobSize.toPx() / 2 + joystickOffset.y).toInt()
                        )
                    }
                    .size(knobSize)
                    .clip(RoundedCornerShape(knobSize / 2))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(ElegantPrimaryContainer, ElegantPrimary)
                        )
                    )
                    .border(2.dp, ElegantPrimaryVariant, RoundedCornerShape(knobSize / 2))
            )
        }

        // --- 5. BOTTOM-RIGHT WEAPON SYSTEM HUD GRID ---
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 26.dp)
                .background(ElegantHeaderBg.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                .border(1.dp, ElegantBorder, RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (weapon in viewModel.activeWeapons) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(ElegantInnerBg, RoundedCornerShape(8.dp))
                            .border(1.dp, ElegantBorder.copy(alpha = 0.6f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(
                                text = weapon.type.iconSymbol,
                                color = ElegantTextPrimary,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Lv.${weapon.level}",
                                color = ElegantAccentGold,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // --- 6. OVERLAYS (PAUSED, TECH SELECTION AND GAMEOVER) ---

        // A. PAUSE OVERLAY
        if (gameStatus == GameStatus.PAUSED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElegantBg.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ElegantCardBg,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, ElegantBorder),
                    modifier = Modifier.width(320.dp).padding(20.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "星际航线已暂停",
                            color = ElegantPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "已阻断战术时序环。建议玩家休整充能。",
                            color = ElegantTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )

                        Button(
                            onClick = { viewModel.resumeGame() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElegantPrimary,
                                contentColor = ElegantOnPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("恢复战斗 RESUME", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.navigateToMenu() },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, ElegantBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElegantTextPrimary)
                        ) {
                            Text("返回船坞 ENGINE BAY")
                        }
                    }
                }
            }
        }

        // B. LEVEL UP CHOICE MODAL (Technology selecting)
        if (gameStatus == GameStatus.LEVEL_UP_CHOICE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElegantBg.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.width(680.dp)
                ) {
                    Text(
                        text = "🔬 发现未知星空科技，请拟合升级",
                        color = ElegantAccentGold,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        for (option in viewModel.levelUpChoices) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(210.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ElegantInnerBg)
                                    .border(1.5.dp, ElegantBorder, RoundedCornerShape(16.dp))
                                    .clickable { viewModel.chooseUpgrade(option) }
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .background(ElegantPrimary.copy(alpha = 0.15f), RoundedCornerShape(23.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = option.iconSymbol,
                                                fontSize = 24.sp,
                                                color = Color.White
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        Text(
                                            text = option.title,
                                            color = ElegantTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = option.text,
                                            color = ElegantTextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 13.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 4
                                        )
                                    }

                                    // Dynamic select chip
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ElegantAccentGold.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "组装 INSTALL",
                                            color = ElegantAccentGold,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // C. GAME OVER SCREEN
        if (gameStatus == GameStatus.GAME_OVER) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ElegantBg.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = ElegantCardBg,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(2.dp, ElegantAccentRed),
                    modifier = Modifier.width(360.dp).padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "❌ 飞船已坠毁 Game Over",
                            color = ElegantAccentRed,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "您的星际装甲在狂暴的黑洞弹幕中过载崩解。",
                            color = ElegantTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )

                        Divider(color = ElegantBorder.copy(alpha = 0.4f), thickness = 1.dp)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            HUDReportRow("歼灭分数 Destroyed", "$score")
                            HUDReportRow("突破波次 Waves Clashed", "Wave $wave")
                            HUDReportRow("夺回母能金币 Credits Salvaged", "+ $creditsEarned 💰")
                        }

                        Button(
                            onClick = { viewModel.startNewGame() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElegantAccentGold,
                                contentColor = ElegantOnPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("再次征战 CLASH REPLAY", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.navigateToMenu() },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, ElegantBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElegantTextPrimary)
                        ) {
                            Text("返回空天船坞 ENGINE BAY")
                        }
                    }
                }
            }
        }
    }
}

// Sub components

@Composable
fun ProfileHUDMetric(
    label: String,
    valStr: String,
    tColor: Color
) {
    Column(horizontalAlignment = Alignment.End) {
        Text(label, color = ElegantTextSecondary, fontSize = 9.sp)
        Text(valStr, color = tColor, fontSize = 14.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun HUDReportRow(label: String, valStr: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ElegantTextSecondary, fontSize = 12.sp)
        Text(valStr, color = ElegantTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

// Vector Drawing helpers inside Compose Canvas

private fun DrawScope.drawDiamondCrystal(cx: Float, cy: Float, color: Color) {
    val size = 7f
    val path = Path().apply {
        moveTo(cx, cy - size)
        lineTo(cx + size, cy)
        lineTo(cx, cy + size)
        lineTo(cx - size, cy)
        close()
    }
    drawPath(path = path, color = color)
}

private fun DrawScope.drawPlayerShip(
    x: Float,
    y: Float,
    shipType: ShipType,
    gameTimeTick: Float
) {
    // Ship Core layout offsets
    val size = 20f

    // Wobbling Engine fire
    val flameLength = 15f + sin(gameTimeTick * 30f) * 6f
    val flamePath = Path().apply {
        moveTo(x - 5f, y + size * 0.7f)
        lineTo(x, y + size * 0.7f + flameLength)
        lineTo(x + 5f, y + size * 0.7f)
        close()
    }
    drawPath(
        path = flamePath,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFFFFEA00), Color(0xFFFF3D00).copy(alpha = 0.0f))
        )
    )

    when (shipType) {
        ShipType.VANGUARD -> {
            // Cyan Arrow jet
            val shipPath = Path().apply {
                moveTo(x, y - size) // Nose tip
                lineTo(x + size * 0.8f, y + size * 0.6f) // Right wing
                lineTo(x + size * 0.3f, y + size * 0.4f) // Right inner flank
                lineTo(x - size * 0.3f, y + size * 0.4f) // Left inner flank
                lineTo(x - size * 0.8f, y + size * 0.6f) // Left wing
                close()
            }
            drawPath(path = shipPath, color = shipType.primaryColor)
            
            // Cockpit center glass
            drawCircle(
                color = Color.White,
                radius = 4f,
                center = Offset(x, y - size * 0.2f)
            )
        }
        ShipType.DREADNOUGHT -> {
            // Heavy Red rounded fortress
            val baseRectWidth = size * 1.8f
            val baseRectHeight = size * 1.2f
            
            drawRoundRect(
                color = shipType.primaryColor,
                topLeft = Offset(x - baseRectWidth / 2, y - baseRectHeight / 2),
                size = Size(baseRectWidth, baseRectHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Heavy nose gun extension
            drawRect(
                color = shipType.accentColor,
                topLeft = Offset(x - 6f, y - size * 1.1f),
                size = Size(12f, size * 0.8f)
            )
            // Wing shields
            drawLine(
                color = Color.White,
                start = Offset(x - baseRectWidth/2 - 4f, y - size * 0.6f),
                end = Offset(x - baseRectWidth/2 - 4f, y + size * 0.6f),
                strokeWidth = 3f
            )
            drawLine(
                color = Color.White,
                start = Offset(x + baseRectWidth/2 + 4f, y - size * 0.6f),
                end = Offset(x + baseRectWidth/2 + 4f, y + size * 0.6f),
                strokeWidth = 3f
            )
        }
        ShipType.VOID_PHANTOM -> {
            // Psychedelic Purple lightning deltawing
            val shipPath = Path().apply {
                moveTo(x, y - size * 1.2f) // Sharper nose
                lineTo(x + size * 0.9f, y + size * 0.5f)
                lineTo(x, y + size * 0.1f) // Center trailing hollow cut
                lineTo(x - size * 0.9f, y + size * 0.5f)
                close()
            }
            drawPath(path = shipPath, color = shipType.primaryColor)
            
            // Blade line details
            drawLine(
                color = shipType.accentColor,
                start = Offset(x, y - size * 0.9f),
                end = Offset(x, y + size * 0.05f),
                strokeWidth = 2.5f
            )
        }
    }
}

private fun DrawScope.drawEnemySpaceship(enemy: Enemy) {
    val s = enemy.radius

    when (enemy.type) {
        EnemyType.SCOUT -> {
            // Small green rhomboid scout
            val path = Path().apply {
                moveTo(enemy.x, enemy.y - s)
                lineTo(enemy.x + s, enemy.y)
                lineTo(enemy.x, enemy.y + s)
                lineTo(enemy.x - s, enemy.y)
                close()
            }
            drawPath(path = path, color = enemy.type.color)
            // Inner scanner eye
            drawCircle(Color.White, radius = s * 0.28f, center = Offset(enemy.x, enemy.y - s * 0.15f))
        }
        EnemyType.CHASER -> {
            // Red fast interceptor arrowhead
            val path = Path().apply {
                moveTo(enemy.x, enemy.y + s) // Flying downward usually
                lineTo(enemy.x + s, enemy.y - s * 0.7f)
                lineTo(enemy.x, enemy.y - s * 0.2f)
                lineTo(enemy.x - s, enemy.y - s * 0.7f)
                close()
            }
            drawPath(path = path, color = enemy.type.color)
        }
        EnemyType.SPITTER -> {
            // Orange hexagonal tank
            val path = Path().apply {
                for (i in 0 until 6) {
                    val angle = (2 * Math.PI * i) / 6
                    val px = enemy.x + cos(angle).toFloat() * s
                    val py = enemy.y + sin(angle).toFloat() * s
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(path = path, color = enemy.type.color)
            drawCircle(Color.Black, radius = s * 0.4f, center = Offset(enemy.x, enemy.y))
            drawCircle(Color(0xFFFFB74D), radius = s * 0.2f, center = Offset(enemy.x, enemy.y)) // charging pulse
        }
        EnemyType.METEOR -> {
            // Jagged Asteroid rock
            drawCircle(color = enemy.type.color, radius = s, center = Offset(enemy.x, enemy.y))
            drawCircle(color = Color.Black.copy(alpha = 0.2f), radius = s * 0.7f, center = Offset(enemy.x, enemy.y))
            // draw small craters
            drawCircle(Color(0x3B000000), radius = s * 0.22f, center = Offset(enemy.x - s * 0.3f, enemy.y - s * 0.2f))
            drawCircle(Color(0x3B000000), radius = s * 0.3f, center = Offset(enemy.x + s * 0.2f, enemy.y + s * 0.3f))
        }
        EnemyType.ELITE -> {
            // Imperial purple fighter wedge
            val path = Path().apply {
                moveTo(enemy.x, enemy.y - s * 0.8f)
                lineTo(enemy.x + s, enemy.y - s * 0.8f)
                lineTo(enemy.x + s * 0.5f, enemy.y + s)
                lineTo(enemy.x - s * 0.5f, enemy.y + s)
                lineTo(enemy.x - s, enemy.y - s * 0.8f)
                close()
            }
            drawPath(path = path, color = enemy.type.color)
            drawCircle(Color.White, radius = s * 0.2f, center = Offset(enemy.x, enemy.y))
        }
        EnemyType.BOSS -> {
            // Colossal alien mothership with floating shields
            drawCircle(color = enemy.type.color, radius = s, center = Offset(enemy.x, enemy.y))
            drawCircle(color = Color.Black, radius = s * 0.65f, center = Offset(enemy.x, enemy.y))
            
            // Pulsing fusion core reactor
            val wavePulse = (sin(enemy.phase * 6f) * 0.15f + 0.85f)
            drawCircle(
                color = Color(0xFFFFEA00),
                radius = s * 0.45f * wavePulse,
                center = Offset(enemy.x, enemy.y)
            )

            // Orbiting shields
            for (i in 0 until 4) {
                val orAngle = enemy.phase * 1.5f + (2 * Math.PI * i) / 4
                val ox = enemy.x + cos(orAngle).toFloat() * s * 1.3f
                val oy = enemy.y + sin(orAngle).toFloat() * s * 1.3f
                drawCircle(color = Color(0xFFE040FB), radius = s * 0.18f, center = Offset(ox, oy))
                drawLine(
                    color = Color(0xFFE040FB).copy(alpha = 0.35f),
                    start = Offset(enemy.x, enemy.y),
                    end = Offset(ox, oy),
                    strokeWidth = 2.5f
                )
            }

            // Top overlay boss health slider tube
            val hpPercent = enemy.hp / enemy.maxHp
            val barWidth = s * 2.2f
            val barHeight = 8f
            drawRect(
                color = Color.Black,
                topLeft = Offset(enemy.x - barWidth / 2, enemy.y - s - 25f),
                size = Size(barWidth, barHeight)
            )
            drawRect(
                color = Color(0xFFFF1744),
                topLeft = Offset(enemy.x - barWidth / 2, enemy.y - s - 25f),
                size = Size(barWidth * hpPercent, barHeight)
            )
        }
    }
}

// Utility color list casting helper
private fun <T> listOf(vararg elements: T): List<T> = elements.toList()
