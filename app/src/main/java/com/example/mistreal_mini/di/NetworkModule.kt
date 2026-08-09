package com.example.mistreal_mini.di

import com.example.mistreal_mini.data.api.AiApiService
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.CertificatePinner
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import timber.log.Timber
import retrofit2.converter.gson.GsonConverterFactory
import java.net.InetAddress
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authRepository: AuthRepository
    ): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add("mistreal-backend.onrender.com", "sha256/fizfE9JVlzlRpIEx7epXfqW9enrbLvwF/LU26XTPEG4=")
            .add("mistreal-backend.onrender.com", "sha256/kldp6NNEd8wsugYyyIYFsi1yIMCED3hZbSR8ZFsa/A4=")
            .add("mistreal-backend.onrender.com", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
            .build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor(loggingInterceptor)
            .addInterceptor(Interceptor { chain ->
                val token = runBlocking { authRepository.getIdToken() }
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            })
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return try {
                        Dns.SYSTEM.lookup(hostname)
                    } catch (e: Exception) {
                        // Diagnostic log for the user
                        Timber.e("DNS Lookup failed for $hostname: ${e.message}")
                        throw e
                    }
                }
            })
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://mistreal-backend.onrender.com/") // I've updated this to your repo name, update if different
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAiApiService(retrofit: Retrofit): AiApiService {
        return retrofit.create(AiApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideInfoApiService(retrofit: Retrofit): InfoApiService {
        return retrofit.create(InfoApiService::class.java)
    }
}
