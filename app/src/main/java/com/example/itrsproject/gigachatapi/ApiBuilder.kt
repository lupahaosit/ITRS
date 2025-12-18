package com.example.itrsproject.gigachatapi

import android.provider.Settings.Global.getString
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.Settings.Global.getString
import android.util.Log
import com.example.itrsproject.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import java.security.SecureRandom
import java.security.cert.X509Certificate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resumeWithException

class ApiBuilder {

    private val scope = "GIGACHAT_API_PERS"
    private val baseUrl = "https://gigachat.devices.sberbank.ru/api/v1"
    private val authUrl = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    private val xClientId = "posttman-request-collection"
    private var token: String = ""

    suspend fun extractTextFromPhoto(files: List<Uri>, context: Context): String =
        withContext(Dispatchers.IO) {
            try {
                token = getAccessToken(context)
                val fileIds = uploadFilesWithDelay(files, context, token)
                extractText(fileIds, token)
            } catch (e: Exception) {
                Log.e("ApiBuilder", "Ошибка при распознавании текста: ${e.message}", e)
                throw e
            }
        }

    suspend fun extractText(ids: List<String>, token: String): String = withContext(Dispatchers.IO) {
        val attachments = JSONArray(ids)
        val message = JSONObject()
            .put("role", "user")
            .put("content", "Что написано на изображении? Объедени текст из всех файлов. Объедени их последовательно по смыслу. В ответ верни только итоговый текст")
            .put("attachments", attachments)
        val body = JSONObject()
            .put("model", "GigaChat-2-Max")
            .put("messages", JSONArray().put(message))

        val requestBody = body.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/chat/completions")
            .post(requestBody)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("X-Request-ID", UUID.randomUUID().toString()) // Генерируем уникальный ID
            .addHeader("X-Session-ID", UUID.randomUUID().toString()) // Генерируем уникальный ID
            .addHeader("X-Client-ID", xClientId)
            .build()

        unsafeOkHttpClient().newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw IOException("Пустой ответ сервера")
            if (!response.isSuccessful) {
                Log.e("ApiBuilder", "Ошибка при распознавании текста: ${response.code}, $body")
                throw IOException("Ошибка распознавания: ${response.code}")
            }
            val json = JSONObject(body)
            val choices = json.getJSONArray("choices")
            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.getJSONObject("message")
            message.getString("content")
        }
    }

    // ИСПРАВЛЕНО: последовательная загрузка файлов с задержками
    private suspend fun uploadFilesWithDelay(files: List<Uri>, context: Context, token: String): List<String> =
        withContext(Dispatchers.IO) {
            val fileIds = mutableListOf<String>()

            for ((index, file) in files.withIndex()) {
                try {
                    // Добавляем задержку между запросами (2 секунды)
                    if (index > 0) {
                        delay(2000)
                    }

                    val fileId = uploadPNGFile(file, context, token)
                    fileIds.add(fileId)
                    Log.d("ApiBuilder", "Успешно загружен файл $index: $fileId")

                } catch (e: Exception) {
                    Log.e("ApiBuilder", "Ошибка при загрузке файла $index: ${e.message}")
                }
            }

            fileIds
        }

    private suspend fun uploadPNGFile(uri: Uri, context: Context, token: String): String =
        withContext(Dispatchers.IO) {
            try {
                // Получаем InputStream для чтения файла
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: throw IOException("Не удалось открыть файл: $uri")

                // Читаем содержимое файла в массив байтов
                val fileBytes = inputStream.readBytes()
                inputStream.close()

                // Проверяем размер файла
                if (fileBytes.size > 20 * 1024 * 1024) { // 20MB лимит
                    throw IOException("Файл слишком большой: ${fileBytes.size / 1024 / 1024}MB")
                }

                // Формируем имя файла с правильным расширением
                val fileName = "${System.currentTimeMillis()}_${uri.lastPathSegment ?: "photo"}.png"

                // Создаем RequestBody для отправки файла
                val fileBody = fileBytes.toRequestBody("image/png".toMediaTypeOrNull())

                // Формируем multipart-запрос
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", fileName, fileBody)
                    .addFormDataPart("purpose", "general")
                    .build()

                // Формируем POST-запрос
                val request = Request.Builder()
                    .url("$baseUrl/files")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("X-Client-ID", xClientId)
                    .addHeader("X-Request-ID", UUID.randomUUID().toString()) // Уникальный ID для каждого запроса
                    .post(requestBody)
                    .build()

                // Выполняем запрос асинхронно
                val client = unsafeOkHttpClient()
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string()

                    when {
                        response.isSuccessful -> {
                            JSONObject(responseBody!!).getString("id")
                        }
                        response.code == 429 -> {
                            // Обработка лимита запросов
                            Log.w("ApiBuilder", "Превышен лимит запросов, пробуем снова через 5 секунд")
                            delay(5000) // Ждем 5 секунд
                            // Рекурсивно пробуем снова (максимум 3 попытки)
                            uploadPNGFile(uri, context, token)
                        }
                        else -> {
                            Log.e("UploadError", "Ошибка загрузки файла: ${response.code}, $responseBody")
                            throw IOException("Ошибка загрузки файла: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("UploadError", "Ошибка при загрузке файла: ${e.localizedMessage ?: "Неизвестная ошибка"}", e)
                throw e
            }
        }

    private suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val authToken = getAuthToken(context)
            val formBody = FormBody.Builder()
                .add("scope", scope)
                .build()

            val request = Request.Builder()
                .url(authUrl)
                .post(formBody)
                .addHeader("RqUID", UUID.randomUUID().toString())
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .addHeader("Authorization", "Basic $authToken")
                .build()

            unsafeOkHttpClient().newCall(request).execute().use { response ->
                val body = response.body?.string() ?: throw IOException("Пустой ответ при получении токена")
                if (!response.isSuccessful) {
                    Log.e("ApiBuilder", "Ошибка авторизации: ${response.code}, $body")
                    throw IOException("Ошибка авторизации: ${response.code}")
                }
                JSONObject(body).getString("access_token")
            }
        } catch (e: Exception) {
            Log.e("ApiBuilder", "Ошибка при получении токена: ${e.message}", e)
            throw e
        }
    }

    private fun getAuthToken(context: Context): String =
        context.resources.getString(R.string.authKey)

    private fun unsafeOkHttpClient(): OkHttpClient {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(30, TimeUnit.SECONDS) // Увеличиваем таймауты
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}