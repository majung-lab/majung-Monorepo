package com.majung.vehicle.data.remote

import com.majung.vehicle.BuildConfig
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ApiException(val status: Int) : IOException("HTTP $status")

class VisitApi(val baseUrl: String = BuildConfig.SERVER_URL) {
    val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    suspend inline fun <reified T> get(path: String): T = json.decodeFromString(call("GET", path))
    suspend inline fun <reified T> write(method: String, path: String, body: JsonElement): T =
        json.decodeFromString(call(method, path, body.toString()))

    suspend fun call(method: String, path: String, body: String? = null): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("${baseUrl.trimEnd('/')}$path")
            .method(method, body?.toRequestBody("application/json".toMediaType())).build()
        // Retrying the identical request preserves the server's requestId idempotency guarantee.
        for (attempt in 0..1) {
            try {
                return@withContext client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw ApiException(response.code)
                    response.body?.string() ?: throw IOException("Empty response")
                }
            } catch (error: IOException) {
                if (error is ApiException || attempt == 1) throw error
            }
        }
        error("unreachable")
    }
}
