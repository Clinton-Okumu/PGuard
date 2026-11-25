package com.example.pguard.core.network

import android.content.Context
import com.example.pguard.BuildConfig
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

    private var _baseUrl: String = BuildConfig.API_BASE_URL

    var baseUrl: String
        @Synchronized get() = _baseUrl
        @Synchronized set(value) {
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
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
    }

    private var _okHttpClient: OkHttpClient? = null
    private var _hasAuthSetup: Boolean = false
    private var _retrofit: Retrofit? = null
    private var _authApiService: AuthApiService? = null
    private var _settingsApiService: SettingsApiService? = null
    private var _blocklistApiService: BlocklistApiService? = null
    private var _habitsApiService: HabitsApiService? = null
    private var _accountabilityApiService: AccountabilityApiService? = null

    @Synchronized
    fun getOkHttpClient(authDataStore: AuthDataStore? = null): OkHttpClient {
        if (_okHttpClient != null && (_hasAuthSetup || authDataStore == null)) {
            return _okHttpClient!!
        }
        val builder = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)

        if (authDataStore != null) {
            builder.addInterceptor(AuthInterceptor(authDataStore))
            builder.authenticator(TokenAuthenticator(authDataStore, { _baseUrl }, json))
            if (!_hasAuthSetup) {
                // Invalidate unauthenticated Retrofit & service instances so future requests use auth
                _retrofit = null
                _authApiService = null
                _settingsApiService = null
                _blocklistApiService = null
                _habitsApiService = null
                _accountabilityApiService = null
            }
            _hasAuthSetup = true
        }

        val client = builder.build()
        _okHttpClient = client
        return client
    }

    @Synchronized
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

    @Synchronized
    fun getAuthApiService(context: Context? = null): AuthApiService {
        return _authApiService ?: run {
            val service = getRetrofit(context).create(AuthApiService::class.java)
            _authApiService = service
            service
        }
    }

    @Synchronized
    fun getSettingsApiService(context: Context? = null): SettingsApiService {
        return _settingsApiService ?: run {
            val service = getRetrofit(context).create(SettingsApiService::class.java)
            _settingsApiService = service
            service
        }
    }

    @Synchronized
    fun getBlocklistApiService(context: Context? = null): BlocklistApiService {
        return _blocklistApiService ?: run {
            val service = getRetrofit(context).create(BlocklistApiService::class.java)
            _blocklistApiService = service
            service
        }
    }

    @Synchronized
    fun getHabitsApiService(context: Context? = null): HabitsApiService {
        return _habitsApiService ?: run {
            val service = getRetrofit(context).create(HabitsApiService::class.java)
            _habitsApiService = service
            service
        }
    }

    @Synchronized
    fun getAccountabilityApiService(context: Context? = null): AccountabilityApiService {
        return _accountabilityApiService ?: run {
            val service = getRetrofit(context).create(AccountabilityApiService::class.java)
            _accountabilityApiService = service
            service
        }
    }
}
