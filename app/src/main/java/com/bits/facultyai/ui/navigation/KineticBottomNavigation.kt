package com.bits.facultyai.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.bits.facultyai.ui.theme.KineticBorder
import com.bits.facultyai.ui.theme.KineticSpacing
import com.bits.facultyai.ui.theme.KineticType
import com.bits.facultyai.ui.theme.LocalKineticColors

data class NavItem(val route: String, val label: String)

val bottomNavItems = listOf(
    NavItem("home", "HOME"),
    NavItem("timetable", "TIMETABLE"),
    NavItem("attendance", "ATTENDANCE"),
    NavItem("assistant", "AI"),
    NavItem("more", "MORE"),
)

@Composable
fun KineticBottomNavigation(navController: NavHostController) {
    val k = LocalKineticColors.current
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(KineticBorder.hair, k.border)
            .background(k.background)
            .padding(horizontal = KineticSpacing.sm, vertical = KineticSpacing.sm),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            Column(
                modifier = Modifier
                    .clickable {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                    .padding(horizontal = KineticSpacing.md, vertical = KineticSpacing.xs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .width(if (selected) 20.dp else 6.dp)
                        .height(3.dp)
                        .background(if (selected) k.accent else Color.Transparent)
                )
                Spacer(Modifier.height(KineticSpacing.xs))
                Text(
                    text = item.label,
                    style = if (selected) KineticType.labelBold else KineticType.label,
                    color = if (selected) k.foreground else k.mutedForeground,
                )
            }
        }
    }
}
