package com.example.hsiaowear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.window.core.layout.WindowWidthSizeClass
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hsiaowear.data.local.ClothingEntity
import com.example.hsiaowear.ui.screen.AddClothingScreen
import com.example.hsiaowear.ui.screen.ClothingDetailScreen
import com.example.hsiaowear.ui.screen.LobsterScreen
import com.example.hsiaowear.ui.screen.OnboardingScreen
import com.example.hsiaowear.ui.screen.SettingsScreen
import com.example.hsiaowear.ui.screen.TodayScreen
import com.example.hsiaowear.ui.screen.WardrobeScreen
import com.example.hsiaowear.ui.theme.HsiaoWearTheme
import com.example.hsiaowear.viewmodel.LobsterViewModel
import com.example.hsiaowear.viewmodel.SettingsViewModel
import com.example.hsiaowear.viewmodel.TodayViewModel
import com.example.hsiaowear.viewmodel.WardrobeViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            HsiaoWearTheme {
                MainContent()
            }
        }
    }
}

data class NavItem(
    val label: String,
    val titleRes: Int,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val index: Int
)

val navItems = listOf(
    NavItem("今日", R.string.today_title, Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, 0),
    NavItem("衣橱", R.string.wardrobe_title, Icons.Filled.Checkroom, Icons.Outlined.Checkroom, 1),
    NavItem("AI助手", R.string.lobster_title, Icons.Filled.Bolt, Icons.Outlined.Bolt, 2),
    NavItem("设置", R.string.settings_title, Icons.Filled.Settings, Icons.Outlined.Settings, 3)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MainContent() {
    val settingsViewModel = hiltViewModel<SettingsViewModel>()
    val wardrobeViewModel = hiltViewModel<WardrobeViewModel>()
    var showOnboarding by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var showAddClothing by remember { mutableStateOf(false) }
    var selectedClothing by remember { mutableStateOf<ClothingEntity?>(null) }

    LaunchedEffect(Unit) {
        showOnboarding = !settingsViewModel.isOnboardingCompleted()
    }

    when {
        showOnboarding -> {
            OnboardingScreen(
                viewModel = settingsViewModel,
                onComplete = { showOnboarding = false }
            )
        }
        else -> {
            val adaptiveInfo = currentWindowAdaptiveInfo()
            val isExpanded = adaptiveInfo.windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED

            Box(modifier = Modifier.fillMaxSize()) {
                if (isExpanded) {
                    AdaptiveScreen(
                        currentPage = currentPage,
                        onPageChange = { currentPage = it },
                        wardrobeViewModel = wardrobeViewModel,
                        onAddClothing = { showAddClothing = true },
                        onClothingDetail = { clothing -> selectedClothing = clothing }
                    )
                } else {
                    PhoneScreen(
                        currentPage = currentPage,
                        onPageChange = { currentPage = it },
                        wardrobeViewModel = wardrobeViewModel,
                        onAddClothing = { showAddClothing = true },
                        onClothingDetail = { clothing -> selectedClothing = clothing }
                    )
                }

                if (showAddClothing) {
                    AddClothingScreen(
                        onSave = { name, category, color, imageUrl ->
                            wardrobeViewModel.addClothing(name, category, color, imageUrl)
                            showAddClothing = false
                        },
                        onDismiss = { showAddClothing = false },
                        onMatting = { wardrobeViewModel.processClothingImage(it) }
                    )
                }

                if (selectedClothing != null) {
                    ClothingDetailScreen(
                        clothing = selectedClothing!!,
                        onBack = { selectedClothing = null },
                        onEdit = { },
                        onDelete = {
                            wardrobeViewModel.deleteClothing(selectedClothing!!.id)
                            selectedClothing = null
                        },
                        onUpdate = { updated ->
                            wardrobeViewModel.updateClothing(
                                id = updated.id,
                                name = updated.name,
                                category = updated.category,
                                color = updated.color,
                                imageUrl = updated.imageUrl
                            )
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneScreen(
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    wardrobeViewModel: WardrobeViewModel,
    onAddClothing: () -> Unit,
    onClothingDetail: (ClothingEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(navItems[currentPage].titleRes),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 0.dp
            ) {
                navItems.forEach { item ->
                    val isSelected = currentPage == item.index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onPageChange(item.index) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = currentPage,
            transitionSpec = {
                fadeIn() togetherWith fadeOut()
            },
            label = "page_switch"
        ) { page ->
            when (page) {
                0 -> TodayScreen(viewModel = hiltViewModel<TodayViewModel>(), paddingValues = paddingValues)
                1 -> WardrobeScreen(
                    viewModel = wardrobeViewModel,
                    onAddClothing = onAddClothing,
                    onClothingDetail = onClothingDetail,
                    paddingValues = paddingValues
                )
                2 -> LobsterScreen(viewModel = hiltViewModel<LobsterViewModel>(), paddingValues = paddingValues)
                3 -> SettingsScreen(viewModel = hiltViewModel<SettingsViewModel>(), paddingValues = paddingValues)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveScreen(
    currentPage: Int,
    onPageChange: (Int) -> Unit,
    wardrobeViewModel: WardrobeViewModel,
    onAddClothing: () -> Unit,
    onClothingDetail: (ClothingEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(navItems[currentPage].titleRes),
                        fontWeight = FontWeight.SemiBold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavigationRail(
                modifier = Modifier.fillMaxHeight(),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                header = {
                    Row(
                        modifier = Modifier
                            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "👗",
                            fontSize = 28.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "小不衣橱",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "AI 穿搭助手",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            ) {
                navItems.forEach { item ->
                    NavigationRailItem(
                        selected = currentPage == item.index,
                        onClick = { onPageChange(item.index) },
                        icon = {
                            Icon(
                                imageVector = if (currentPage == item.index) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight())

            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                AnimatedContent(
                    targetState = currentPage,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "tablet_page_switch"
                ) { page ->
                    when (page) {
                        0 -> TodayScreen(viewModel = hiltViewModel<TodayViewModel>(), paddingValues = PaddingValues(0.dp))
                        1 -> WardrobeScreen(
                            viewModel = wardrobeViewModel,
                            onAddClothing = onAddClothing,
                            onClothingDetail = onClothingDetail,
                            paddingValues = PaddingValues(0.dp)
                        )
                        2 -> LobsterScreen(viewModel = hiltViewModel<LobsterViewModel>(), paddingValues = PaddingValues(0.dp))
                        3 -> SettingsScreen(viewModel = hiltViewModel<SettingsViewModel>(), paddingValues = PaddingValues(0.dp))
                    }
                }
            }
        }
    }
}
