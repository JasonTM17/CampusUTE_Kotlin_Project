package com.campusute.app.core.network

import com.campusute.app.BuildConfig
import com.campusute.app.core.security.EncryptedTokenStore
import com.campusute.app.core.security.TokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {
    @Binds
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun baseUrl(): String = BuildConfig.API_BASE_URL

    @Provides
    @Singleton
    fun okHttp(
        interceptor: AuthInterceptor,
        authenticator: TokenAuthenticator,
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .authenticator(authenticator)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(
            Json { ignoreUnknownKeys = true }.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    @Provides
    @Singleton
    fun campusApi(retrofit: Retrofit): CampusApi = retrofit.create(CampusApi::class.java)
}
