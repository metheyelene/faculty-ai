package com.bits.facultyai.ui.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalIsDark
import com.bits.facultyai.ui.theme.LocalKineticColors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/** Rounded dock shape — shared with the glass system's moderate radius. */
private val DockShape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)

/**
 * Liquid-glass dock surface with a REAL backdrop blur.
 *
 * When [hazeState] is provided, the dock is a Haze child: the app content
 * behind it (the NavHost, marked as the Haze source) is diffused through a
 * RenderEffect backdrop blur on Android 12+/13+, then covered by a
 * translucent glass tint and a lit top edge. On devices without hardware
 * blur support Haze renders its scrim fallback ([fallbackTint]), so the dock
 * stays fully readable everywhere — one visual system, two capability tiers.
 */
private fun Modifier.glassDockSurface(hazeState: HazeState?): Modifier = composed {
    val k = LocalKineticColors.current
    val shaped = this
        .clip(DockShape)
        .border(1.dp, k.glassBorder, DockShape)
    if (hazeState != null) {
        shaped
            .hazeChild(hazeState) {
                blurRadius = 22.dp
                noiseFactor = 0.08f
                tints = listOf(HazeTint(k.glassBlurTint))
                backgroundColor = k.background
                fallbackTint = HazeTint(k.glassThick)
            }
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(k.glassHighlight, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.4f,
                    )
                )
            }
    } else {
        shaped
            .background(k.glassThick, DockShape)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(k.glassHighlight, Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.4f,
                    )
                )
            }
    }
}

data class NavItem(val route: String, val label: String)

val bottomNavItems = listOf(
    NavItem("home", "HOME"),
    NavItem("timetable", "TIMETABLE"),
    NavItem("attendance", "ATTEND"),
    NavItem("assistant", "AI"),
    NavItem("more", "MORE"),
)

/**
 * Floating bottom navigation.
 *
 * Inset rules (single source of truth — no double padding anywhere else):
 *  - The bar is rendered as an OVERLAY on top of the NavHost (see
 *    FacultyAINavHost) so screens genuinely scroll underneath it — the real
 *    backdrop blur keeps passing content readable.
 *  - It sits above the system navigation area using
 *    `windowInsetsPadding(WindowInsets.navigationBars)` — adapts to gesture
 *    nav, 3-button nav and per-device insets. No hardcoded bottom padding.
 *  - Horizontally centered with side margins; never stretches edge-to-edge.
 *  - Content screens add [bottomClearance] after their last item so nothing
 *    rests beneath the dock. The dock hides itself while the keyboard is
 *    open (gated in FacultyAINavHost) so it never covers the composer.
 */
object KineticBottomNavigation {
    /** Height of the dock itself (indicator + label + vertical padding). */
    val barHeight = 60.dp

    /**
     * The dock's footprint above the system navigation area: bar + its own
     * vertical padding + breathing room. Does NOT include the nav-bar inset.
     */
    fun dockOverlayHeight(): Dp = barHeight + KineticSpacing.sm * 2 + 12.dp

    /**
     * Bottom clearance screens add after their last content item so it can
     * scroll fully clear of the floating dock (includes the nav-bar inset,
     * which the dock itself consumes).
     */
    @Composable
    fun bottomClearance(): Dp {
        val density = LocalDensity.current
        return dockOverlayHeight() + with(density) {
            WindowInsets.navigationBars.getBottom(density).toDp()
        }
    }
}

@Composable
fun KineticBottomNavigation(
    navController: NavHostController,
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val k = LocalKineticColors.current
    val isDark = LocalIsDark.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Column(
        modifier = modifier
            .fillMaxWidth()
            // The only bottom-inset consumer in the app: lifts the whole
            // floating bar clear of gesture/3-button navigation areas.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 20.dp, vertical = KineticSpacing.sm),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(KineticBottomNavigation.barHeight)
                .glassDockSurface(hazeState)
                .padding(horizontal = KineticSpacing.xs, vertical = KineticSpacing.sm),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                // Active indicator grows from the left — moves, never teleports.
                val indicatorWidth by animateDpAsState(
                    targetValue = if (selected) 20.dp else 6.dp,
                    animationSpec = KineticMotion.springStandard(),
                    label = "navIndicator",
                )
                val labelScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = KineticMotion.springFast(),
                    label = "navLabelScale",
                )
                val interaction = remember { MutableInteractionSource() }
                // Subtle purple glass highlight on the active item.
                val itemShape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .kineticPressScale(interaction)
                        .clip(itemShape)
                        .background(
                            if (selected) k.accent.copy(alpha = if (isDark) 0.16f else 0.22f) else Color.Transparent,
                            itemShape,
                        )
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .width(indicatorWidth)
                            .height(3.dp)
                            .background(if (selected) k.accent else Color.Transparent)
                    )
                    Spacer(Modifier.height(KineticSpacing.xs))
                    Text(
                        text = item.label,
                        style = if (selected) KineticType.labelBold else KineticType.label,
                        color = if (selected) k.foreground else k.mutedForeground,
                        modifier = Modifier.graphicsLayer { scaleX = labelScale; scaleY = labelScale },
                    )
                }
            }
        }
    }
}

/** Local press-scale helper to avoid a components dependency cycle here. */
private fun Modifier.kineticPressScale(interaction: MutableInteractionSource): Modifier =
    this.then(
        Modifier.composed {
            val pressed by interaction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.95f else 1f,
                animationSpec = KineticMotion.springFast(),
                label = "navPress",
            )
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        }
    )
