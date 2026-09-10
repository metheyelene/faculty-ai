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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticMotion
import com.bits.facultyai.ui.theme.KineticShape
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalIsDark
import com.bits.facultyai.ui.theme.LocalKineticColors

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
 *  - The bar is rendered as an overlay inside the root Column and sits above
 *    the system navigation area using `windowInsetsPadding(WindowInsets.navigationBars)`
 *    — adapts to gesture nav, 3-button nav and per-device insets automatically.
 *    No hardcoded bottom padding.
 *  - Horizontally centered with side margins; never stretches edge-to-edge.
 *  - Content screens pad their scroll ends by [KineticBottomNavigation.contentClearance]
 *    so the last list item can scroll fully above the bar.
 */
object KineticBottomNavigation {
    /** Height of the pill itself (indicator + label + vertical padding). */
    val barHeight = 60.dp

    /**
     * Bottom clearance screens should add after their last item: the pill,
     * its surrounding padding and a little breathing room.
     */
    val contentClearance = barHeight + 48.dp
}

@Composable
fun KineticBottomNavigation(navController: NavHostController) {
    val k = LocalKineticColors.current
    val isDark = LocalIsDark.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Column(
        modifier = Modifier
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
                .border(KineticBorder.hair, if (isDark) k.glassBorder else k.border, KineticShape.slight)
                .background(
                    // Translucent glass surface over app content.
                    if (isDark) k.glassSurface else k.background.copy(alpha = 0.94f),
                    KineticShape.slight,
                )
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
                Column(
                    modifier = Modifier
                        .kineticPressScale(interaction)
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
