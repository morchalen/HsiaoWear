package com.example.hsiaowear.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MeituOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MeituRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DashScopeOkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DashScopeRetrofit
