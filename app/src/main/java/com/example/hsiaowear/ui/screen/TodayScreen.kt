package com.example.hsiaowear.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.hsiaowear.R
import com.example.hsiaowear.data.local.ClothingEntity
import com.example.hsiaowear.data.repository.ClothingRecommendationItem
import com.example.hsiaowear.network.OutfitRecommendation
import com.example.hsiaowear.network.WeatherInfo
import com.example.hsiaowear.ui.components.colorOptions
import com.example.hsiaowear.ui.theme.LocalAppShape
import com.example.hsiaowear.ui.theme.LocalAppSpacing
import com.example.hsiaowear.util.ImageUtils
import com.example.hsiaowear.viewmodel.TodayViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TodayScreen(viewModel: TodayViewModel, paddingValues: PaddingValues) {
    val weather by viewModel.weather.collectAsState()
    val recommendation by viewModel.recommendation.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isMatting by viewModel.isMatting.collectAsState()
    val bodyImageUrl by viewModel.bodyImageUrl.collectAsState()
    val errorMessage by viewModel.error.collectAsState()
    val isTryOnLoading by viewModel.isTryOnLoading.collectAsState()
    val tryOnResultUrl by viewModel.tryOnResultUrl.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    var upperClothing by remember { mutableStateOf<ClothingRecommendationItem?>(null) }
    var lowerClothing by remember { mutableStateOf<ClothingRecommendationItem?>(null) }
    var shoesClothing by remember { mutableStateOf<ClothingRecommendationItem?>(null) }

    if (recommendation != null) {
        loadClothingDetails(viewModel, recommendation!!,
            onUpperLoaded = { upperClothing = it },
            onLowerLoaded = { lowerClothing = it },
            onShoesLoaded = { shoesClothing = it }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                WeatherCard(weather = weather, onRefresh = { viewModel.fetchWeather() })
            }

            item {
                OutfitRecommendationSection(
                    bodyImageUrl = bodyImageUrl,
                    isMatting = isMatting,
                    onBodyImageUpdate = { viewModel.setBodyImageUrl(it) },
                    upperClothing = upperClothing,
                    lowerClothing = lowerClothing,
                    shoesClothing = shoesClothing,
                    isLoading = isLoading,
                    isTryOnLoading = isTryOnLoading,
                    isShowingTryOnResult = tryOnResultUrl != null,
                    onRefresh = { viewModel.fetchRecommendation() },
                    onTryOn = { upper, lower, shoes ->
                        viewModel.performTryOn(bodyImageUrl ?: "", upper, lower, shoes)
                    },
                    onRestoreBodyImage = { viewModel.restoreBodyImage() },
                    onUpperChanged = { upperClothing = it },
                    onLowerChanged = { lowerClothing = it },
                    onShoesChanged = { shoesClothing = it },
                    allClothes = viewModel.allClothes.collectAsState().value
                )
            }

            item {
                RecommendationReasonCard(reason = recommendation?.reason)
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun WeatherCard(weather: WeatherInfo?, onRefresh: () -> Unit) {
    val shapes = LocalAppShape.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(shapes.button)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = getWeatherIcon(weather?.description ?: ""),
                        fontSize = 28.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "${weather?.temperature ?: "--"}°C",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = weather?.description ?: "获取天气中...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = weather?.city ?: "重庆",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${weather?.dateStr ?: ""} ${weather?.weekday ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.today_refresh))
            }
        }
    }
}

@Composable
private fun OutfitRecommendationSection(
    bodyImageUrl: String?,
    isMatting: Boolean = false,
    onBodyImageUpdate: (String?) -> Unit,
    upperClothing: ClothingRecommendationItem?,
    lowerClothing: ClothingRecommendationItem?,
    shoesClothing: ClothingRecommendationItem?,
    isLoading: Boolean,
    isTryOnLoading: Boolean = false,
    isShowingTryOnResult: Boolean = false,
    onRefresh: () -> Unit,
    onTryOn: (ClothingRecommendationItem?, ClothingRecommendationItem?, ClothingRecommendationItem?) -> Unit,
    onRestoreBodyImage: () -> Unit,
    onUpperChanged: (ClothingRecommendationItem?) -> Unit,
    onLowerChanged: (ClothingRecommendationItem?) -> Unit,
    onShoesChanged: (ClothingRecommendationItem?) -> Unit,
    allClothes: List<ClothingEntity>
) {
    val shapes = LocalAppShape.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.today_outfit_recommendation),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // AI试衣按钮 - 只在有身体图片并且不加载时显示
                    if (!bodyImageUrl.isNullOrBlank() && !isTryOnLoading) {
                        Button(
                            onClick = {
                                onTryOn(upperClothing, lowerClothing, shoesClothing)
                            },
                            modifier = Modifier.height(36.dp),
                            shape = shapes.pill,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                Icons.Filled.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AI试衣",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    // 试衣加载中 - 显示进度
                    if (isTryOnLoading) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.today_refresh))
                    }
                }
            }

            if (isLoading || isTryOnLoading) {
                Box(modifier = Modifier.height(280.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isTryOnLoading) "AI试衣处理中...\n（约15-30秒）" else "加载中...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BodyImageColumn(
                        bodyImageUrl = bodyImageUrl,
                        isMatting = isMatting,
                        isShowingTryOnResult = isShowingTryOnResult,
                        onBodyImageUpdate = onBodyImageUpdate,
                        onRestore = onRestoreBodyImage
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        ClothingRow(
                            title = "上装",
                            icon = "👕",
                            clothing = upperClothing,
                            onClick = { onUpperChanged(it) },
                            allClothes = allClothes.filter { c ->
                                c.category == "上衣" || c.category == "外套" || c.category == "连衣裙"
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ClothingRow(
                            title = "下装",
                            icon = "👖",
                            clothing = lowerClothing,
                            onClick = { onLowerChanged(it) },
                            allClothes = allClothes.filter { c ->
                                c.category == "下装"
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ClothingRow(
                            title = "鞋子",
                            icon = "👟",
                            clothing = shoesClothing,
                            onClick = { onShoesChanged(it) },
                            allClothes = allClothes.filter { c ->
                                c.category == "鞋"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyImageColumn(
    bodyImageUrl: String?,
    isMatting: Boolean = false,
    isShowingTryOnResult: Boolean = false,
    onBodyImageUpdate: (String?) -> Unit,
    onRestore: () -> Unit
) {
    val context = LocalContext.current
    var showGalleryPicker by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val filePath = ImageUtils.copyImageToPrivateDir(context, it)
            if (filePath != null) {
                onBodyImageUpdate(filePath)
            }
        }
        showGalleryPicker = false
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .aspectRatio(0.65f)
                .clip(LocalAppShape.current.button)
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (bodyImageUrl.isNullOrBlank()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "上传照片",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isMatting) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "抠图中...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                AsyncImage(
                    model = bodyImageUrl,
                    contentDescription = "用户身体照片",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { showGalleryPicker = true },
            modifier = Modifier.width(100.dp),
            shape = LocalAppShape.current.pill,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(
                text = "上传",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "AI自动抠图优化",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(100.dp)
        )

        // 试衣结果恢复按钮
        if (isShowingTryOnResult) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier.width(100.dp),
                shape = LocalAppShape.current.pill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = "恢复默认",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }

    if (showGalleryPicker) {
        galleryLauncher.launch("image/*")
    }
}

/**
 * 可点击的衣物行 - 点击弹出衣橱选择弹窗。
 */
@Composable
private fun ClothingRow(
    title: String,
    icon: String,
    clothing: ClothingRecommendationItem?,
    onClick: (ClothingRecommendationItem?) -> Unit,
    allClothes: List<ClothingEntity>
) {
    val shapes = LocalAppShape.current
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = icon, fontSize = 20.sp)

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (clothing?.imageUrl.isNullOrBlank()) {
                    Text(text = "👔", fontSize = 20.sp)
                } else {
                    AsyncImage(
                        model = clothing?.imageUrl,
                        contentDescription = clothing?.name,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = clothing?.name ?: "点击选择",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!clothing?.color.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(getColorByName(clothing?.color ?: ""))
                )
            }
        }
    }

    if (showDialog) {
        ClothingSelectionDialog(
            title = "选择${title}",
            clothes = allClothes,
            onSelect = { selected ->
                if (selected != null) {
                    onClick(ClothingRecommendationItem(
                        id = selected.id,
                        name = selected.name,
                        category = selected.category,
                        color = selected.color,
                        imageUrl = selected.imageUrl
                    ))
                } else {
                    onClick(null) // 取消选择
                }
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}

/**
 * 衣橱选择弹窗 - 显示衣物图片和名称，点击选择。
 */
@Composable
private fun ClothingSelectionDialog(
    title: String,
    clothes: List<ClothingEntity>,
    onSelect: (ClothingEntity?) -> Unit,
    onDismiss: () -> Unit
) {
    val shapes = LocalAppShape.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            if (clothes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "暂无该分类的衣物",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(clothes) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.medium)
                                .clickable { onSelect(item) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(shapes.small)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (item.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = item.imageUrl,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(text = "👔", fontSize = 24.sp)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (item.color.isNotBlank()) {
                                    Text(
                                        text = item.color,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (item.color.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(getColorByName(item.color))
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSelect(null) // 清除选择
                onDismiss()
            }) {
                Text(text = "清除选择")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "取消")
            }
        },
        shape = shapes.large
    )
}

@Composable
private fun RecommendationReasonCard(reason: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = LocalAppShape.current.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💡", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.today_reason),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = reason ?: stringResource(R.string.today_reason_placeholder),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun getWeatherIcon(description: String): String {
    return when {
        description.contains("晴") -> "☀️"
        description.contains("云") || description.contains("阴") -> "☁️"
        description.contains("雨") -> "🌧️"
        description.contains("雪") -> "❄️"
        description.contains("风") -> "💨"
        else -> "🌤️"
    }
}

private fun getColorByName(colorName: String): Color {
    return colorOptions.firstOrNull { it.first == colorName }?.second ?: Color.Gray
}

private fun loadClothingDetails(
    viewModel: TodayViewModel,
    recommendation: OutfitRecommendation,
    onUpperLoaded: (ClothingRecommendationItem?) -> Unit,
    onLowerLoaded: (ClothingRecommendationItem?) -> Unit,
    onShoesLoaded: (ClothingRecommendationItem?) -> Unit
) {
    GlobalScope.launch(Dispatchers.IO) {
        recommendation.upperId?.let {
            viewModel.getClothingByIdSuspend(it)?.let { item ->
                withContext(Dispatchers.Main) { onUpperLoaded(item) }
            }
        }
        recommendation.lowerId?.let {
            viewModel.getClothingByIdSuspend(it)?.let { item ->
                withContext(Dispatchers.Main) { onLowerLoaded(item) }
            }
        }
        recommendation.shoesId?.let {
            viewModel.getClothingByIdSuspend(it)?.let { item ->
                withContext(Dispatchers.Main) { onShoesLoaded(item) }
            }
        }
    }
}
