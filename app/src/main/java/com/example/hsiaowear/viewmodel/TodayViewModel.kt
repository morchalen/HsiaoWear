package com.example.hsiaowear.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.SettingsDataStore
import com.example.hsiaowear.data.local.ClothingEntity
import com.example.hsiaowear.data.repository.AIRepository
import com.example.hsiaowear.data.repository.ClothingRecommendationItem
import com.example.hsiaowear.data.repository.ClothingRepository
import com.example.hsiaowear.data.repository.MattingRepository
import com.example.hsiaowear.data.repository.TryOnGarment
import com.example.hsiaowear.data.repository.TryOnRepository
import com.example.hsiaowear.network.OutfitRecommendation
import com.example.hsiaowear.network.WeatherInfo
import com.example.hsiaowear.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HsiaoWear-TodayVM"

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val clothingRepository: ClothingRepository,
    private val mattingRepository: MattingRepository,
    private val tryOnRepository: TryOnRepository,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: StateFlow<WeatherInfo?> = _weather.asStateFlow()

    private val _recommendation = MutableStateFlow<OutfitRecommendation?>(null)
    val recommendation: StateFlow<OutfitRecommendation?> = _recommendation.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isMatting = MutableStateFlow(false)
    val isMatting: StateFlow<Boolean> = _isMatting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _bodyImageUrl = MutableStateFlow<String?>(null)
    val bodyImageUrl: StateFlow<String?> = _bodyImageUrl.asStateFlow()

    // ==================== AI 试衣状态 ====================

    private val _isTryOnLoading = MutableStateFlow(false)
    val isTryOnLoading: StateFlow<Boolean> = _isTryOnLoading.asStateFlow()

    private val _tryOnResultUrl = MutableStateFlow<String?>(null)
    val tryOnResultUrl: StateFlow<String?> = _tryOnResultUrl.asStateFlow()

    private val _tryOnStep = MutableStateFlow<String?>(null)
    val tryOnStep: StateFlow<String?> = _tryOnStep.asStateFlow()

    // ==================== AI 穿搭推荐状态 ====================

    private val _isAiRecommending = MutableStateFlow(false)
    val isAiRecommending: StateFlow<Boolean> = _isAiRecommending.asStateFlow()

    // AI 推荐的各品类衣物（由 ViewModel 直接查询 DB 填充，确保 UI 正确重组）
    private val _aiUpperClothing = MutableStateFlow<ClothingRecommendationItem?>(null)
    val aiUpperClothing: StateFlow<ClothingRecommendationItem?> = _aiUpperClothing.asStateFlow()
    private val _aiLowerClothing = MutableStateFlow<ClothingRecommendationItem?>(null)
    val aiLowerClothing: StateFlow<ClothingRecommendationItem?> = _aiLowerClothing.asStateFlow()
    private val _aiShoesClothing = MutableStateFlow<ClothingRecommendationItem?>(null)
    val aiShoesClothing: StateFlow<ClothingRecommendationItem?> = _aiShoesClothing.asStateFlow()

    /** 保存试衣前的原始身体图片路径，用于恢复 */
    private var savedOriginalBodyImage: String? = null

    // ==================== 试衣历史记录 ====================

    private val _tryOnHistory = MutableStateFlow<List<String>>(emptyList())
    val tryOnHistory: StateFlow<List<String>> = _tryOnHistory.asStateFlow()

    // ==================== 衣橱列表（用于试衣选择弹窗） ====================

    private val _allClothes = MutableStateFlow<List<ClothingEntity>>(emptyList())
    val allClothes: StateFlow<List<ClothingEntity>> = _allClothes.asStateFlow()

    // ==================== 默认随机推荐（无 AI 时的回退） ====================
    private val _defaultUpperClothing = MutableStateFlow<ClothingRecommendationItem?>(null)
    val defaultUpperClothing: StateFlow<ClothingRecommendationItem?> = _defaultUpperClothing.asStateFlow()
    private val _defaultLowerClothing = MutableStateFlow<ClothingRecommendationItem?>(null)
    val defaultLowerClothing: StateFlow<ClothingRecommendationItem?> = _defaultLowerClothing.asStateFlow()
    private val _defaultShoesClothing = MutableStateFlow<ClothingRecommendationItem?>(null)
    val defaultShoesClothing: StateFlow<ClothingRecommendationItem?> = _defaultShoesClothing.asStateFlow()

    init {
        loadAllClothes()
        loadPersistedBodyImage()
        loadTryOnHistory()
    }

    /** 加载持久化的抠图 Body 图片 */
    private fun loadPersistedBodyImage() {
        viewModelScope.launch {//标准的内部定义好的vm层协程写法，在可以被挂起的内容外面包裹一个作用域
            //下面这个是耗时操作：从DataStore文件里面获取标签为mattedBodyImagePathFlow的第一个值，如果还没有值则等待直到有值
            val path = settingsDataStore.mattedBodyImagePathFlow.first()
            if (path.isNotBlank()) {
                Log.d(TAG, "loadPersistedBodyImage: 加载持久化抠图图片 $path")
                _bodyImageUrl.value = path
            }
        }
    }

    /** 加载试衣历史记录 */
    private fun loadTryOnHistory() {
        viewModelScope.launch {
            settingsDataStore.tryOnHistoryFlow.collect { history ->
                _tryOnHistory.value = history
            }
        }
    }

    /** 持续监听衣橱数据变化，衣橱非空时自动生成随机默认推荐 */
    private fun loadAllClothes() {
        viewModelScope.launch {
            clothingRepository.getAllClothes().collect { clothes ->
                Log.d(TAG, "loadAllClothes: 衣橱数据更新, 共 ${clothes.size} 件")
                _allClothes.value = clothes

                // 衣橱非空时，自动为每类随机选一件作为默认推荐
                if (clothes.isNotEmpty()) {
                    val tops = clothes.filter { it.category == "上装" }
                    val bottoms = clothes.filter { it.category == "下装" }
                    val shoesList = clothes.filter { it.category == "鞋" }

                    Log.d(TAG, "loadAllClothes: 候选 - 上装 ${tops.size} 件, 下装 ${bottoms.size} 件, 鞋 ${shoesList.size} 件")

                    // 只在默认推荐为空（首次）时生成
                    if (_defaultUpperClothing.value == null) {
                        _defaultUpperClothing.value = tops.randomOrNull()?.let {
                            ClothingRecommendationItem(
                                id = it.id, name = it.name, category = it.category,
                                color = it.color, imageUrl = it.imageUrl
                            )
                        }
                        _defaultLowerClothing.value = bottoms.randomOrNull()?.let {
                            ClothingRecommendationItem(
                                id = it.id, name = it.name, category = it.category,
                                color = it.color, imageUrl = it.imageUrl
                            )
                        }
                        _defaultShoesClothing.value = shoesList.randomOrNull()?.let {
                            ClothingRecommendationItem(
                                id = it.id, name = it.name, category = it.category,
                                color = it.color, imageUrl = it.imageUrl
                            )
                        }

                        Log.d(TAG, "loadAllClothes: 默认推荐已设置 - 上装=${_defaultUpperClothing.value?.name ?: "无"}, " +
                                "下装=${_defaultLowerClothing.value?.name ?: "无"}, " +
                                "鞋=${_defaultShoesClothing.value?.name ?: "无"}")
                    }
                } else {
                    // 衣橱清空时，同时清空默认推荐
                    _defaultUpperClothing.value = null
                    _defaultLowerClothing.value = null
                    _defaultShoesClothing.value = null
                    Log.d(TAG, "loadAllClothes: 衣橱为空，清除默认推荐")
                }
            }
        }
    }

    fun fetchWeather(city: String = "重庆") {
        viewModelScope.launch {
            when (val result = aiRepository.getWeather(city)) {
                is Result.Success -> {
                    _weather.value = result.data
                }
                is Result.Error -> {
                    _error.value = "获取天气失败：${result.message}"
                }
                Result.Loading -> {}
            }
        }
    }

    fun fetchRecommendation() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val weatherResult = aiRepository.getWeather()
            val weather = if (weatherResult is Result.Success) {
                weatherResult.data
            } else {
                val errMsg = (weatherResult as? Result.Error)?.message ?: "未知错误"
                _error.value = "获取天气失败：$errMsg"
                _isLoading.value = false
                return@launch
            }
            _weather.value = weather

            val clothes = clothingRepository.getAllClothes().first()
            if (clothes.isEmpty()) {
                _error.value = "衣橱为空，请先添加衣服"
                _isLoading.value = false
                return@launch
            }

            val clothingItems = clothes.map {
                ClothingRecommendationItem(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    color = it.color,
                    imageUrl = it.imageUrl
                )
            }

            when (val result = aiRepository.getOutfitRecommendation(weather, clothingItems)) {
                is Result.Success -> {
                    _recommendation.value = result.data
                }
                is Result.Error -> {
                    _error.value = "获取推荐失败：${result.message}"
                }
                Result.Loading -> {}
            }

            _isLoading.value = false
        }
    }

    /**
     * 使用 AI 助手 API（纯文字）获取穿搭推荐。
     * 收集所有衣物 + 天气信息，调用 AI 返回上装、下装、鞋子各一件 + 推荐理由。
     */
    fun performAiRecommendation() {
        viewModelScope.launch {
            _isAiRecommending.value = true
            _error.value = null

            // 1. 获取天气（优先使用已缓存的，否则重新获取）
            var weather = _weather.value
            Log.d(TAG, "performAiRecommendation: 缓存天气=${weather != null}")
            if (weather == null) {
                val weatherResult = aiRepository.getWeather()
                if (weatherResult is Result.Success) {
                    weather = weatherResult.data
                    _weather.value = weather
                    Log.d(TAG, "performAiRecommendation: 重新获取天气成功: ${weather?.city}, ${weather?.temperature}°C, ${weather?.description}")
                } else {
                    val err = (weatherResult as? Result.Error)?.message ?: "未知"
                    Log.w(TAG, "performAiRecommendation: 获取天气失败: $err")
                }
            } else {
                Log.d(TAG, "performAiRecommendation: 使用缓存天气: ${weather.city}, ${weather.temperature}°C, ${weather.description}")
            }

            // 2. 获取所有衣物
            val clothes = clothingRepository.getAllClothes().first()
            if (clothes.isEmpty()) {
                Log.e(TAG, "performAiRecommendation: 衣橱为空")
                _error.value = "衣橱为空，请先添加衣服"
                _isAiRecommending.value = false
                return@launch
            }
            Log.d(TAG, "performAiRecommendation: 衣橱 ${clothes.size} 件衣服")
            clothes.forEach { c ->
                Log.d(TAG, "  衣物: id=${c.id}, name=${c.name}, category=${c.category}")
            }

            val clothingItems = clothes.map {
                ClothingRecommendationItem(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    color = it.color,
                    imageUrl = it.imageUrl
                )
            }

            val weatherInfo = weather ?: WeatherInfo(
                city = "未知",
                temperature = "--",
                description = "未知",
                humidity = "--",
                wind = "--",
                dateStr = "",
                weekday = ""
            ).also {
                Log.w(TAG, "performAiRecommendation: 天气为 null，使用默认值")
            }

            Log.d(TAG, "performAiRecommendation: 最终天气信息: city=${weatherInfo.city}, temp=${weatherInfo.temperature}, desc=${weatherInfo.description}")
            Log.d(TAG, "performAiRecommendation: 开始调用 AI 获取推荐, 衣服 ${clothingItems.size} 件")

            // 3. 调用 AI 获取推荐
            when (val result = aiRepository.getOutfitRecommendation(weatherInfo, clothingItems)) {
                is Result.Success -> {
                    _recommendation.value = result.data
                    Log.d(TAG, "AI推荐成功: ${result.data}")

                    // 同时从 DB 加载对应的衣物详情，确保 UI 状态同步
                    val rec = result.data
                    // 清除旧的选择，避免脏数据残留
                    _aiUpperClothing.value = null
                    _aiLowerClothing.value = null
                    _aiShoesClothing.value = null

                    // 直接从 DB 加载对应的衣物（在同一协程中串行执行，确保在 isAiRecommending=false 前完成）
                    val upperEntity = rec.upperId?.let { clothingRepository.getClothingById(it) }
                    _aiUpperClothing.value = upperEntity?.let {
                        ClothingRecommendationItem(it.id, it.name, it.category, it.color, it.imageUrl)
                    } ?: rec.upperId?.let { id ->
                        ClothingRecommendationItem(id, rec.upperName, "上装", rec.upperColor, "")
                    }

                    val lowerEntity = rec.lowerId?.let { clothingRepository.getClothingById(it) }
                    _aiLowerClothing.value = lowerEntity?.let {
                        ClothingRecommendationItem(it.id, it.name, it.category, it.color, it.imageUrl)
                    } ?: rec.lowerId?.let { id ->
                        ClothingRecommendationItem(id, rec.lowerName, "下装", rec.lowerColor, "")
                    }

                    val shoesEntity = rec.shoesId?.let { clothingRepository.getClothingById(it) }
                    _aiShoesClothing.value = shoesEntity?.let {
                        ClothingRecommendationItem(it.id, it.name, it.category, it.color, it.imageUrl)
                    } ?: rec.shoesId?.let { id ->
                        ClothingRecommendationItem(id, rec.shoesName, "鞋", rec.shoesColor, "")
                    }

                    Log.d(TAG, "AI推荐衣物已设置: upper=${_aiUpperClothing.value?.name}, lower=${_aiLowerClothing.value?.name}, shoes=${_aiShoesClothing.value?.name}")
                }
                is Result.Error -> {
                    _error.value = "AI推荐失败：${result.message}"
                    Log.w(TAG, "AI推荐失败: ${result.message}")
                }
                Result.Loading -> {}
            }

            _isAiRecommending.value = false
        }
    }

    /**
     * 用户选择图片后调用：仅保存原图并持久化，不再自动抠图。
     * 抠图由用户通过 UI 上的"网络抠图/本地抠图"按钮手动触发。
     */
    fun setBodyImageUrl(filePath: String?) {
        if (filePath.isNullOrBlank()) {
            Log.d(TAG, "setBodyImageUrl: filePath 为空，清空图片")
            viewModelScope.launch {
                clearPersistedBodyImage()
            }
            _bodyImageUrl.value = null
            _tryOnResultUrl.value = null
            savedOriginalBodyImage = null
            return
        }

        Log.d(TAG, "setBodyImageUrl: 上传新图片 $filePath")
        _bodyImageUrl.value = filePath
        _isMatting.value = false
        savedOriginalBodyImage = null

        // 不自动抠图，只持久化保存原图路径
        viewModelScope.launch {
            settingsDataStore.saveMattedBodyImagePath(filePath)
        }
    }

    /** 清除持久化的 Body 图片 */
    private suspend fun clearPersistedBodyImage() {
        val oldPath = settingsDataStore.mattedBodyImagePathFlow.first()
        if (oldPath.isNotBlank()) {
            ImageUtils.deleteImageFile(oldPath)
        }
        settingsDataStore.clearMattedBodyImagePath()
    }

    /**
     * 用户手动点击抠图按钮时调用。
     */
    fun performMatting() {
        val path = _bodyImageUrl.value ?: return
        if (path.isBlank()) return

        viewModelScope.launch {
            _isMatting.value = true
            _error.value = null

            when (val result = mattingRepository.matting(path)) {
                is Result.Success -> {
                    val mattedPath = result.data
                    // 删除原图（可能是之前的未抠图图片）
                    ImageUtils.deleteImageFile(path)
                    // 替换显示
                    _bodyImageUrl.value = mattedPath
                    // 持久化
                    val oldMattedPath = settingsDataStore.mattedBodyImagePathFlow.first()
                    if (oldMattedPath.isNotBlank() && oldMattedPath != mattedPath) {
                        ImageUtils.deleteImageFile(oldMattedPath)
                    }
                    settingsDataStore.saveMattedBodyImagePath(mattedPath)
                }
                is Result.Error -> {
                    _error.value = "抠图失败：${result.message}"
                }
                Result.Loading -> {}
            }

            _isMatting.value = false
        }
    }

    /**
     * 用户手动点击"本地抠图"时调用。
     */
    fun performLocalMatting() {
        val path = _bodyImageUrl.value ?: return
        if (path.isBlank()) return

        viewModelScope.launch {
            _isMatting.value = true
            _error.value = null

            when (val result = mattingRepository.mattingLocal(path)) {
                is Result.Success -> {
                    val mattedPath = result.data
                    // 删除原图
                    ImageUtils.deleteImageFile(path)
                    // 替换显示
                    _bodyImageUrl.value = mattedPath
                    // 持久化
                    val oldMattedPath = settingsDataStore.mattedBodyImagePathFlow.first()
                    if (oldMattedPath.isNotBlank() && oldMattedPath != mattedPath) {
                        ImageUtils.deleteImageFile(oldMattedPath)
                    }
                    settingsDataStore.saveMattedBodyImagePath(mattedPath)
                }
                is Result.Error -> {
                    _error.value = "本地抠图失败：${result.message}"
                }
                Result.Loading -> {}
            }

            _isMatting.value = false
        }
    }

    suspend fun getClothingById(id: Long): ClothingRecommendationItem? {
        return clothingRepository.getClothingById(id)?.let {
            ClothingRecommendationItem(
                id = it.id,
                name = it.name,
                category = it.category,
                color = it.color,
                imageUrl = it.imageUrl
            )
        }
    }

    // ==================== AI 试衣方法 ====================

    /**
     * 执行 AI 试衣。
     * @param bodyImagePath 当前身体图片路径
     * @param upperItem 可选的上装（可为 null）
     * @param lowerItem 可选的下装（可为 null）
     * @param shoesItem 可选的鞋子（可为 null，不发送到 API）
     */
    fun performTryOn(
        bodyImagePath: String,
        upperItem: ClothingRecommendationItem?,
        lowerItem: ClothingRecommendationItem?,
        shoesItem: ClothingRecommendationItem?
    ) {
        if (bodyImagePath.isBlank()) {
            _error.value = "请先上传身体照片"
            return
        }
        if (upperItem == null && lowerItem == null) {
            _error.value = "至少需要选择一件上装或下装才能试穿"
            return
        }

        // 保存当前身体图片以便恢复
        savedOriginalBodyImage = bodyImagePath

        val garments = mutableListOf<TryOnGarment>()

        upperItem?.let {
            garments.add(TryOnGarment(
                name = it.name,
                category = it.category,
                imagePath = it.imageUrl
            ))
        }

        lowerItem?.let {
            garments.add(TryOnGarment(
                name = it.name,
                category = it.category,
                imagePath = it.imageUrl
            ))
        }

        // 鞋子不上传给 aitryon API，但保留信息用于 UI 显示
        shoesItem?.let {
            garments.add(TryOnGarment(
                name = it.name,
                category = it.category,
                imagePath = it.imageUrl
            ))
        }

        viewModelScope.launch {
            _isTryOnLoading.value = true
            _tryOnStep.value = "正在初始化..."
            _error.value = null

            Log.d(TAG, "performTryOn: 开始 AI 试衣...")

            when (val result = tryOnRepository.tryOn(bodyImagePath, garments) { step ->
                _tryOnStep.value = step
                Log.d(TAG, "试衣步骤: $step")
            }) {
                is Result.Success -> {
                    Log.d(TAG, "performTryOn: 试衣成功，结果路径=${result.data}")
                    _tryOnResultUrl.value = result.data
                    // 保存到试衣历史
                    settingsDataStore.addTryOnHistoryItem(result.data)
                    // 不替换身体图片（试衣结果不保存在模特图位置）
                }
                is Result.Error -> {
                    Log.w(TAG, "performTryOn: 试衣失败: ${result.message}")
                    _error.value = "AI试衣失败：${result.message}"
                }
                Result.Loading -> {}
            }

            _isTryOnLoading.value = false
            if (_tryOnResultUrl.value == null) {
                _tryOnStep.value = null
            }
        }
    }

    /** 清空试衣历史记录 */
    fun clearTryOnHistory() {
        viewModelScope.launch {
            // 删除本地文件
            val history = _tryOnHistory.value
            for (path in history) {
                ImageUtils.deleteImageFile(path)
            }
            settingsDataStore.clearTryOnHistory()
        }
    }

    /**
     * 恢复试衣前的原始身体图片。
     */
    fun restoreBodyImage() {
        if (savedOriginalBodyImage != null) {
            _bodyImageUrl.value = savedOriginalBodyImage
        }
        _tryOnResultUrl.value = null
    }

    /**
     * 当前是否正在显示试衣结果。
     */
    fun isShowingTryOnResult(): Boolean = _tryOnResultUrl.value != null
}
