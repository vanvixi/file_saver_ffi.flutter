package com.vanvixi.file_saver_ffi.utils

import android.os.Build
import com.vanvixi.file_saver_ffi.exception.NetworkDownloadException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

object NetworkHelper {
    /**
     * Result of opening an HTTP connection.
     *
     * @param inputStream The response body stream
     * @param contentLength Content length from response headers (-1 if unknown)
     * @param connection The underlying connection (caller must disconnect when done)
     */
    data class ConnectionResult(
        val inputStream: InputStream,
        val contentLength: Long,
        val connection: HttpURLConnection,
    )

    /**
     * Opens an HTTP connection and returns the response InputStream + content length.
     *
     * Does NOT download to a temp file — the caller streams directly from the InputStream.
     *
     * @param urlString The URL to connect to
     * @param headersJson Optional JSON string of HTTP headers (e.g., {"Authorization":"Bearer ..."})
     * @param timeoutMs Timeout in milliseconds for both connect and read
     * @return [ConnectionResult] with input stream, content length, and connection handle
     * @throws NetworkDownloadException on HTTP errors or connection failures
     */
    suspend fun openConnection(
        urlString: String,
        headersJson: String?,
        timeoutMs: Int,
    ): ConnectionResult =
        withContext(Dispatchers.IO) {
            val headers = parseHeaders(headersJson)

            val url =
                try {
                    URL(urlString)
                } catch (_: Exception) {
                    throw NetworkDownloadException("Invalid URL: $urlString")
                }

            val connection =
                try {
                    (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = timeoutMs
                        readTimeout = timeoutMs
                        requestMethod = "GET"
                        instanceFollowRedirects = true

                        headers?.forEach { (key, value) ->
                            setRequestProperty(key, value)
                        }
                    }
                } catch (e: Exception) {
                    throw NetworkDownloadException("Failed to open connection: ${e.message}")
                }

            try {
                connection.connect()

                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    connection.disconnect()
                    throw NetworkDownloadException(
                        connection.responseMessage ?: "Unknown error",
                        statusCode,
                    )
                }

                val contentLength =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        connection.contentLengthLong
                    } else {
                        connection.contentLength.toLong()
                    }

                ConnectionResult(
                    inputStream = connection.inputStream,
                    contentLength = contentLength,
                    connection = connection,
                )
            } catch (e: NetworkDownloadException) {
                throw e
            } catch (e: Exception) {
                connection.disconnect()
                throw NetworkDownloadException("Connection failed: ${e.message}")
            }
        }

    /**
     * Parses a JSON string into a Map of HTTP headers.
     *
     * @param headersJson JSON string like {"key":"value"}, or null
     * @return Map of header key-value pairs, or null if input is null/empty
     */
    private fun parseHeaders(headersJson: String?): Map<String, String>? {
        if (headersJson.isNullOrBlank()) return null

        return try {
            val json = JSONObject(headersJson)
            val map = mutableMapOf<String, String>()
            json.keys().forEach { key ->
                map[key] = json.getString(key)
            }
            map
        } catch (_: Exception) {
            null
        }
    }
}
