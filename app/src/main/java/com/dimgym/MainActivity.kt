package com.dimgym

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
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
            LaunchedEffect(Unit) { delay(1500); splash = false }
            val dark = settings?.theme != "light"
            // System bar icons follow the in-app theme (and stay light over the always-dark splash)
            val barsDark = dark || splash
            DisposableEffect(barsDark) {
                val bars = if (barsDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            DimGymTheme(dark = dark) {
                Box(Modifier.fillMaxSize()) {
                    DimGymApp() // builds behind the splash, so it is ready when the splash fades
                    AnimatedVisibility(splash, exit = fadeOut(tween(450))) { SplashScreen() }
                }
            }
        }
    }
}

@Composable
private fun DimGymApp() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var warm by remember { mutableStateOf(false) }
    // While the splash is up, build the other tabs too so switching later costs nothing
    LaunchedEffect(Unit) { delay(300); warm = true }
    val c = MaterialTheme.colorScheme
    val screens: List<@Composable () -> Unit> = listOf(
        { TodayScreen(onOpenPlan = { tab = 1 }) },
        { PlanScreen() },
        { SettingsScreen() },
    )
    Scaffold(
        containerColor = c.background,
        bottomBar = {
            NavigationBar(containerColor = c.surface) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i; warm = true },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = c.onPrimaryContainer, selectedTextColor = c.onSurface,
                            indicatorColor = c.primaryContainer,
                            unselectedIconColor = c.onSurfaceVariant, unselectedTextColor = c.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).consumeWindowInsets(pad).fillMaxSize()) {
            screens.forEachIndexed { i, screen ->
                key(i) { if (i == tab || warm) KeepAlive(active = i == tab, content = screen) }
            }
        }
    }
}

/**
 * Keeps a tab composed after its first visit, so switching never rebuilds it or reloads its data.
 * A hidden tab is neither drawn nor touchable, and the tab you switch to fades in.
 */
@Composable
private fun KeepAlive(active: Boolean, content: @Composable () -> Unit) {
    val alpha = remember { Animatable(if (active) 1f else 0f) }
    LaunchedEffect(active) { if (active) alpha.animateTo(1f, tween(180)) else alpha.snapTo(0f) }
    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { this.alpha = alpha.value }
            .layout { m, c ->
                val p = m.measure(c)
                layout(p.width, p.height) { if (active) p.place(0, 0) } // not placed = not drawn, not touchable
            },
    ) {
        CompositionLocalProvider(LocalActive provides active, content = content)
    }
}