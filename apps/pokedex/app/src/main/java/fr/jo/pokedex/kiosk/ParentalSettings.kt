package fr.jo.pokedex.kiosk

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.jo.pokedex.speech.RecognitionMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

interface RecognitionSettings {
    val recognitionMode: Flow<RecognitionMode>

    suspend fun setRecognitionMode(mode: RecognitionMode)
}

private val Context.parentalStore: DataStore<Preferences> by preferencesDataStore(name = "parental")

class ParentalSettings(context: Context) : RecognitionSettings {

    private val store = context.applicationContext.parentalStore

    override val recognitionMode: Flow<RecognitionMode> = store.data.map { prefs ->
        prefs[KEY_MODE]?.let { runCatching { RecognitionMode.valueOf(it) }.getOrNull() } ?: RecognitionMode.GRAMMAR
    }

    val isDefaultPin: Flow<Boolean> = store.data.map { it[KEY_HASH] == null }

    val micPermissionAsked: Flow<Boolean> = store.data.map { it[KEY_MIC_ASKED] ?: false }

    override suspend fun setRecognitionMode(mode: RecognitionMode) {
        store.edit { it[KEY_MODE] = mode.name }
    }

    suspend fun setMicPermissionAsked() {
        store.edit { it[KEY_MIC_ASKED] = true }
    }

    suspend fun checkPin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val prefs = store.data.first()
        val hash = prefs[KEY_HASH]
        val salt = prefs[KEY_SALT]
        if (hash == null || salt == null) {
            pin == DEFAULT_PIN
        } else {
            PinHasher.matches(pin, decode(salt), decode(hash))
        }
    }

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val (salt, hash) = withContext(Dispatchers.Default) {
            val salt = PinHasher.newSalt()
            salt to PinHasher.hash(pin, salt)
        }
        store.edit {
            it[KEY_SALT] = encode(salt)
            it[KEY_HASH] = encode(hash)
        }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        const val DEFAULT_PIN = "1234"
        private val KEY_MODE = stringPreferencesKey("recognition_mode")
        private val KEY_HASH = stringPreferencesKey("pin_hash")
        private val KEY_SALT = stringPreferencesKey("pin_salt")
        private val KEY_MIC_ASKED = booleanPreferencesKey("mic_permission_asked")
    }
}
