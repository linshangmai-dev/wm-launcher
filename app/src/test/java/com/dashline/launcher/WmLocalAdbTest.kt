// SPDX-License-Identifier: GPL-3.0-only
package com.dashline.launcher

import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23])
class WmLocalAdbTest {
    private data class Packet(val command: Int, val a: Int, val b: Int, val data: ByteArray)
    private fun read(socket: Socket): Packet {
        val input = DataInputStream(socket.getInputStream())
        val header = ByteBuffer.wrap(ByteArray(24).also { input.readFully(it) }).order(ByteOrder.LITTLE_ENDIAN)
        val command = header.int; val a = header.int; val b = header.int; val length = header.int
        val checksum = header.int; assertEquals(command.inv(), header.int)
        val bytes = ByteArray(length).also { input.readFully(it) }
        assertEquals(checksum, bytes.sumOf { it.toInt() and 255 })
        return Packet(command, a, b, bytes)
    }
    private fun send(socket: Socket, command: Int, a: Int, b: Int, text: String = "", badChecksum: Boolean = false) {
        val bytes = text.toByteArray()
        val header = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN).putInt(command).putInt(a).putInt(b)
            .putInt(bytes.size).putInt(bytes.sumOf { it.toInt() and 255 } + if (badChecksum) 1 else 0).putInt(command.inv()).array()
        socket.getOutputStream().apply { write(header); write(bytes); flush() }
    }
    private fun fixture(server: (Socket) -> Unit, client: (Int) -> Unit) {
        ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1")).use { listener ->
            val failure = AtomicReference<Throwable>()
            val worker = Thread({ try { listener.accept().use { it.soTimeout = 2000; server(it) } }
                catch (error: Throwable) { failure.set(error) } }).apply { start() }
            try { client(listener.localPort) } finally { worker.join(3000) }
            assertFalse(worker.isAlive)
            failure.get()?.let { throw it }
        }
    }
    @Test fun delayedCloseCannotSwallowTheNextCommandResult() = fixture(server = { socket ->
        assertEquals(CNXN, read(socket).command)
        send(socket, CNXN, 0x01000000, 4096, "device::\u0000")
        val first = read(socket); assertEquals(OPEN, first.command); assertEquals(1, first.a)
        send(socket, OKAY, 7, first.a); send(socket, WRTE, 7, first.a, "uid=0(root)\n")
        assertEquals(OKAY, read(socket).command)
        send(socket, CLSE, 7, first.a); assertEquals(CLSE, read(socket).command)
        send(socket, CLSE, 7, first.a) // Legacy daemon's late close for the previous stream.
        val next = read(socket); assertEquals(OPEN, next.command); assertEquals(2, next.a)
        send(socket, OKAY, 8, next.a); send(socket, WRTE, 8, next.a, "WM_HOME_RESULT={\"ok\":true}\n")
        assertEquals(OKAY, read(socket).command)
        send(socket, CLSE, 8, next.a); assertEquals(CLSE, read(socket).command)
    }, client = { port ->
        WmLocalAdb(RuntimeEnvironment.getApplication(), port).use { adb ->
            adb.connect()
            assertEquals("uid=0(root)\n", adb.shell("id"))
            assertEquals("WM_HOME_RESULT={\"ok\":true}\n", adb.shell("read-status"))
        }
    })
    @Test fun aSilentConnectedDaemonIsRecoverableAndIsNotReportedAsDisabled() = fixture(server = { socket ->
        assertEquals(CNXN, read(socket).command); Thread.sleep(300)
    }, client = { port ->
        WmLocalAdb(RuntimeEnvironment.getApplication(), port, 100).use { adb ->
            try { adb.connect(); fail("Expected handshake timeout") }
            catch (failure: WmAdbHandshakeException) {
                assertTrue(failure.mayRecover)
                assertFalse(failure.message!!.contains("请打开"))
            }
        }
    })
    @Test fun corruptPacketsNeverTriggerServiceRecovery() = fixture(server = { socket ->
        read(socket); send(socket, CNXN, 0x01000000, 4096, "device::\u0000", true)
    }, client = { port ->
        WmLocalAdb(RuntimeEnvironment.getApplication(), port).use { adb ->
            try { adb.connect(); fail("Expected checksum failure") }
            catch (failure: WmAdbHandshakeException) { assertFalse(failure.mayRecover) }
        }
    })
    private companion object {
        const val CNXN = 0x4e584e43; const val OPEN = 0x4e45504f; const val OKAY = 0x59414b4f
        const val WRTE = 0x45545257; const val CLSE = 0x45534c43
    }
}
