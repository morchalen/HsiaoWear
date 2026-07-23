package com.example.hsiaowear.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VolcengineOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class VolcengineRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DashScopeOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DashScopeRetrofit
