package com.example.hsiaowear.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hsiaowear.data.Result
import com.example.hsiaowear.data.local.ClothingEntity
import com.example.hsiaowear.data.repository.ClothingRepository
import com.example.hsiaowear.data.repository.MattingRepository
import com.example.hsiaowear.util.ImageUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "HsiaoWear-WardrobeVM"

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    private val repository: ClothingRepository,
    private val mattingRepository: MattingRepository
) : ViewModel() {

    private val _clothes = MutableStateFlow<List<ClothingEntity>>(emptyList())
    val clothes: StateFlow<List<ClothingEntity>> = _clothes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllClothes().collect {
                _clothes.value = it
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun addClothing(name: String, category: String, color: String, imageUrl: String, aiData: String = "{}") {
        viewModelScope.launch {
            repository.insertClothing(
                ClothingEntity(
                    name = name,
                    category = category,
                    color = color,
                    imageUrl = imageUrl,
                    aiData = aiData
                )
            )
        }
    }

    fun updateClothing(id: Long, name: String, category: String, color: String, imageUrl: String = "") {
        viewModelScope.launch {
            val existing = repository.getClothingById(id)
            existing?.let {
                if (imageUrl.isNotBlank() && imageUrl != existing.imageUrl && existing.imageUrl.isNotBlank()) {
                    ImageUtils.deleteImageFile(existing.imageUrl)
                }
                repository.updateClothing(
                    it.copy(
                        name = name,
                        category = category,
                        color = color,
                        imageUrl = imageUrl.ifBlank { existing.imageUrl }
                    )
                )
            }
        }
    }

    fun deleteClothing(id: Long) {
        viewModelScope.launch {
            val clothing = repository.getClothingById(id)
            clothing?.let {
                if (it.imageUrl.isNotBlank()) {
                    ImageUtils.deleteImageFile(it.imageUrl)
                }
            }
            repository.deleteClothingById(id)
        }
    }

    /**
     * 对衣物图片进行抠图处理，返回抠图结果路径。
     * 抠图失败时返回 null，由调用方决定是否使用原图。
     */
    suspend fun processClothingImage(filePath: String): String? {
        Log.d(TAG, "processClothingImage: 开始抠图 $filePath")
        return when (val result = mattingRepository.matting(filePath)) {
            is Result.Success -> {
                Log.d(TAG, "processClothingImage: 抠图成功 ${result.data}")
                result.data
            }
            is Result.Error -> {
                Log.w(TAG, "processClothingImage: 抠图失败 ${result.message}")
                null
            }
            Result.Loading -> null
        }
    }
}