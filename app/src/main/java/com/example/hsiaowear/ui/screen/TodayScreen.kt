package com.example.hsiaowear.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
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
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch

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
    val tryOnStep by viewModel.tryOnStep.collectAsState()
    val isAiRecommending by viewModel.isAiRecommending.collectAsState()
    val allClothes by viewModel.allClothes.collectAsState()
    val tryOnHistory by viewModel.tryOnHistory.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // 首次加载时获取天气
    LaunchedEffect(Unit) {
        viewModel.fetchWeather()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Long)
        }
    }

    var upperClothing by remember { mutableStateOf<ClothingRecommendationItem?>(null) }
    var lowerClothing by remember { mutableStateOf<ClothingRecommendationItem?>(null) }
    var shoesClothing by remember { mutableStateOf<ClothingRecommendationItem?>(null) }

    val aiUpperClothing by viewModel.aiUpperClothing.collectAsState()
    val aiLowerClothing by viewModel.aiLowerClothing.collectAsState()
    val aiShoesClothing by viewModel.aiShoesClothing.collectAsState()

    // ViewModel 自动生成的默认随机推荐（衣橱加载时自动设置）
    val defaultUpper by viewModel.defaultUpperClothing.collectAsState()
    val defaultLower by viewModel.defaultLowerClothing.collectAsState()
    val defaultShoes by viewModel.defaultShoesClothing.collectAsState()

    // 优先级：AI 推荐 > 用户手动选择 > ViewModel 默认随机推荐
    val displayUpper = aiUpperClothing ?: upperClothing ?: defaultUpper
    val displayLower = aiLowerClothing ?: lowerClothing ?: defaultLower
    val displayShoes = aiShoesClothing ?: shoesClothing ?: defaultShoes

    // 当 ViewModel 生成默认推荐且用户尚未手动选择时，同步到本地选择状态
    LaunchedEffect(defaultUpper) {
        if (upperClothing == null && defaultUpper != null) {
            upperClothing = defaultUpper
        }
    }
    LaunchedEffect(defaultLower) {
        if (lowerClothing == null && defaultLower != null) {
            lowerClothing = defaultLower
        }
    }
    LaunchedEffect(defaultShoes) {
        if (shoesClothing == null && defaultShoes != null) {
            shoesClothing = defaultShoes
        }
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
                WeatherCard(weather = weather)
            }

            item {
                OutfitRecommendationSection(
                    bodyImageUrl = bodyImageUrl,
                    isMatting = isMatting,
                    onBodyImageUpdate = { viewModel.setBodyImageUrl(it) },
                    upperClothing = displayUpper,
                    lowerClothing = displayLower,
                    shoesClothing = displayShoes,
                    isLoading = isLoading,
                    isAiRecommending = isAiRecommending,
                    isTryOnLoading = isTryOnLoading,
                    tryOnStep = tryOnStep,
                    isShowingTryOnResult = tryOnResultUrl != null,
                    onRefresh = { viewModel.fetchRecommendation() },
                    onAiRecommend = { viewModel.performAiRecommendation() },
                    onTryOn = { upper, lower, shoes ->
                        viewModel.performTryOn(bodyImageUrl ?: "", upper, lower, shoes)
                    },
                    onRestoreBodyImage = { viewModel.restoreBodyImage() },
                    onMatting = { viewModel.performMatting() },
                    onUpperChanged = { upperClothing = it },
                    onLowerChanged = { lowerClothing = it },
                    onShoesChanged = { shoesClothing = it },
                    allClothes = allClothes
                )
            }

            // 推荐理由 - 有内容时才显示
            if (!recommendation?.reason.isNullOrBlank()) {
                item {
                    RecommendationReasonCard(reason = recommendation!!.reason)
                }
            }

            // 试衣历史记录 - 非空时显示
            if (tryOnHistory.isNotEmpty()) {
                item {
                    TryOnHistorySection(
                        history = tryOnHistory,
                        onClearAll = { viewModel.clearTryOnHistory() }
                    )
                }
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
private fun WeatherCard(weather: WeatherInfo?) {
    val shapes = LocalAppShape.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一列：天气图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(shapes.button)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getWeatherIcon(weather?.description ?: ""),
                    fontSize = 24.sp
                )
            }

            // 第二列：温度 + 天气描述
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

            Spacer(modifier = Modifier.weight(1f))

            // 第三列：城市 + 日期周几
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
    isAiRecommending: Boolean = false,
    isTryOnLoading: Boolean = false,
    tryOnStep: String? = null,
    isShowingTryOnResult: Boolean = false,
    onRefresh: () -> Unit,
    onAiRecommend: () -> Unit = {},
    onTryOn: (ClothingRecommendationItem?, ClothingRecommendationItem?, ClothingRecommendationItem?) -> Unit,
    onRestoreBodyImage: () -> Unit,
    onMatting: () -> Unit,
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
            android.util.Log.d("HsiaoWear-TodayUI", "=== OutfitRecommendationSection 渲染 ===")
            android.util.Log.d("HsiaoWear-TodayUI", "allClothes总数=${allClothes.size}, " +
                    "upperClothing=${upperClothing?.name ?: "null"}, " +
                    "lowerClothing=${lowerClothing?.name ?: "null"}, " +
                    "shoesClothing=${shoesClothing?.name ?: "null"}")

            // 第一行：标题 + AI按钮
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
                    Button(
                        onClick = onAiRecommend,
                        enabled = !isAiRecommending,
                        modifier = Modifier.height(36.dp),
                        shape = shapes.pill,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isAiRecommending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                Icons.Filled.SmartToy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI推荐",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
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
                    }
                    if (isTryOnLoading) {
                        Spacer(modifier = Modifier.width(4.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 第二行：模特图 + 三行衣物推荐（固定高度，确保平均分配）
            if (isLoading || isTryOnLoading || isAiRecommending) {
                Box(modifier = Modifier.height(300.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                isAiRecommending -> "AI 正在分析穿搭...\n（获取天气 + 衣橱 + 推荐理由）"
                                isTryOnLoading -> (tryOnStep ?: "AI试衣处理中...\n（约15-30秒）")
                                else -> "加载中..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // 固定高度 300dp，确保衣物行一定可见且平均分配
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BodyImageColumn(
                        bodyImageUrl = bodyImageUrl,
                        isMatting = isMatting,
                        isShowingTryOnResult = isShowingTryOnResult,
                        onBodyImageUpdate = onBodyImageUpdate,
                        onRestore = onRestoreBodyImage,
                        onMatting = onMatting
                    )

                    // 衣物推荐列：三行平均分配高度
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClothingRow(
                            modifier = Modifier.weight(1f),
                            title = "上装",
                            icon = "",
                            clothing = upperClothing,
                            onClick = { onUpperChanged(it) },
                            allClothes = allClothes.filter { c ->
                                c.category == "上装"
                            }
                        )
                        ClothingRow(
                            modifier = Modifier.weight(1f),
                            title = "下装",
                            icon = "",
                            clothing = lowerClothing,
                            onClick = { onLowerChanged(it) },
                            allClothes = allClothes.filter { c ->
                                c.category == "下装"
                            }
                        )
                        ClothingRow(
                            modifier = Modifier.weight(1f),
                            title = "鞋子",
                            icon = "",
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
    onRestore: () -> Unit,
    onMatting: () -> Unit
) {
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val filePath = ImageUtils.copyImageToPrivateDir(context, it)
            if (filePath != null) {
                onBodyImageUpdate(filePath)
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxHeight()
    ) {
        // 整个模特图区域可点击上传
        Box(
            modifier = Modifier
                .width(105.dp)
                .weight(1f)
                .clip(LocalAppShape.current.button)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { galleryLauncher.launch("image/*") },
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
                        text = "点击上传",
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
                // 有图时右上角显示换图提示
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = "更换照片",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "自动抠图优化",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(105.dp)
        )

        // 试衣结果恢复按钮
        if (isShowingTryOnResult) {
            Spacer(modifier = Modifier.height(6.dp))
            Button(
                onClick = onRestore,
                modifier = Modifier.width(105.dp),
                shape = LocalAppShape.current.pill,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Text(
                    text = "恢复默认",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * 可点击的衣物行 - 点击弹出衣橱选择弹窗。
 */
@Composable
private fun ClothingRow(
    modifier: Modifier = Modifier,
    title: String,
    icon: String,
    clothing: ClothingRecommendationItem?,
    onClick: (ClothingRecommendationItem?) -> Unit,
    allClothes: List<ClothingEntity>
) {
    val shapes = LocalAppShape.current
    var showDialog by remember { mutableStateOf(false) }

    android.util.Log.d("HsiaoWear-TodayUI", "ClothingRow渲染: title=$title, clothing=${clothing?.name ?: "null"}, 该分类衣物数=${allClothes.size}")

    // 根据衣橱状态决定提示文案
    val displayText = when {
        clothing != null -> clothing.name
        allClothes.isEmpty() -> "暂无$title，去添加"
        else -> "点击选择"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        shape = shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .padding(10.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = icon, fontSize = 16.sp)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (clothing?.imageUrl.isNullOrBlank()) {
                    Text(text = "👔", fontSize = 18.sp)
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
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            if (!clothing?.color.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
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

/**
 * 试衣历史记录 - 每行最多 4 张图片，一键清空。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TryOnHistorySection(
    history: List<String>,
    onClearAll: () -> Unit
) {
    val shapes = LocalAppShape.current
    var previewImagePath by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shapes.card,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "试衣记录",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "清空",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                history.takeLast(20).forEach { imagePath ->
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onClick = { previewImagePath = imagePath }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = imagePath,
                            contentDescription = "试衣结果",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            if (history.size > 20) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "仅显示最近 20 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // 图片预览弹窗
    previewImagePath?.let { path ->
        Dialog(onDismissRequest = { previewImagePath = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { previewImagePath = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = path,
                    contentDescription = "试衣结果预览",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
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
