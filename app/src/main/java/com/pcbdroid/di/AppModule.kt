package com.pcbdroid.di

import android.content.Context
import com.pcbdroid.data.repository.ProjectRepository
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

// Manual Dependency Injection Container
// Menggantikan Hilt yang butuh annotation processor (ksp/kapt)
class AppContainer(context: Context) {

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(context)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }
}
