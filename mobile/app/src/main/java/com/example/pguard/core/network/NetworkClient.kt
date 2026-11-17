package com.example.pguard.core.network

import android.content.Context
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.api.AccountabilityApiService
import com.example.pguard.core.network.api.AuthApiService
import com.example.pguard.core.network.api.BlocklistApiService
import com.example.pguard.core.network.api.HabitsApiService
import com.example.pguard.core.network.api.SettingsApiService
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
            _okHttpClient = null
            _hasAuthSetup = false
            _retrofit = null
            _authApiService = null
            _settingsApiService = null
            _blocklistApiService = null
            _habitsApiService = null
            _accountabilityApiService = null
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
    private var _hasAuthSetup: Boolean = false
    private var _retrofit: Retrofit? = null
    private var _authApiService: AuthApiService? = null
    private var _settingsApiService: SettingsApiService? = null
    private var _blocklistApiService: BlocklistApiService? = null
    private var _habitsApiService: HabitsApiService? = null
    private var _accountabilityApiService: AccountabilityApiService? = null

    fun getOkHttpClient(authDataStore: AuthDataStore? = null): OkHttpClient {
        if (_okHttpClient != null && (_hasAuthSetup || authDataStore == null)) {
            return _okHttpClient!!
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)

        if (authDataStore != null) {
            builder.addInterceptor(AuthInterceptor(authDataStore))
            builder.authenticator(TokenAuthenticator(authDataStore, { _baseUrl }, json))
            _hasAuthSetup = true
        }

        val client = builder.build()
        _okHttpClient = client
        return client
    }

    fun getRetrofit(context: Context? = null): Retrofit {
        val authDataStore = context?.let { AuthDataStore(it.applicationContext) }
        val client = getOkHttpClient(authDataStore)
        if (_retrofit != null && (_hasAuthSetup || authDataStore == null)) {
            return _retrofit!!
        }
        val contentType = "application/json".toMediaType()

        val retrofit = Retrofit.Builder()
            .baseUrl(_baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()

        _retrofit = retrofit
        return retrofit
    }

    fun getAuthApiService(context: Context? = null): AuthApiService {
        return _authApiService ?: run {
            val service = getRetrofit(context).create(AuthApiService::class.java)
            _authApiService = service
            service
        }
    }

    fun getSettingsApiService(context: Context? = null): SettingsApiService {
        return _settingsApiService ?: run {
            val service = getRetrofit(context).create(SettingsApiService::class.java)
            _settingsApiService = service
            service
        }
    }

    fun getBlocklistApiService(context: Context? = null): BlocklistApiService {
        return _blocklistApiService ?: run {
            val service = getRetrofit(context).create(BlocklistApiService::class.java)
            _blocklistApiService = service
            service
        }
    }

    fun getHabitsApiService(context: Context? = null): HabitsApiService {
        return _habitsApiService ?: run {
            val service = getRetrofit(context).create(HabitsApiService::class.java)
            _habitsApiService = service
            service
        }
    }

    fun getAccountabilityApiService(context: Context? = null): AccountabilityApiService {
        return _accountabilityApiService ?: run {
            val service = getRetrofit(context).create(AccountabilityApiService::class.java)
            _accountabilityApiService = service
            service
        }
    }
}
