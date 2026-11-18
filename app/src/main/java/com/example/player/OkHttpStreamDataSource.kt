package com.example.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.EOFException
import java.io.IOException
import java.io.InputStream

@OptIn(UnstableApi::class)
class OkHttpStreamDataSource(
    private val client: OkHttpClient,
    private val defaultUserAgent: String,
) : BaseDataSource(/* isNetwork = */ true) {

    class Factory(
        private val client: OkHttpClient,
        private val defaultUserAgent: String,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return OkHttpStreamDataSource(client, defaultUserAgent)
        }
    }

    private var dataSpec: DataSpec? = null
    private var response: Response? = null
    private var responseByteStream: InputStream? = null
    private var opened = false
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()
    private var bytesRead: Long = 0L

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.bytesRead = 0L
        this.bytesToRead = C.LENGTH_UNSET.toLong()
        transferInitializing(dataSpec)

        val requestBuilder = Request.Builder()
            .url(dataSpec.uri.toString())

        var hasCustomUa = false
        for ((key, value) in dataSpec.httpRequestHeaders) {
            if (key.equals("User-Agent", ignoreCase = true)) {
                hasCustomUa = true
            }
            requestBuilder.header(key, value)
        }
        if (!hasCustomUa) {
            requestBuilder.header("User-Agent", defaultUserAgent)
        }

        val rangeStart = dataSpec.position
        val rangeLength = dataSpec.length
        if (rangeStart != 0L || rangeLength != C.LENGTH_UNSET.toLong()) {
            val rangeHeader = if (rangeLength != C.LENGTH_UNSET.toLong()) {
                "bytes=$rangeStart-${rangeStart + rangeLength - 1}"
            } else {
                "bytes=$rangeStart-"
            }
            requestBuilder.header("Range", rangeHeader)
        }

        val execResponse = try {
            client.newCall(requestBuilder.build()).execute()
        } catch (e: IOException) {
            throw IOException("Network error opening stream: ${e.message}", e)
        }

        response = execResponse
        val body = execResponse.body
        if (!execResponse.isSuccessful || body == null) {
            val code = execResponse.code
            closeConnectionQuietly()
            throw IOException("HTTP $code error playing stream")
        }

        responseByteStream = body.byteStream()
        val contentLength = body.contentLength()
        bytesToRead = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (contentLength >= 0L) {
            contentLength
        } else {
            C.LENGTH_UNSET.toLong()
        }

        opened = true
        transferStarted(dataSpec)
        return bytesToRead
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesToRead != C.LENGTH_UNSET.toLong()) {
            val remaining = bytesToRead - bytesRead
            if (remaining <= 0L) return C.RESULT_END_OF_INPUT
            val readLen = minOf(length.toLong(), remaining).toInt()
            return readInternal(buffer, offset, readLen)
        }
        return readInternal(buffer, offset, length)
    }

    private fun readInternal(buffer: ByteArray, offset: Int, readLength: Int): Int {
        val stream = responseByteStream ?: throw IOException("Stream is closed")
        val read = stream.read(buffer, offset, readLength)
        if (read == -1) {
            if (bytesToRead != C.LENGTH_UNSET.toLong() && bytesRead < bytesToRead) {
                throw EOFException("Unexpected end of stream")
            }
            return C.RESULT_END_OF_INPUT
        }
        bytesRead += read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? {
        return response?.request?.url?.toString()?.let { Uri.parse(it) } ?: dataSpec?.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        return response?.headers?.toMultimap() ?: emptyMap()
    }

    override fun close() {
        dataSpec = null
        if (opened) {
            opened = false
            transferEnded()
        }
        closeConnectionQuietly()
    }

    private fun closeConnectionQuietly() {
        runCatching { responseByteStream?.close() }
        runCatching { response?.close() }
        responseByteStream = null
        response = null
    }
}
