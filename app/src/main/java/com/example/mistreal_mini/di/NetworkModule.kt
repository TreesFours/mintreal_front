package com.example.mistreal_mini.di

import com.example.mistreal_mini.data.api.AiApiService
import com.example.mistreal_mini.data.api.AiProviderApiService
import com.example.mistreal_mini.data.api.ErrorNotifyInterceptor
import com.example.mistreal_mini.data.api.InfoApiService
import com.example.mistreal_mini.data.repository.AuthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
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
            level = if (com.example.mistreal_mini.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authRepository: AuthRepository,
        errorNotifyInterceptor: ErrorNotifyInterceptor
    ): OkHttpClient {
        val certificatePinner = CertificatePinner.Builder()
            .add("mistreal-backend.onrender.com", "sha256/fizfE9JVlzlRpIEx7epXfqW9enrbLvwF/LU26XTPEG4=")
            .add("mistreal-backend.onrender.com", "sha256/kldp6NNEd8wsugYyyIYFsi1yIMCED3hZbSR8ZFsa/A4=")
            .add("mistreal-backend.onrender.com", "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c=")
            .build()

        return OkHttpClient.Builder()
            .certificatePinner(certificatePinner)
            .addInterceptor(loggingInterceptor)
            // Outermost of the two custom interceptors — sees the FINAL
            // response (after the auth-retry-on-401 interceptor below has
            // already done its one retry), so a real failure only notifies
            // once, not once per retry attempt.
            .addInterceptor(errorNotifyInterceptor)
            .addInterceptor(Interceptor { chain ->
                fun requestWithToken(token: String?) = if (token != null) {
                    chain.request().newBuilder().addHeader("Authorization", "Bearer $token").build()
                } else {
                    chain.request()
                }

                val token = runBlocking(Dispatchers.IO) { authRepository.getIdToken() }
                val response = chain.proceed(requestWithToken(token))

                // Cached token was rejected (expired/revoked server-side before
                // our local expiry check caught it) — force exactly one refresh
                // and retry, rather than caching staleness indefinitely.
                if (response.code == 401 && token != null) {
                    response.close()
                    val freshToken = runBlocking(Dispatchers.IO) { authRepository.getIdToken(forceRefresh = true) }
                    chain.proceed(requestWithToken(freshToken))
                } else {
                    response
                }
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

    @Provides
    @Singleton
    fun provideAiProviderApiService(retrofit: Retrofit): AiProviderApiService {
        return retrofit.create(AiProviderApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideMarketApiService(retrofit: Retrofit): com.example.mistreal_mini.data.api.MarketApiService {
        return retrofit.create(com.example.mistreal_mini.data.api.MarketApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideReviewApiService(retrofit: Retrofit): com.example.mistreal_mini.data.api.ReviewApiService {
        return retrofit.create(com.example.mistreal_mini.data.api.ReviewApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCloudStorageApiService(retrofit: Retrofit): com.example.mistreal_mini.data.api.CloudStorageApiService {
        return retrofit.create(com.example.mistreal_mini.data.api.CloudStorageApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideNeedsApiService(retrofit: Retrofit): com.example.mistreal_mini.data.api.NeedsApiService {
        return retrofit.create(com.example.mistreal_mini.data.api.NeedsApiService::class.java)
    }
}
