package com.moonforce.ohmyainote.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moonforce.ohmyainote.ai.api.AiSettings
import com.moonforce.ohmyainote.ai.api.ResolvedAiSettings
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.aiPreferences by preferencesDataStore("ai_settings")

class AiSettingsStore(private val context: Context) {
    private val secureDir = context.filesDir.resolve("secure")
    private val keyFile = secureDir.resolve("ai_key.gcm")

    val settings: Flow<AiSettings> = context.aiPreferences.data.map { preferences ->
        AiSettings(
            baseUrl = preferences[BASE_URL].orEmpty(),
            model = preferences[MODEL].orEmpty(),
            hasApiKey = preferences[HAS_API_KEY] == true && keyFile.isFile,
        )
    }

    val exportPressureVarying: Flow<Boolean> = context.aiPreferences.data.map { it[EXPORT_PRESSURE] ?: true }

    suspend fun save(baseUrl: String, model: String, apiKey: String?) {
        val normalizedUrl = baseUrl.trim().trimEnd('/')
        val normalizedModel = model.trim()
        require(normalizedUrl.isNotBlank() && normalizedModel.isNotBlank()) { "URL and model are required" }
        val previous = context.aiPreferences.data.first()[BASE_URL]
        if (apiKey != null) {
            require(apiKey.isNotBlank()) { "API key cannot be blank" }
            setApiKey(apiKey)
        } else {
            require(getApiKey() != null) { "API key is required" }
        }
        context.aiPreferences.edit { preferences ->
            preferences[BASE_URL] = normalizedUrl
            preferences[MODEL] = normalizedModel
            preferences[HAS_API_KEY] = true
            if (previous != normalizedUrl) preferences.remove(CONFIRMED_URL)
        }
    }

    suspend fun setExportPressureVarying(enabled: Boolean) {
        context.aiPreferences.edit { it[EXPORT_PRESSURE] = enabled }
    }

    suspend fun setApiKey(plain: String) = withContext(Dispatchers.IO) {
        require(plain.isNotBlank())
        secureDir.mkdirs()
        val cipher = Cipher.getInstance(CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(AAD)
        val ciphertextAndTag = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val bytes = cipher.iv + ciphertextAndTag
        val temporary = secureDir.resolve("ai_key.gcm.tmp")
        temporary.writeBytes(bytes)
        if (keyFile.exists() && !keyFile.delete()) error("Unable to replace encrypted API key")
        check(temporary.renameTo(keyFile)) { "Unable to persist encrypted API key" }
    }

    suspend fun getApiKey(): String? = withContext(Dispatchers.IO) {
        if (!keyFile.isFile) return@withContext null
        runCatching {
            val bytes = keyFile.readBytes()
            require(bytes.size > IV_BYTES + 16) { "Encrypted API key is truncated" }
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val encrypted = bytes.copyOfRange(IV_BYTES, bytes.size)
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.updateAAD(AAD)
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrElse {
            keyFile.delete()
            context.aiPreferences.edit { preferences -> preferences[HAS_API_KEY] = false }
            null
        }
    }

    suspend fun clearApiKey() = withContext(Dispatchers.IO) {
        keyFile.delete()
        context.aiPreferences.edit { preferences ->
            preferences[HAS_API_KEY] = false
            preferences.remove(CONFIRMED_URL)
        }
    }

    suspend fun resolvedOrNull(): ResolvedAiSettings? {
        val preferences = context.aiPreferences.data.first()
        val baseUrl = preferences[BASE_URL].orEmpty()
        val model = preferences[MODEL].orEmpty()
        val key = getApiKey().orEmpty()
        if (baseUrl.isBlank() || model.isBlank() || key.isBlank()) return null
        return ResolvedAiSettings(baseUrl, model, key)
    }

    suspend fun isConfirmedFor(baseUrl: String): Boolean =
        context.aiPreferences.data.first()[CONFIRMED_URL] == baseUrl.trim().trimEnd('/')

    suspend fun confirm(baseUrl: String) {
        context.aiPreferences.edit { it[CONFIRMED_URL] = baseUrl.trim().trimEnd('/') }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "oma_ai_key"
        const val CIPHER = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        val AAD = "com.moonforce.ohmyainote".toByteArray(Charsets.UTF_8)
        val BASE_URL = stringPreferencesKey("base_url")
        val MODEL = stringPreferencesKey("model")
        val HAS_API_KEY = booleanPreferencesKey("has_api_key")
        val CONFIRMED_URL = stringPreferencesKey("confirmed_base_url")
        val EXPORT_PRESSURE = booleanPreferencesKey("export_pressure_varying")
    }
}
