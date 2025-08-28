package com.example.forklift_phone.ui.theme

import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.forklift_phone.R

@Composable
fun BottomNavBar(nav: NavController) {
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    NavigationBar(
        containerColor = Color(0xFF1A1D22),   // 다크바
        contentColor = Color.White
    ) {
        NavigationBarItem(
            selected = current == "monitor",
            onClick = { if (current != "monitor") nav.navigate("monitor") },
            icon = { Icon(painterResource(R.drawable.monitor), contentDescription = "Monitor") },
            label = { Text("Monitor") }
        )
        NavigationBarItem(
            selected = current == "home",
            onClick  = { nav.navigate("home") { launchSingleTop = true; popUpTo("home"){inclusive=false} } },
            icon     = { Icon(painterResource(R.drawable.ic_home), contentDescription = "Home") },
            label    = { Text("Home") }
        )
        NavigationBarItem(
            selected = current == "dashboard",
            onClick = { if (current != "dashboard") nav.navigate("dashboard") },
            icon = { Icon(painterResource(R.drawable.dashboard), contentDescription = "Dashboard") },
            label = { Text("Dashboard") }
        )
    }
}
