package com.example.pguard.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.pguard.core.network.model.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore(name = "pguard_auth_prefs")

class AuthDataStore(private val context: Context) {

    private object PreferencesKeys {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        val USER_ID = longPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val USER_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        val USER_AVATAR_URL = stringPreferencesKey("user_avatar_url")
    }

    private val dataStore = context.authDataStore

    val accessTokenFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN]
        }

    val refreshTokenFlow: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            preferences[PreferencesKeys.REFRESH_TOKEN]
        }

    val isLoggedInFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            !preferences[PreferencesKeys.ACCESS_TOKEN].isNullOrBlank()
        }

    val userFlow: Flow<UserDto?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences ->
            val id = preferences[PreferencesKeys.USER_ID] ?: return@map null
            val email = preferences[PreferencesKeys.USER_EMAIL] ?: return@map null
            UserDto(
                id = id,
                email = email,
                displayName = preferences[PreferencesKeys.USER_DISPLAY_NAME],
                avatarUrl = preferences[PreferencesKeys.USER_AVATAR_URL]
            )
        }

    suspend fun saveSession(
        accessToken: String,
        refreshToken: String,
        user: UserDto
    ) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
            preferences[PreferencesKeys.REFRESH_TOKEN] = refreshToken
            preferences[PreferencesKeys.USER_ID] = user.id
            preferences[PreferencesKeys.USER_EMAIL] = user.email
            user.displayName?.let { preferences[PreferencesKeys.USER_DISPLAY_NAME] = it }
            user.avatarUrl?.let { preferences[PreferencesKeys.USER_AVATAR_URL] = it }
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCESS_TOKEN] = accessToken
            preferences[PreferencesKeys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearSession() {
        dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.ACCESS_TOKEN)
            preferences.remove(PreferencesKeys.REFRESH_TOKEN)
            preferences.remove(PreferencesKeys.USER_ID)
            preferences.remove(PreferencesKeys.USER_EMAIL)
            preferences.remove(PreferencesKeys.USER_DISPLAY_NAME)
            preferences.remove(PreferencesKeys.USER_AVATAR_URL)
        }
    }
}
