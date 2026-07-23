package com.example.hsiaowear.di

import com.example.hsiaowear.network.ApiProviderRegistry
import com.example.hsiaowear.network.DynamicBaseUrlInterceptor
import com.example.hsiaowear.network.TryOnApi
import com.example.hsiaowear.network.VolcengineApi
import com.example.hsiaowear.network.VolcengineCredentialsProvider
import com.example.hsiaowear.network.VolcengineInterceptor
import com.example.hsiaowear.network.WardrobeApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ==================== 原有的 API 客户端（动态 Base URL） ====================

    @Provides
    @Singleton
    fun provideApiProviderRegistry(): ApiProviderRegistry {
        return ApiProviderRegistry()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(apiProviderRegistry: ApiProviderRegistry): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(DynamicBaseUrlInterceptor(apiProviderRegistry))
            .addInterceptor(loggingInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.example.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideWardrobeApi(retrofit: Retrofit): WardrobeApi {
        return retrofit.create(WardrobeApi::class.java)
    }

    // ==================== 火山引擎通用图像分割 API 客户端 ====================

    @Provides
    @Singleton
    @VolcengineOkHttpClient
    fun provideVolcengineOkHttpClient(
        credentialsProvider: VolcengineCredentialsProvider
    ): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(VolcengineInterceptor(
                credentialsProvider = credentialsProvider,
                region = "cn-north-1",
                service = "cv"
            ))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @VolcengineRetrofit
    fun provideVolcengineRetrofit(
        @VolcengineOkHttpClient volcengineOkHttpClient: OkHttpClient
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://visual.volcengineapi.com/")
            .client(volcengineOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideVolcengineApi(
        @VolcengineRetrofit volcengineRetrofit: Retrofit
    ): VolcengineApi {
        return volcengineRetrofit.create(VolcengineApi::class.java)
    }

    // ==================== DashScope（AI 试衣）API 客户端 ====================

    @Provides
    @Singleton
    @DashScopeOkHttpClient
    fun provideDashScopeOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @DashScopeRetrofit
    fun provideDashScopeRetrofit(@DashScopeOkHttpClient dashScopeOkHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://dashscope.aliyuncs.com/")
            .client(dashScopeOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideTryOnApi(@DashScopeRetrofit dashScopeRetrofit: Retrofit): TryOnApi {
        return dashScopeRetrofit.create(TryOnApi::class.java)
    }
}
