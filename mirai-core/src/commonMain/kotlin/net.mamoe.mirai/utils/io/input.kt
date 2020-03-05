/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("EXPERIMENTAL_API_USAGE", "EXPERIMENTAL_UNSIGNED_LITERALS", "NOTHING_TO_INLINE")
@file:JvmMultifileClass
@file:JvmName("Utils")

package net.mamoe.mirai.utils.io

import io.ktor.utils.io.charsets.Charset
import io.ktor.utils.io.charsets.Charsets
import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.useInstance
import kotlinx.io.OutputStream
import kotlinx.serialization.InternalSerializationApi
import net.mamoe.mirai.utils.MiraiDebugAPI
import net.mamoe.mirai.utils.MiraiInternalAPI
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.jvm.JvmMultifileClass
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSynthetic

@OptIn(MiraiInternalAPI::class, InternalSerializationApi::class)
fun ByteReadPacket.copyTo(outputStream: OutputStream) {
    ByteArrayPool.useInstance {
        while (this.isNotEmpty) {
            outputStream.write(it, 0, this.readAvailable(it))
        }
    }
}

@MiraiInternalAPI
inline fun <R> ByteReadPacket.useBytes(
    n: Int = remaining.toInt(),//not that safe but adequate
    block: (data: ByteArray, length: Int) -> R
): R = ByteArrayPool.useInstance {
    this.readFully(it, 0, n)
    block(it, n)
}

@MiraiInternalAPI
inline fun ByteReadPacket.readPacketExact(
    n: Int = remaining.toInt()//not that safe but adequate
): ByteReadPacket = this.readBytes(n).toReadPacket()

@OptIn(ExperimentalContracts::class)
inline fun <C : Closeable, R> C.withUse(block: C.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return use(block)
}

private inline fun <R> inline(block: () -> R): R = block()

typealias TlvMap = MutableMap<Int, ByteArray>

inline fun TlvMap.getOrFail(tag: Int): ByteArray {
    return this[tag] ?: error("cannot find tlv 0x${tag.toUHexString("")}($tag)")
}

inline fun TlvMap.getOrFail(tag: Int, lazyMessage: (tag: Int) -> String): ByteArray {
    return this[tag] ?: error(lazyMessage(tag))
}

@Suppress("FunctionName")
@MiraiInternalAPI
inline fun Input._readTLVMap(tagSize: Int = 2, suppressDuplication: Boolean = true): TlvMap =
    _readTLVMap(true, tagSize, suppressDuplication)

@MiraiDebugAPI
@Suppress("DuplicatedCode", "FunctionName")
fun Input._readTLVMap(expectingEOF: Boolean = true, tagSize: Int, suppressDuplication: Boolean = true): TlvMap {
    val map = mutableMapOf<Int, ByteArray>()
    var key = 0

    while (inline {
            try {
                key = when (tagSize) {
                    1 -> readUByte().toInt()
                    2 -> readUShort().toInt()
                    4 -> readUInt().toInt()
                    else -> error("Unsupported tag size: $tagSize")
                }
            } catch (e: Exception) { // java.nio.BufferUnderflowException is not a EOFException...
                if (expectingEOF) {
                    return map
                }
                throw e
            }
            key
        }.toUByte() != UByte.MAX_VALUE) {

        if (map.containsKey(key)) {
            @Suppress("ControlFlowWithEmptyBody")
            if (!suppressDuplication) {
                /*
                @Suppress("DEPRECATION")
                MiraiLogger.error(
                    @Suppress("IMPLICIT_CAST_TO_ANY")
                    """
                Error readTLVMap:
                duplicated key ${when (tagSize) {
                        1 -> key.toByte()
                        2 -> key.toShort()
                        4 -> key
                        else -> error("unreachable")
                    }.contentToString()}
                map=${map.contentToString()}
                duplicating value=${this.readUShortLVByteArray().toUHexString()}
                """.trimIndent()
                )*/
            } else {
                this.discardExact(this.readShort().toInt() and 0xffff)
            }
        } else {
            try {
                map[key] = this.readBytes(readUShort().toInt())
            } catch (e: Exception) { // BufferUnderflowException, java.io.EOFException
                // if (expectingEOF) {
                //     return map
                // }
                throw e
            }
        }
    }
    return map
}

inline fun Input.readString(length: Int, charset: Charset = Charsets.UTF_8): String =
    String(this.readBytes(length), charset = charset)

inline fun Input.readString(length: Long, charset: Charset = Charsets.UTF_8): String =
    String(this.readBytes(length.toInt()), charset = charset)

inline fun Input.readString(length: Short, charset: Charset = Charsets.UTF_8): String =
    String(this.readBytes(length.toInt()), charset = charset)

@JvmSynthetic
inline fun Input.readString(length: UShort, charset: Charset = Charsets.UTF_8): String =
    String(this.readBytes(length.toInt()), charset = charset)

inline fun Input.readString(length: Byte, charset: Charset = Charsets.UTF_8): String =
    String(this.readBytes(length.toInt()), charset = charset)