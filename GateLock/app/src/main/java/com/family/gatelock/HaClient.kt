package com.family.gatelock

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

sealed class HaResult {
    data object Success : HaResult()
    data class Error(val message: String) : HaResult()
}

class HaClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    /**
     * Викликає відповідний сервіс Home Assistant для відкриття хвіртки,
     * залежно від обраного типу entity.
     */
    suspend fun triggerOpen(settings: HaSettings): HaResult = withContext(Dispatchers.IO) {
        if (settings.baseUrl.isBlank() || settings.token.isBlank() || settings.entityId.isBlank()) {
            return@withContext HaResult.Error("Заповни налаштування: адреса, токен та entity_id")
        }

        val (service, domainPath) = when (settings.domain) {
            EntityDomain.SWITCH -> "turn_on" to "switch"
            EntityDomain.LOCK -> "open" to "lock"
            EntityDomain.SCRIPT -> "turn_on" to "script"
        }

        val url = "${settings.baseUrl}/api/services/$domainPath/$service"
        val body = """{"entity_id":"${settings.entityId}"}""".toRequestBody(jsonMedia)

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${settings.token}")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    HaResult.Success
                } else {
                    HaResult.Error("HA відповів помилкою: ${response.code}")
                }
            }
        } catch (e: Exception) {
            HaResult.Error("Немає з'єднання: ${e.message}")
        }
    }

    /** Перевірка, чи взагалі доступний сервер HA (для екрана налаштувань). */
    suspend fun testConnection(settings: HaSettings): HaResult = withContext(Dispatchers.IO) {
        if (settings.baseUrl.isBlank() || settings.token.isBlank()) {
            return@withContext HaResult.Error("Вкажи адресу сервера та токен")
        }
        val request = Request.Builder()
            .url("${settings.baseUrl}/api/")
            .addHeader("Authorization", "Bearer ${settings.token}")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) HaResult.Success
                else HaResult.Error("HA відповів помилкою: ${response.code}")
            }
        } catch (e: Exception) {
            HaResult.Error("Немає з'єднання: ${e.message}")
        }
    }
}
