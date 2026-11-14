package com.example.pguard.core.network

import android.content.Context
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.api.AuthApiService
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {

    // LAN IP of your development Mac (accessible by both physical phones on Wi-Fi and emulators):
    const val LAN_BASE_URL = "http://192.168.100.80:8080/"
    const val EMULATOR_BASE_URL = "http://10.0.2.2:8080/"

    private var _baseUrl: String = LAN_BASE_URL

    var baseUrl: String
        get() = _baseUrl
        set(value) {
            _baseUrl = if (value.endsWith("/")) value else "$value/"
            _apiService = null
        }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        encodeDefaults = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private var _okHttpClient: OkHttpClient? = null
    private var _apiService: AuthApiService? = null

    fun getOkHttpClient(authDataStore: AuthDataStore? = null): OkHttpClient {
        return _okHttpClient ?: run {
            val builder = OkHttpClient.Builder()
                .addInterceptor(loggingInterceptor)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)

            if (authDataStore != null) {
                builder.addInterceptor(AuthInterceptor(authDataStore))
            }

            val client = builder.build()
            _okHttpClient = client
            client
        }
    }

    fun getAuthApiService(context: Context? = null): AuthApiService {
        return _apiService ?: run {
            val authDataStore = context?.let { AuthDataStore(it.applicationContext) }
            val client = getOkHttpClient(authDataStore)
            val contentType = "application/json".toMediaType()

            val retrofit = Retrofit.Builder()
                .baseUrl(_baseUrl)
                .client(client)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()

            val service = retrofit.create(AuthApiService::class.java)
            _apiService = service
            service
        }
    }
}
