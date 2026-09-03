package com.family.gatelock

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "gatelock_settings")

/**
 * Тип Home Assistant entity, яким керуємо.
 * SWITCH  -> switch.turn_on / switch.turn_off (реле, що саме вимикається автоматично
 *            після паузи, налаштованої в Zigbee2MQTT / автоматизації HA)
 * LOCK    -> lock.open (для entity домену "lock" з підтримкою миттєвого відкриття)
 * SCRIPT  -> script.turn_on (якщо в HA зроблений окремий script/scene "відкрити хвіртку")
 */
enum class EntityDomain { SWITCH, LOCK, SCRIPT }

data class HaSettings(
    val baseUrl: String = "",       // напр. http://192.168.1.109:8123
    val token: String = "",         // Long-Lived Access Token з профілю HA
    val entityId: String = "",      // напр. switch.gate_relay
    val domain: EntityDomain = EntityDomain.SWITCH
)

private object Keys {
    val BASE_URL = stringPreferencesKey("base_url")
    val TOKEN = stringPreferencesKey("token")
    val ENTITY_ID = stringPreferencesKey("entity_id")
    val DOMAIN = stringPreferencesKey("domain")
}

class SettingsStore(private val context: Context) {

    val settingsFlow: Flow<HaSettings> = context.dataStore.data.map { prefs ->
        HaSettings(
            baseUrl = prefs[Keys.BASE_URL] ?: "",
            token = prefs[Keys.TOKEN] ?: "",
            entityId = prefs[Keys.ENTITY_ID] ?: "",
            domain = runCatching {
                EntityDomain.valueOf(prefs[Keys.DOMAIN] ?: EntityDomain.SWITCH.name)
            }.getOrDefault(EntityDomain.SWITCH)
        )
    }

    suspend fun save(settings: HaSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BASE_URL] = settings.baseUrl.trim().trimEnd('/')
            prefs[Keys.TOKEN] = settings.token.trim()
            prefs[Keys.ENTITY_ID] = settings.entityId.trim()
            prefs[Keys.DOMAIN] = settings.domain.name
        }
    }
}
