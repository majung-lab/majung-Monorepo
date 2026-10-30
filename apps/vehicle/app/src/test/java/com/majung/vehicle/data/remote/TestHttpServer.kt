package com.majung.vehicle.data.remote

import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.net.SocketException
import kotlin.concurrent.thread

/** A real local HTTP peer; keeps contract tests independent of the running development server. */
class TestHttpServer(private val respond: (Request) -> Pair<Int, String>) : AutoCloseable {
    data class Request(val method: String, val path: String, val body: String)
    private val server = ServerSocket(0)
    val port = server.localPort
    init {
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val input = socket.getInputStream()
                        val bytes = ByteArrayOutputStream()
                        var tail = 0
                        while (tail != 0x0d0a0d0a) {
                            val b = input.read()
                            if (b < 0) error("Incomplete headers")
                            bytes.write(b)
                            tail = (tail shl 8) or b
                        }
                        val headers = bytes.toString("UTF-8").split("\r\n")
                        val first = headers.first().split(' ')
                        val length = headers.firstOrNull { it.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                        val body = ByteArray(length)
                        var offset = 0
                        while (offset < length) { val n = input.read(body, offset, length - offset); check(n > 0); offset += n }
                        val (status, value) = respond(Request(first[0], first[1].substringBefore('?'), body.decodeToString()))
                        val payload = value.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 $status Result\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(payload)
                            flush()
                        }
                    }
                } catch (e: SocketException) { if (!server.isClosed) throw e }
            }
        }
    }
    override fun close() { server.close() }
}
