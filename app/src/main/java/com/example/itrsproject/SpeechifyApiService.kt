import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class SpeechifyApiService private constructor() {

    private lateinit var appContext: Context

    // НАСТРОЙКИ СЕРВИСА — ВНУТРИ КЛАССА
    private var apiKey: String = "WTjN_NiiR70WDps5YMH6x9za5gQ2MvdDVwGiOYa4kMk="
    private var voiceId: String = "mikhail"
    private var audioFormat: String = "mp3"
    private var language: String? = "ru-RU"
    private var model: String? = "simba-multilingual"

    private val baseUrl = "https://api.sws.speechify.com"

    private val http = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun configure(
        apiKey: String? = null,
        voiceId: String? = null,
        audioFormat: String? = null,
        language: String? = null,
        model: String? = null
    ) {
        if (apiKey != null) this.apiKey = apiKey
        if (voiceId != null) this.voiceId = voiceId
        if (audioFormat != null) this.audioFormat = audioFormat
        if (language != null) this.language = language
        if (model != null) this.model = model
    }

    suspend fun synthesize(text: String): File = withContext(Dispatchers.IO) {
        if (!::appContext.isInitialized) error("SpeechifyApiService.init(context) не вызван")

        val payload = JSONObject().apply {
            put("input", text)
            put("voice_id", voiceId)
            put("audio_format", audioFormat)
            language?.takeIf { it.isNotBlank() }?.let { put("language", it) }
            model?.takeIf { it.isNotBlank() }?.let { put("model", it) }
        }

        val request = Request.Builder()
            .url("$baseUrl/v1/audio/speech")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val bodyString = http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("Speechify HTTP ${resp.code}: $body")
            body
        }

        val json = JSONObject(bodyString)
        val audioB64 = json.getString("audio_data")
        val fmt = json.optString("audio_format", audioFormat).lowercase()

        val bytes = android.util.Base64.decode(audioB64, android.util.Base64.DEFAULT)

        val ext = when (fmt) {
            "wav" -> "wav"
            "mp3" -> "mp3"
            "ogg" -> "ogg"
            "aac" -> "aac"
            "pcm" -> "pcm"
            else -> audioFormat.lowercase()
        }

        File(appContext.cacheDir, "speechify_${System.currentTimeMillis()}.$ext").apply {
            writeBytes(bytes)
        }
    }

    companion object {
        val instance: SpeechifyApiService by lazy { SpeechifyApiService() }
    }
}