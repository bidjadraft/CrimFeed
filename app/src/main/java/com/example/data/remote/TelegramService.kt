package com.example.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class TelegramService {

    suspend fun verifyBot(botToken: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanToken = botToken.trim()
        if (cleanToken.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Bot token is empty"))
        }
        runCatching {
            val req = Request.Builder()
                .url("https://api.telegram.org/bot$cleanToken/getMe")
                .get()
                .build()
            NetworkClient.rssHttpClient.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val obj = NetworkClient.json.parseToJsonElement(raw).jsonObject
                val ok = obj["ok"]?.jsonPrimitive?.booleanOrNull == true
                if (!ok) {
                    val desc = obj["description"]?.jsonPrimitive?.contentOrNull ?: "Invalid Bot Token"
                    throw IllegalStateException(desc)
                }
                val username = obj["result"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull ?: "Bot"
                "@$username connected"
            }
        }
    }

    suspend fun publishPost(
        botToken: String,
        channelId: String,
        summaryText: String,
        imageUrl: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = botToken.trim()
        val chatId = channelId.trim()
        if (token.isEmpty() || chatId.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("Configure Telegram Bot Token & Channel ID in Settings first.")
            )
        }

        runCatching {
            val cleanCaption = summaryText.trim().take(1020)
            if (!imageUrl.isNullOrBlank()) {
                // Attempt 1: Direct remote image URL via sendPhoto
                val urlAttempt = runCatching {
                    sendPhotoByUrl(token, chatId, imageUrl, cleanCaption)
                }
                if (urlAttempt.isSuccess) return@runCatching

                // Attempt 2: Download image bytes and upload multipart photo (handles hotlink-protected CDNs)
                val uploadAttempt = runCatching {
                    sendPhotoByMultipartUpload(token, chatId, imageUrl, cleanCaption)
                }
                if (uploadAttempt.isSuccess) return@runCatching
            }

            // Fallback or text-only post via sendMessage
            sendTextMessage(token, chatId, summaryText.trim().take(4000))
        }
    }

    private fun sendPhotoByUrl(token: String, chatId: String, imageUrl: String, caption: String) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("photo", imageUrl)
            .add("caption", caption)
            .build()

        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendPhoto")
            .post(form)
            .build()

        executeAndAssertTelegramOk(req)
    }

    private fun sendPhotoByMultipartUpload(token: String, chatId: String, imageUrl: String, caption: String) {
        val imgReq = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", NetworkClient.BROWSER_USER_AGENT)
            .get()
            .build()

        val (bytes, mimeType) = NetworkClient.rssHttpClient.newCall(imgReq).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("Image download HTTP ${resp.code}")
            val contentBytes = resp.body?.bytes() ?: throw IllegalStateException("Empty image bytes")
            val mime = resp.header("Content-Type")?.substringBefore(';')?.trim() ?: "image/jpeg"
            contentBytes to mime
        }

        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", caption)
            .addFormDataPart(
                "photo",
                "article.jpg",
                bytes.toRequestBody(mimeType.toMediaType())
            )
            .build()

        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendPhoto")
            .post(multipart)
            .build()

        executeAndAssertTelegramOk(req)
    }

    private fun sendTextMessage(token: String, chatId: String, text: String) {
        val form = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .build()

        val req = Request.Builder()
            .url("https://api.telegram.org/bot$token/sendMessage")
            .post(form)
            .build()

        executeAndAssertTelegramOk(req)
    }

    private fun executeAndAssertTelegramOk(request: Request) {
        NetworkClient.aiHttpClient.newCall(request).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            val ok = runCatching {
                NetworkClient.json.parseToJsonElement(raw).jsonObject["ok"]?.jsonPrimitive?.booleanOrNull
            }.getOrNull() == true

            if (!resp.isSuccessful || !ok) {
                val desc = runCatching {
                    NetworkClient.json.parseToJsonElement(raw).jsonObject["description"]?.jsonPrimitive?.contentOrNull
                }.getOrNull() ?: "Telegram API HTTP ${resp.code}"
                throw IllegalStateException(desc)
            }
        }
    }
}
