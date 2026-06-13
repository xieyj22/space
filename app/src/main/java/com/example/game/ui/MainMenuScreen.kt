package com.example.game.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.game.database.PlayerStats
import com.example.game.domain.ShipType
import com.example.ui.theme.*
import java.util.Random

@Composable
fun MainMenuScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    val stats by viewModel.playerStats.collectAsState()
    
    // Star ambient background particles
    val starField = remember {
        List(40) {
            val r = Random()
            Offset(r.nextFloat(), r.nextFloat()) to (r.nextFloat() * 2f + 1f)
        }
    }
    
    // Tab switching for Shop vs Base Info
    var activeTab by remember { mutableStateOf("SHIPS") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElegantBg)
    ) {
        // Draw starry space canvas overlaid with high-fidelity dot grid simulation
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // 1. Futuristic Dot Grid Pattern
            val dotSpacing = 40f
            val dotColor = ElegantPrimary.copy(alpha = 0.08f)
            var currentX = 20f
            while (currentX < w) {
                var currentY = 20f
                while (currentY < h) {
                    drawCircle(
                        color = dotColor,
                        radius = 1.2f,
                        center = Offset(currentX, currentY)
                    )
                    currentY += dotSpacing
                }
                currentX += dotSpacing
            }
            
            // 2. Twinkling Stars
            for (star in starField) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.5f),
                    radius = star.second,
                    center = Offset(star.first.x * w, star.first.y * h)
                )
            }
        }

        // Dashboard Console Wrap
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            
            // Left Side: Pilot Profile, Record stats and LAUNCH Action
            Surface(
                modifier = Modifier
                    .weight(0.42f)
                    .fillMaxHeight(),
                color = ElegantCardBg.copy(alpha = 0.85f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.5.dp, ElegantBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Title Logo Header
                        Text(
                            text = "🚀 星际征途",
                            color = ElegantPrimary,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "COSMIC ODYSSEY",
                            color = ElegantTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        Divider(color = ElegantBorder.copy(alpha = 0.4f), thickness = 1.dp)

                        Spacer(modifier = Modifier.height(14.dp))

                        // Player Profile metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ProfileMetricCard(
                                title = "母能黄金 credits",
                                value = "${stats.credits} 💰",
                                tint = ElegantAccentGold,
                                modifier = Modifier.weight(1f)
                            )
                            ProfileMetricCard(
                                title = "历史最高 wave",
                                value = "Wave ${stats.highScore}",
                                tint = ElegantSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Selected Battleship Showcase
                        val currentShipType = ShipType.entries.find { it.id == stats.selectedShipId } ?: ShipType.VANGUARD
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ElegantInnerBg, RoundedCornerShape(12.dp))
                                .border(1.dp, ElegantBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(currentShipType.primaryColor)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "已就绪: ${currentShipType.shipName}",
                                        color = ElegantTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    text = currentShipType.description,
                                    color = ElegantTextSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // BIG LAUNCH GAME BUTTON
                    Button(
                        onClick = { viewModel.startNewGame() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElegantPrimary,
                            contentColor = ElegantOnPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "开启星征 LAUNCH",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }

            // Right Side: Ships Selection and Shop Upgrades
            Column(
                modifier = Modifier
                    .weight(0.58f)
                    .fillMaxHeight()
            ) {
                // Secondary tab row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabToggleButton(
                        text = "🚀 机体整备舱 Fleet",
                        active = activeTab == "SHIPS",
                        color = ElegantPrimary,
                        onClick = { activeTab = "SHIPS" },
                        modifier = Modifier.weight(1f)
                    )
                    TabToggleButton(
                        text = "🛠️ 装备核升级 HyperShop",
                        active = activeTab == "SHOP",
                        color = ElegantAccentGold,
                        onClick = { activeTab = "SHOP" },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Dynamic Terminal Display Pane
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = ElegantCardBg.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, ElegantBorder)
                ) {
                    AnimatedContent(
                        targetState = activeTab,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "tab_animation"
                    ) { tab ->
                        when (tab) {
                            "SHIPS" -> FleetSelectionTab(stats, viewModel)
                            "SHOP" -> ShopUpgradesTab(stats, viewModel)
                        }
                    }
                }
            }
        }
    }
}

// Sub-components

@Composable
fun TabToggleButton(
    text: String,
    active: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) color else ElegantHeaderBg,
            contentColor = if (active) ElegantOnPrimary else ElegantTextPrimary
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (active) color else ElegantBorder),
        modifier = modifier.height(44.dp)
    ) {
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun ProfileMetricCard(
    title: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(ElegantHeaderBg, RoundedCornerShape(12.dp))
            .border(1.dp, ElegantBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 10.dp, horizontal = 14.dp)
    ) {
        Column {
            Text(title, color = ElegantTextSecondary, fontSize = 10.sp)
            Text(value, color = tint, fontSize = 17.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun FleetSelectionTab(stats: PlayerStats, viewModel: GameViewModel) {
    val unlocked = remember(stats.unlockedShips) {
        stats.unlockedShips.split(",")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(ShipType.entries.toTypedArray()) { ship ->
            val isUnlocked = unlocked.contains(ship.id)
            val isSelected = stats.selectedShipId == ship.id

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) ship.primaryColor.copy(alpha = 0.12f) else ElegantInnerBg,
                        RoundedCornerShape(12.dp)
                    )
                    .border(
                        1.5.dp,
                        if (isSelected) ship.primaryColor else if (isUnlocked) ElegantBorder.copy(alpha = 0.5f) else Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        if (isUnlocked && !isSelected) {
                            viewModel.selectActiveShip(ship.id)
                        }
                    }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.68f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(ship.primaryColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = ship.shipName,
                                color = ElegantTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text("搭载中 RUNNING", fontSize = 9.sp, fontWeight = FontWeight.Black) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = ship.primaryColor,
                                        labelColor = ElegantOnPrimary
                                    ),
                                    border = null,
                                    modifier = Modifier.height(18.dp)
                                )
                            }
                        }
                        Text(
                            text = ship.description,
                            color = ElegantTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text("🛡️ 生命值: ${ship.baseHp.toInt()}", color = ElegantAccentRed, fontSize = 11.sp)
                            Text("⚡ 推进力: ${ship.baseSpeed.toInt()}", color = ElegantSecondary, fontSize = 11.sp)
                            Text("☄ 伤害系数: x${ship.damageModifier}", color = ElegantAccentGold, fontSize = 11.sp)
                        }
                    }

                    // Action buttons (Buy, Locked, Selected)
                    Column(
                        modifier = Modifier.weight(0.32f),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (!isUnlocked) {
                            Button(
                                onClick = { viewModel.purchaseUnlockShip(ship.id, ship.cost) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElegantAccentGold,
                                    contentColor = ElegantOnPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("${ship.cost} 💰", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        } else {
                            if (!isSelected) {
                                OutlinedButton(
                                    onClick = { viewModel.selectActiveShip(ship.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, ship.primaryColor),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ship.primaryColor),
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Text("部署 DEPLOY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShopUpgradesTab(stats: PlayerStats, viewModel: GameViewModel) {
    // Generate static cost structures
    val statOptions = remember(stats) {
        listOf(
            ShopStatOption(
                type = "ARMOR",
                name = "强化钛合金护甲 (Max HP)",
                description = "增加纳米钛合金防护网。每阶提升 15% 战机装甲耐久极限。",
                tier = stats.armorTier,
                cost = getStatCost(stats.armorTier),
                icon = "🛡️"
            ),
            ShopStatOption(
                type = "SPEED",
                name = "矢量跃迁推进引擎 (Move Speed)",
                description = "装载高级等离子微推进喷口。每阶提升 10% 矢量推进力。",
                tier = stats.speedTier,
                cost = getStatCost(stats.speedTier),
                icon = "🚀"
            ),
            ShopStatOption(
                type = "RATE",
                name = "脉冲射击超导芯 (Fire Rate)",
                description = "重载火控电容导板。每阶缩减 10% 双星极置飞弹射击冷却时间。",
                tier = stats.rateTier,
                cost = getStatCost(stats.rateTier),
                icon = "⏳"
            ),
            ShopStatOption(
                type = "MAGNET",
                name = "奇星力场纳米吸盘 (Magnet)",
                description = "微调量子重力吸引力。每星阶提升 25% 能量吸附归港范围。",
                tier = stats.magnetTier,
                cost = getStatCost(stats.magnetTier),
                icon = "🧲"
            ),
            ShopStatOption(
                type = "CRIT",
                name = "热磁态战眼狙控镜 (Crit Rate)",
                description = "搭载高精度战术自锁探头。每阶提供 5% 的双倍能量爆轰爆。最高25%。",
                tier = stats.critTier,
                cost = getStatCost(stats.critTier),
                icon = "🎯"
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(statOptions) { option ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ElegantInnerBg, RoundedCornerShape(12.dp))
                    .border(1.dp, ElegantBorder, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(0.7f)) {
                        Text(
                            text = "${option.icon} ${option.name}",
                            color = ElegantTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = option.description,
                            color = ElegantTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                        // Show glowing indicators reflecting tier level
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (t in 1..5) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp, 6.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (t <= option.tier) ElegantPrimary else ElegantBorder.copy(alpha = 0.3f))
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(0.3f),
                        horizontalAlignment = Alignment.End
                    ) {
                        if (option.tier >= 5) {
                            Text(
                                "已满阶 MAX",
                                color = ElegantAccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        } else {
                            val canAfford = stats.credits >= option.cost
                            Button(
                                onClick = { viewModel.purchaseShopUpgrade(option.type, option.cost) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (canAfford) ElegantAccentGold else ElegantBorder.copy(alpha = 0.4f),
                                    contentColor = ElegantOnPrimary,
                                    disabledContainerColor = ElegantBorder.copy(alpha = 0.2f),
                                    disabledContentColor = ElegantTextSecondary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                enabled = canAfford,
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("${option.cost} 💰", fontSize = 11.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

data class ShopStatOption(
    val type: String,
    val name: String,
    val description: String,
    val tier: Int,
    val cost: Int,
    val icon: String
)

private fun getStatCost(tier: Int): Int {
    return when(tier) {
        0 -> 100
        1 -> 200
        2 -> 450
        3 -> 800
        4 -> 1400
        else -> 99999
    }
}
