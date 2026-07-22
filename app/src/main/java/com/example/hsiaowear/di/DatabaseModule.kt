package com.example.hsiaowear.di

import android.content.Context
import androidx.room.Room
import com.example.hsiaowear.data.local.AppDatabase
import com.example.hsiaowear.data.local.ClothingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "hsiaowear_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideClothingDao(appDatabase: AppDatabase): ClothingDao {
        return appDatabase.clothingDao()
    }
}