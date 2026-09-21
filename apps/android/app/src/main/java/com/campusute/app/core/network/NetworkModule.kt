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
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The AI gateway waits up to 60s for the model, so the shared 20s client can never
 * deliver a slow answer — it surfaces as a false "offline". AI calls get their own
 * client whose read budget exceeds the gateway's.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AiNetwork

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

    private val apiJson = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(
            apiJson.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    @Provides
    @Singleton
    fun campusApi(retrofit: Retrofit): CampusApi = retrofit.create(CampusApi::class.java)

    @Provides
    @Singleton
    @AiNetwork
    fun aiOkHttp(client: OkHttpClient): OkHttpClient = client.newBuilder()
        .readTimeout(75, TimeUnit.SECONDS)
        .callTimeout(80, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @AiNetwork
    fun aiRetrofit(baseUrl: String, @AiNetwork client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(
            apiJson.asConverterFactory("application/json".toMediaType()),
        )
        .build()

    @Provides
    @Singleton
    @AiNetwork
    fun aiCampusApi(@AiNetwork retrofit: Retrofit): CampusApi = retrofit.create(CampusApi::class.java)
}
