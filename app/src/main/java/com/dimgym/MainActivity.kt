package com.dimgym

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dimgym.data.DimGymDb
import com.dimgym.ui.theme.DimGymTheme
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Default.Home),
    Plan("Plan", Icons.Default.DateRange),
    Settings("Settings", Icons.Default.Settings),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val dao = DimGymDb.get(applicationContext).dao()
        setContent {
            val settings by remember { dao.settings() }.collectAsState(null)
            var splash by rememberSaveable { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(1200)
                splash = false
            }
            val dark = settings?.theme != "light"
            val barsDark = dark || splash
            DisposableEffect(barsDark) {
                val bars = if (barsDark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            DimGymTheme(dark = dark) {
                Box(Modifier.fillMaxSize()) {
                    DimGymApp()
                    AnimatedVisibility(
                        visible = splash,
                        exit = fadeOut(tween(350)),
                    ) {
                        SplashScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun DimGymApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var warm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        warm = true
    }
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current

    val screens: List<@Composable () -> Unit> = listOf(
        { TodayScreen(onOpenPlan = { tab = 1 }) },
        { PlanScreen() },
        { SettingsScreen() },
    )

    Scaffold(
        containerColor = c.background,
        bottomBar = {
            // Apple-style Frosted Glass Tab Bar:
            // Translucent material background, hairline top border, spring-scaled icons,
            // single-word labels and direct haptic confirmation on touch-down.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.surface.copy(alpha = 0.92f))
                    .border(
                        width = 0.5.dp,
                        color = c.outlineVariant.copy(alpha = 0.45f),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(52.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.entries.forEachIndexed { i, t ->
                        val isSelected = tab == i
                        val tabScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
                            label = "tabIconScale",
                        )

                        val interactionSource = remember { MutableInteractionSource() }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = {
                                        if (tab != i) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            tab = i
                                            warm = true
                                        }
                                    },
                                ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = t.icon,
                                contentDescription = t.label,
                                tint = if (isSelected) c.primary else c.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = tabScale
                                        scaleY = tabScale
                                    },
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = t.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    letterSpacing = 0.1.sp,
                                ),
                                color = if (isSelected) c.primary else c.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        },
    ) { pad ->
        Box(
            Modifier
                .padding(pad)
                .consumeWindowInsets(pad)
                .fillMaxSize(),
        ) {
            screens.forEachIndexed { i, screen ->
                key(i) {
                    if (i == tab || warm) {
                        KeepAlive(active = i == tab, content = screen)
                    }
                }
            }
        }
    }
}

/**
 * Keeps a tab composed after its first visit, so switching never rebuilds it or reloads its data.
 * A hidden tab is neither drawn nor touchable, and the tab you switch to transitions smoothly.
 */
@Composable
private fun KeepAlive(active: Boolean, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active) {
        if (active) alpha.animateTo(1f, tween(180)) else alpha.snapTo(0f)
    }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .layout { m, c ->
                val p = m.measure(c)
                layout(p.width, p.height) {
                    if (active) p.place(0, 0)
                }
            },
    ) {
        CompositionLocalProvider(LocalActive provides active, content = content)
    }
}
