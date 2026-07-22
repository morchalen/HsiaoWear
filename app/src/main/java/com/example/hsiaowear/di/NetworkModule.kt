package com.example.hsiaowear.di

import com.example.hsiaowear.network.ApiProviderRegistry
import com.example.hsiaowear.network.DynamicBaseUrlInterceptor
import com.example.hsiaowear.network.MeituApi
import com.example.hsiaowear.network.MeituInterceptor
import com.example.hsiaowear.network.TryOnApi
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

    // ==================== 原有的 API 客户端 ====================

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

    // ==================== 美图开放平台抠图 API 客户端 ====================

    @Provides
    @Singleton
    @MeituOkHttpClient
    fun provideMeituOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        return OkHttpClient.Builder()
            .addInterceptor(MeituInterceptor(
                accessKey = "63f447c4d33f4391b4be097d6fcea323",
                secretKey = "8e4c09bfc53a44febd97e639562f4514"
            ))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    @MeituRetrofit
    fun provideMeituRetrofit(@MeituOkHttpClient meituOkHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://openapi.meitu.com/")
            .client(meituOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideMeituApi(@MeituRetrofit meituRetrofit: Retrofit): MeituApi {
        return meituRetrofit.create(MeituApi::class.java)
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