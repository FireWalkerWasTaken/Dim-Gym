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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
            // Shorter than before (was 1.5 s): the wait is pure latency, so it only covers the first build
            LaunchedEffect(Unit) { delay(1100); splash = false }
            val system = isSystemInDarkTheme()
            val dark = when (settings?.theme) {
                "light" -> false
                "system" -> system
                else -> true // "dark", and the moment before settings load
            }
            // System bar icons follow the in-app theme (and stay light over the always-dark splash)
            val barsDark = dark || splash
            DisposableEffect(barsDark) {
                val bars = if (barsDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                onDispose {}
            }
            DimGymTheme(dark = dark) {
                CompositionLocalProvider(LocalReduceMotion provides rememberReduceMotion()) {
                    val reduce = LocalReduceMotion.current
                    // The app settles forward as the splash lifts: 96% to 100%, on a critically damped spring
                    val reveal by animateFloatAsState(if (splash) 0f else 1f, motionSpec(1f, 0.6f), label = "reveal")
                    Box(Modifier.fillMaxSize()) {
                        DimGymApp(
                            Modifier.graphicsLayer {
                                val s = if (reduce) 1f else 0.96f + 0.04f * reveal
                                scaleX = s; scaleY = s
                            },
                        ) // builds behind the splash, so it is ready when the splash lifts
                        AnimatedVisibility(
                            splash,
                            exit = fadeOut(tween(if (reduce) 150 else 380)) + (if (reduce) scaleOut(targetScale = 1f) else scaleOut(tween(380), targetScale = 1.05f)),
                        ) { SplashScreen() }
                    }
                }
            }
        }
    }
}

@Composable
private fun DimGymApp(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var warm by remember { mutableStateOf(false) }
    // While the splash is up, build the other tabs too so switching later costs nothing
    LaunchedEffect(Unit) { delay(300); warm = true }
    val c = MaterialTheme.colorScheme
    val items = remember { Tab.entries.map { TabItem(it.label, it.icon) } }
    val screens: List<@Composable () -> Unit> = listOf(
        { TodayScreen(onOpenPlan = { tab = 1; warm = true }) },
        { PlanScreen() },
        { SettingsScreen() },
    )
    val barInset = TabBarContent + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Surface(modifier.fillMaxSize(), color = c.background, contentColor = c.onBackground) {
        Box(Modifier.fillMaxSize()) {
            // Screens run edge to edge and scroll under the translucent tab bar; they read the inset to stay clear of it
            CompositionLocalProvider(LocalBarInset provides barInset) {
                screens.forEachIndexed { i, screen ->
                    key(i) { if (i == tab || warm) KeepAlive(active = i == tab, content = screen) }
                }
            }
            TabBar(items, tab, onSelect = { tab = it; warm = true }, modifier = Modifier.align(Alignment.BottomCenter))
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
    LaunchedEffect(active) { if (active) alpha.animateTo(1f, tween(200)) else alpha.snapTo(0f) }
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
