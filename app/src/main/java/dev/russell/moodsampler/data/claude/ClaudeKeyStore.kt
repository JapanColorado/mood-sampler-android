package dev.russell.moodsampler.data.claude

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// Its own file so res/xml/data_extraction_rules.xml can keep the key out of cloud backup
// and device transfer. Never part of the app's JSON backup either.
private val Context.claudeKeyStore: DataStore<Preferences> by preferencesDataStore(name = "claude_key")

/** The user's Anthropic API key, kept on the phone only. */
class ClaudeKeyStore(private val context: Context) {

    private val apiKeyPref = stringPreferencesKey("api_key")

    /** The saved key, or null when none is set. */
    val apiKey: Flow<String?> = context.claudeKeyStore.data.map { it[apiKeyPref]?.takeIf(String::isNotBlank) }

    suspend fun current(): String? = apiKey.first()

    suspend fun set(key: String) = context.claudeKeyStore.edit { it[apiKeyPref] = key.trim() }

    suspend fun clear() = context.claudeKeyStore.edit { it.remove(apiKeyPref) }
}
