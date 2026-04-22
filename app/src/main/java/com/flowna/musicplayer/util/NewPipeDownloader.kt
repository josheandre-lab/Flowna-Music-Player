package com.flowna.musicplayer.util

import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NewPipeDownloader private constructor() : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val url = URL(request.url())
        val connection = url.openConnection() as HttpURLConnection

        try {
            connection.requestMethod = request.httpMethod()
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true

            // Set headers
            val headers = request.headers()
            for ((key, values) in headers) {
                for (value in values) {
                    connection.addRequestProperty(key, value)
                }
            }

            // Set default User-Agent if not provided
            if (!headers.containsKey("User-Agent")) {
                connection.setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; rv:91.0) Gecko/20100101 Firefox/91.0"
                )
            }

            // Send data for POST requests
            val dataToSend = request.dataToSend()
            if (dataToSend != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(dataToSend) }
            }

            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage ?: ""

            if (responseCode == 429) {
                throw ReCaptchaException("Rate limited", request.url())
            }

            val responseHeaders = connection.headerFields
                ?.filterKeys { it != null }
                ?.mapValues { it.value ?: emptyList() }
                ?: emptyMap()

            val responseBody = try {
                val stream = if (responseCode < 400) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                stream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                ""
            }

            val latestUrl = connection.url?.toString() ?: request.url()

            return Response(responseCode, responseMessage, responseHeaders, responseBody, latestUrl)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        @Volatile
        private var instance: NewPipeDownloader? = null

        fun getInstance(): NewPipeDownloader {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloader().also { instance = it }
            }
        }
    }
}
