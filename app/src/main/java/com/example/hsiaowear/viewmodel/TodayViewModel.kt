package com.example.hsiaowear.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.local.ClothingEntity
import com.example.hsiaowear.data.repository.AIRepository
import com.example.hsiaowear.data.repository.ClothingRecommendationItem
import com.example.hsiaowear.data.repository.ClothingRepository
import com.example.hsiaowear.data.repository.MattingRepository
import com.example.hsiaowear.data.repository.TryOnGarment
import com.example.hsiaowear.data.repository.TryOnRepository
import com.example.hsiaowear.network.OutfitRecommendation
import com.example.hsiaowear.network.WeatherInfo
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
    private val tryOnRepository: TryOnRepository
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

    /** 保存试衣前的原始身体图片路径，用于恢复 */
    private var savedOriginalBodyImage: String? = null

    // ==================== 衣橱列表（用于试衣选择弹窗） ====================

    private val _allClothes = MutableStateFlow<List<ClothingEntity>>(emptyList())
    val allClothes: StateFlow<List<ClothingEntity>> = _allClothes.asStateFlow()

    init {
        loadAllClothes()
    }

    private fun loadAllClothes() {
        viewModelScope.launch {
            _allClothes.value = clothingRepository.getAllClothes().first()
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
     * 用户选择图片后调用：先显示原图，然后自动调用抠图 API，
     * 抠图下载完成后自动替换为抠图结果。
     */
    fun setBodyImageUrl(filePath: String?) {
        if (filePath.isNullOrBlank()) {
            Log.d(TAG, "setBodyImageUrl: filePath 为空，清空图片")
            _bodyImageUrl.value = null
            _tryOnResultUrl.value = null
            savedOriginalBodyImage = null
            return
        }

        Log.d(TAG, "setBodyImageUrl: 开始处理图片文件 $filePath")

        // 先把原图显示出来
        _bodyImageUrl.value = filePath
        Log.d(TAG, "setBodyImageUrl: 已显示原图")

        // 发起抠图
        viewModelScope.launch {
            _isMatting.value = true
            _error.value = null
            Log.d(TAG, "setBodyImageUrl: 开始调用抠图 API...")

            when (val result = mattingRepository.matting(filePath)) {
                is Result.Success -> {
                    Log.d(TAG, "setBodyImageUrl: 抠图成功，结果路径=${result.data}")
                    _bodyImageUrl.value = result.data
                }
                is Result.Error -> {
                    Log.w(TAG, "setBodyImageUrl: 抠图失败: ${result.message}")
                    _error.value = "抠图失败：${result.message}（已保留原图）"
                }
                Result.Loading -> {}
            }

            _isMatting.value = false
            Log.d(TAG, "setBodyImageUrl: 抠图流程结束")
        }
    }

    fun getClothingById(id: Long): ClothingRecommendationItem? {
        var result: ClothingRecommendationItem? = null
        viewModelScope.launch {
            clothingRepository.getClothingById(id)?.let {
                result = ClothingRecommendationItem(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    color = it.color,
                    imageUrl = it.imageUrl
                )
            }
        }
        return result
    }

    suspend fun getClothingByIdSuspend(id: Long): ClothingRecommendationItem? {
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
            _error.value = null

            Log.d(TAG, "performTryOn: 开始 AI 试衣...")

            when (val result = tryOnRepository.tryOn(bodyImagePath, garments)) {
                is Result.Success -> {
                    Log.d(TAG, "performTryOn: 试衣成功，结果路径=${result.data}")
                    _tryOnResultUrl.value = result.data
                    // 替换左侧图片为试衣结果
                    _bodyImageUrl.value = result.data
                }
                is Result.Error -> {
                    Log.w(TAG, "performTryOn: 试衣失败: ${result.message}")
                    _error.value = "AI试衣失败：${result.message}"
                }
                Result.Loading -> {}
            }

            _isTryOnLoading.value = false
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
