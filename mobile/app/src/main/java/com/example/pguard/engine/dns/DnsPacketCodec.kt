package com.example.pguard.engine.dns

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Locale

data class DnsQuestion(
    val transactionId: Int,
    val flags: Int,
    val domain: String,
    val qType: Int,
    val qClass: Int,
    val questionEndOffset: Int
)

/**
 * Pure-Kotlin DNS wire protocol parser and response builder (RFC 1035).
 */
object DnsPacketCodec {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val TYPE_SVCB = 64
    const val TYPE_HTTPS = 65
    const val CLASS_IN = 1

    private const val HEADER_SIZE = 12
    private const val MAX_NAME_JUMPS = 16

    fun parseQuery(payload: ByteArray, length: Int = payload.size): DnsQuestion? {
        if (length < HEADER_SIZE + 5 || length > payload.size) return null

        val transactionId = u16(payload, 0)
        val flags = u16(payload, 2)
        val isResponse = (flags and 0x8000) != 0
        if (isResponse) return null

        val qdCount = u16(payload, 4)
        if (qdCount < 1) return null

        val parsedName = readName(payload, HEADER_SIZE, length) ?: return null
        val qTypeOffset = parsedName.second
        if (qTypeOffset + 4 > length) return null

        val qType = u16(payload, qTypeOffset)
        val qClass = u16(payload, qTypeOffset + 2)

        return DnsQuestion(
            transactionId = transactionId,
            flags = flags,
            domain = parsedName.first.lowercase(Locale.ROOT),
            qType = qType,
            qClass = qClass,
            questionEndOffset = qTypeOffset + 4
        )
    }

    fun buildNxDomainResponse(
        queryPayload: ByteArray,
        queryLength: Int,
        question: DnsQuestion
    ): ByteArray = buildEmptyAnswerResponse(queryPayload, queryLength, question, rcode = 3)

    fun buildNoDataResponse(
        queryPayload: ByteArray,
        queryLength: Int,
        question: DnsQuestion
    ): ByteArray = buildEmptyAnswerResponse(queryPayload, queryLength, question, rcode = 0)

    private fun buildEmptyAnswerResponse(
        queryPayload: ByteArray,
        queryLength: Int,
        question: DnsQuestion,
        rcode: Int
    ): ByteArray {
        val copyLen = minOf(question.questionEndOffset, queryLength, queryPayload.size)
        val response = ByteArray(copyLen)
        System.arraycopy(queryPayload, 0, response, 0, copyLen)

        val rdBit = question.flags and 0x0100
        val respFlags = 0x8080 or rdBit or (rcode and 0x000F)
        writeU16(response, 0, question.transactionId)
        writeU16(response, 2, respFlags)
        writeU16(response, 4, 1) // QDCOUNT
        writeU16(response, 6, 0) // ANCOUNT
        writeU16(response, 8, 0) // NSCOUNT
        writeU16(response, 10, 0) // ARCOUNT
        return response
    }

    fun buildSingleQuery(
        transactionId: Int,
        domain: String,
        qType: Int,
        qClass: Int = CLASS_IN
    ): ByteArray {
        val out = ByteArrayOutputStream(64)
        writeU16Stream(out, transactionId)
        writeU16Stream(out, 0x0100) // RD=1
        writeU16Stream(out, 1) // QDCOUNT
        writeU16Stream(out, 0) // ANCOUNT
        writeU16Stream(out, 0) // NSCOUNT
        writeU16Stream(out, 0) // ARCOUNT

        val labels = domain.trim().trimEnd('.').split('.').filter { it.isNotEmpty() }
        for (label in labels) {
            val bytes = label.toByteArray(StandardCharsets.UTF_8)
            if (bytes.size > 63) continue
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        writeU16Stream(out, qType)
        writeU16Stream(out, qClass)
        return out.toByteArray()
    }

    /**
     * Extracts resolved A / AAAA resource records from [upstreamResponse] (resolved for a
     * SafeSearch hostname) and synthesizes a valid response matching [originalQuestion].
     */
    fun rewriteAnswersForQuestion(
        originalQuery: ByteArray,
        originalQuestion: DnsQuestion,
        upstreamResponse: ByteArray,
        upstreamLength: Int = upstreamResponse.size
    ): ByteArray? {
        if (upstreamLength < HEADER_SIZE || upstreamLength > upstreamResponse.size) return null

        val upstreamFlags = u16(upstreamResponse, 2)
        if ((upstreamFlags and 0x8000) == 0) return null
        val rcode = upstreamFlags and 0x000F
        if (rcode == 3) {
            return buildNxDomainResponse(originalQuery, originalQuery.size, originalQuestion)
        }
        if (rcode != 0) return null

        val qdCount = u16(upstreamResponse, 4)
        val anCount = u16(upstreamResponse, 6)

        var offset = HEADER_SIZE
        repeat(qdCount) {
            offset = skipName(upstreamResponse, offset, upstreamLength) ?: return null
            if (offset + 4 > upstreamLength) return null
            offset += 4
        }

        val matchingRecords = ArrayList<Triple<Int, Int, ByteArray>>()
        repeat(anCount) {
            offset = skipName(upstreamResponse, offset, upstreamLength) ?: return null
            if (offset + 10 > upstreamLength) return null
            val rrType = u16(upstreamResponse, offset)
            val rrClass = u16(upstreamResponse, offset + 2)
            val ttl = u32(upstreamResponse, offset + 4)
            val rdLength = u16(upstreamResponse, offset + 8)
            offset += 10
            if (offset + rdLength > upstreamLength) return null

            val wantsRecord = rrClass == CLASS_IN && (
                (originalQuestion.qType == TYPE_A && rrType == TYPE_A && rdLength == 4) ||
                    (originalQuestion.qType == TYPE_AAAA && rrType == TYPE_AAAA && rdLength == 16)
                )
            if (wantsRecord) {
                val rdata = upstreamResponse.copyOfRange(offset, offset + rdLength)
                matchingRecords.add(Triple(rrType, ttl, rdata))
            }
            offset += rdLength
        }

        if (matchingRecords.isEmpty()) {
            return buildNoDataResponse(originalQuery, originalQuery.size, originalQuestion)
        }

        val headerAndQuestionLen = minOf(originalQuestion.questionEndOffset, originalQuery.size)
        val out = ByteArrayOutputStream(headerAndQuestionLen + matchingRecords.size * 32)
        out.write(originalQuery, 0, headerAndQuestionLen)

        for ((rrType, ttl, rdata) in matchingRecords) {
            // Pointer 0xC00C points to the QNAME at offset 12 in this packet
            out.write(0xC0)
            out.write(0x0C)
            writeU16Stream(out, rrType)
            writeU16Stream(out, CLASS_IN)
            writeU32Stream(out, ttl)
            writeU16Stream(out, rdata.size)
            out.write(rdata)
        }

        val result = out.toByteArray()
        val rdBit = originalQuestion.flags and 0x0100
        writeU16(result, 0, originalQuestion.transactionId)
        writeU16(result, 2, 0x8080 or rdBit) // QR=1, RA=1, RCODE=0
        writeU16(result, 4, 1) // QDCOUNT
        writeU16(result, 6, matchingRecords.size) // ANCOUNT
        writeU16(result, 8, 0) // NSCOUNT
        writeU16(result, 10, 0) // ARCOUNT
        return result
    }

    private fun readName(buf: ByteArray, startOffset: Int, limit: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var offset = startOffset
        var endOffset = -1
        var jumped = false
        var jumps = 0

        while (offset < limit) {
            val len = buf[offset].toInt() and 0xFF
            if (len == 0) {
                if (!jumped) {
                    endOffset = offset + 1
                }
                return sb.toString() to endOffset
            }
            if ((len and 0xC0) == 0xC0) {
                if (offset + 1 >= limit) return null
                val ptr = ((len and 0x3F) shl 8) or (buf[offset + 1].toInt() and 0xFF)
                if (ptr >= limit) return null
                if (!jumped) {
                    endOffset = offset + 2
                    jumped = true
                }
                jumps++
                if (jumps > MAX_NAME_JUMPS) return null
                offset = ptr
            } else {
                if ((len and 0xC0) != 0) return null
                offset++
                if (offset + len > limit) return null
                if (sb.isNotEmpty()) sb.append('.')
                sb.append(String(buf, offset, len, StandardCharsets.UTF_8))
                offset += len
            }
        }
        return null
    }

    private fun skipName(buf: ByteArray, startOffset: Int, limit: Int): Int? {
        var offset = startOffset
        var steps = 0
        while (offset < limit && steps++ < 128) {
            val len = buf[offset].toInt() and 0xFF
            if (len == 0) return offset + 1
            if ((len and 0xC0) == 0xC0) {
                return if (offset + 2 <= limit) offset + 2 else null
            }
            if ((len and 0xC0) != 0) return null
            offset += 1 + len
        }
        return null
    }

    private fun u16(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

    private fun u32(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    private fun writeU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }

    private fun writeU16Stream(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeU32Stream(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
