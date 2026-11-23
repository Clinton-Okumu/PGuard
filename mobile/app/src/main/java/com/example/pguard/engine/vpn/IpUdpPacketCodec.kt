package com.example.pguard.engine.vpn

/**
 * Pure-Kotlin IPv4 / IPv6 + UDP packet parser and response builder for the TUN interface.
 * Computes valid IPv4 header checksums and RFC 768 / RFC 2460 UDP pseudo-header checksums.
 */
object IpUdpPacketCodec {

    private const val PROTO_UDP = 17
    private const val IPV4_MIN_HEADER = 20
    private const val IPV6_HEADER = 40
    private const val UDP_HEADER = 8

    class UdpPacket(
        val ipVersion: Int,
        val srcAddress: ByteArray,
        val dstAddress: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val payload: ByteArray
    )

    fun parseUdp(packet: ByteArray, length: Int = packet.size): UdpPacket? {
        if (length < 28 || length > packet.size) return null
        val version = (packet[0].toInt() ushr 4) and 0x0F
        return when (version) {
            4 -> parseIpv4Udp(packet, length)
            6 -> parseIpv6Udp(packet, length)
            else -> null
        }
    }

    private fun parseIpv4Udp(packet: ByteArray, length: Int): UdpPacket? {
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (ihl < IPV4_MIN_HEADER || length < ihl + UDP_HEADER) return null
        val totalLen = u16(packet, 2)
        if (totalLen < ihl + UDP_HEADER || totalLen > length) return null
        val protocol = packet[9].toInt() and 0xFF
        if (protocol != PROTO_UDP) return null

        val udpOffset = ihl
        val srcPort = u16(packet, udpOffset)
        val dstPort = u16(packet, udpOffset + 2)
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < UDP_HEADER || udpOffset + udpLength > totalLen) return null

        return UdpPacket(
            ipVersion = 4,
            srcAddress = packet.copyOfRange(12, 16),
            dstAddress = packet.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            payload = packet.copyOfRange(udpOffset + UDP_HEADER, udpOffset + udpLength)
        )
    }

    private fun parseIpv6Udp(packet: ByteArray, length: Int): UdpPacket? {
        if (length < IPV6_HEADER + UDP_HEADER) return null
        val payloadLength = u16(packet, 4)
        if (IPV6_HEADER + payloadLength > length || payloadLength < UDP_HEADER) return null
        val nextHeader = packet[6].toInt() and 0xFF
        if (nextHeader != PROTO_UDP) return null

        val udpOffset = IPV6_HEADER
        val srcPort = u16(packet, udpOffset)
        val dstPort = u16(packet, udpOffset + 2)
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < UDP_HEADER || udpOffset + udpLength > IPV6_HEADER + payloadLength) return null

        return UdpPacket(
            ipVersion = 6,
            srcAddress = packet.copyOfRange(8, 24),
            dstAddress = packet.copyOfRange(24, 40),
            srcPort = srcPort,
            dstPort = dstPort,
            payload = packet.copyOfRange(udpOffset + UDP_HEADER, udpOffset + udpLength)
        )
    }

    fun buildUdpResponse(request: UdpPacket, responsePayload: ByteArray): ByteArray {
        val respSrcIp = request.dstAddress
        val respDstIp = request.srcAddress
        val respSrcPort = request.dstPort
        val respDstPort = request.srcPort

        return when (request.ipVersion) {
            4 -> buildIpv4UdpResponse(respSrcIp, respDstIp, respSrcPort, respDstPort, responsePayload)
            6 -> buildIpv6UdpResponse(respSrcIp, respDstIp, respSrcPort, respDstPort, responsePayload)
            else -> throw IllegalArgumentException("Unsupported IP version: ${request.ipVersion}")
        }
    }

    private fun buildIpv4UdpResponse(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = UDP_HEADER + payload.size
        val totalLength = IPV4_MIN_HEADER + udpLength
        val out = ByteArray(totalLength)

        out[0] = 0x45.toByte() // Version 4, IHL 5 (20 bytes)
        out[1] = 0x00
        writeU16(out, 2, totalLength)
        out[4] = 0x00
        out[5] = 0x00
        out[6] = 0x40.toByte() // Don't fragment
        out[7] = 0x00
        out[8] = 64.toByte() // TTL
        out[9] = PROTO_UDP.toByte()
        out[10] = 0x00
        out[11] = 0x00
        System.arraycopy(srcIp, 0, out, 12, 4)
        System.arraycopy(dstIp, 0, out, 16, 4)

        val ipChecksum = onesComplementChecksum(out, 0, IPV4_MIN_HEADER, initialSum = 0)
        writeU16(out, 10, ipChecksum)

        writeUdpSection(out, IPV4_MIN_HEADER, srcPort, dstPort, udpLength, payload)

        var pseudoSum = sumBytes(srcIp, 0, 4)
        pseudoSum += sumBytes(dstIp, 0, 4)
        pseudoSum += PROTO_UDP
        pseudoSum += udpLength
        val udpChecksum = onesComplementChecksum(out, IPV4_MIN_HEADER, udpLength, pseudoSum)
        writeU16(out, IPV4_MIN_HEADER + 6, if (udpChecksum == 0) 0xFFFF else udpChecksum)

        return out
    }

    private fun buildIpv6UdpResponse(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLength = UDP_HEADER + payload.size
        val totalLength = IPV6_HEADER + udpLength
        val out = ByteArray(totalLength)

        out[0] = 0x60.toByte() // Version 6
        out[1] = 0x00
        out[2] = 0x00
        out[3] = 0x00
        writeU16(out, 4, udpLength)
        out[6] = PROTO_UDP.toByte()
        out[7] = 64.toByte() // Hop Limit
        System.arraycopy(srcIp, 0, out, 8, 16)
        System.arraycopy(dstIp, 0, out, 24, 16)

        writeUdpSection(out, IPV6_HEADER, srcPort, dstPort, udpLength, payload)

        var pseudoSum = sumBytes(srcIp, 0, 16)
        pseudoSum += sumBytes(dstIp, 0, 16)
        pseudoSum += (udpLength ushr 16) and 0xFFFF
        pseudoSum += udpLength and 0xFFFF
        pseudoSum += PROTO_UDP
        val udpChecksum = onesComplementChecksum(out, IPV6_HEADER, udpLength, pseudoSum)
        writeU16(out, IPV6_HEADER + 6, if (udpChecksum == 0) 0xFFFF else udpChecksum)

        return out
    }

    private fun writeUdpSection(
        out: ByteArray,
        udpOffset: Int,
        srcPort: Int,
        dstPort: Int,
        udpLength: Int,
        payload: ByteArray
    ) {
        writeU16(out, udpOffset, srcPort)
        writeU16(out, udpOffset + 2, dstPort)
        writeU16(out, udpOffset + 4, udpLength)
        out[udpOffset + 6] = 0x00
        out[udpOffset + 7] = 0x00
        System.arraycopy(payload, 0, out, udpOffset + UDP_HEADER, payload.size)
    }

    internal fun sumBytes(buf: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i = offset
        val end = offset + length
        while (i + 1 < end) {
            sum += ((buf[i].toInt() and 0xFF) shl 8) or (buf[i + 1].toInt() and 0xFF)
            i += 2
        }
        if (i < end) {
            sum += (buf[i].toInt() and 0xFF) shl 8
        }
        return sum
    }

    internal fun onesComplementChecksum(
        buf: ByteArray,
        offset: Int,
        length: Int,
        initialSum: Int
    ): Int {
        var sum = initialSum + sumBytes(buf, offset, length)
        while ((sum ushr 16) != 0) {
            sum = (sum and 0xFFFF) + (sum ushr 16)
        }
        return sum.inv() and 0xFFFF
    }

    private fun u16(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)

    private fun writeU16(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = ((value ushr 8) and 0xFF).toByte()
        buf[offset + 1] = (value and 0xFF).toByte()
    }
}
