package com.example.pguard.engine.dns

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsResponseCacheTest {

    private fun buildSampleResponse(
        txId: Int,
        domain: String,
        ttlSeconds: Int,
        ipOctet: Int = 1
    ): ByteArray {
        val query = DnsPacketCodec.buildSingleQuery(txId, domain, DnsPacketCodec.TYPE_A)
        val out = ByteArray(query.size + 16)
        System.arraycopy(query, 0, out, 0, query.size)
        // QR=1, RA=1, RCODE=0, ANCOUNT=1
        out[2] = 0x81.toByte()
        out[3] = 0x80.toByte()
        out[6] = 0x00
        out[7] = 0x01
        val off = query.size
        out[off] = 0xC0.toByte()
        out[off + 1] = 0x0C
        out[off + 2] = 0x00
        out[off + 3] = 0x01 // TYPE A
        out[off + 4] = 0x00
        out[off + 5] = 0x01 // CLASS IN
        out[off + 6] = ((ttlSeconds ushr 24) and 0xFF).toByte()
        out[off + 7] = ((ttlSeconds ushr 16) and 0xFF).toByte()
        out[off + 8] = ((ttlSeconds ushr 8) and 0xFF).toByte()
        out[off + 9] = (ttlSeconds and 0xFF).toByte()
        out[off + 10] = 0x00
        out[off + 11] = 0x04 // RDLENGTH 4
        out[off + 12] = 93
        out[off + 13] = 184.toByte()
        out[off + 14] = 216.toByte()
        out[off + 15] = ipOctet.toByte()
        return out
    }

    @Test
    fun `packet codec helpers extract ttl rewrite txId and build servfail`() {
        val query = DnsPacketCodec.buildSingleQuery(0x1234, "example.com", DnsPacketCodec.TYPE_A)
        val question = DnsPacketCodec.parseQuery(query)!!
        val servFail = DnsPacketCodec.buildServFailResponse(query, query.size, question)
        assertEquals(0x12, servFail[0].toInt() and 0xFF)
        assertEquals(0x34, servFail[1].toInt() and 0xFF)
        assertEquals(2, servFail[3].toInt() and 0x0F) // RCODE = SERVFAIL

        val resp = buildSampleResponse(0x1122, "example.com", ttlSeconds = 120)
        assertEquals(120, DnsPacketCodec.extractMinTtlSeconds(resp))

        val rewritten = DnsPacketCodec.withTransactionId(resp, 0xABCD)
        assertEquals(0xAB, rewritten[0].toInt() and 0xFF)
        assertEquals(0xCD, rewritten[1].toInt() and 0xFF)
        assertArrayEquals(resp.copyOfRange(2, resp.size), rewritten.copyOfRange(2, rewritten.size))
    }

    @Test
    fun `cache hit rewrites txId respects clamped ttl and isolates modes`() {
        var nowMs = 1_000_000L
        val cache = DnsResponseCache(
            clockMs = { nowMs },
            maxEntries = 10,
            minTtlSeconds = 15,
            maxTtlSeconds = 300
        )

        val familyResp = buildSampleResponse(0x1111, "example.com", ttlSeconds = 2) // clamped up to 15s
        cache.put("Example.COM", DnsPacketCodec.TYPE_A, "family", familyResp)

        val hit = cache.get("example.com", DnsPacketCodec.TYPE_A, "family", 0x9988)
        assertNotNull(hit)
        assertEquals(0x99, hit!![0].toInt() and 0xFF)
        assertEquals(0x88, hit[1].toInt() and 0xFF)

        // Different mode must not return family entry
        assertNull(cache.get("example.com", DnsPacketCodec.TYPE_A, "unfiltered", 0x9988))

        // At +10s still valid (clamped min TTL is 15s)
        nowMs += 10_000L
        assertNotNull(cache.get("example.com", DnsPacketCodec.TYPE_A, "family", 0x7766))

        // At +15s expired
        nowMs += 5_000L
        assertNull(cache.get("example.com", DnsPacketCodec.TYPE_A, "family", 0x7766))
    }

    @Test
    fun `cache evicts oldest entries when reaching maxEntries`() {
        val cache = DnsResponseCache(maxEntries = 2)
        cache.put("one.com", DnsPacketCodec.TYPE_A, "family", buildSampleResponse(1, "one.com", 60))
        cache.put("two.com", DnsPacketCodec.TYPE_A, "family", buildSampleResponse(2, "two.com", 60))
        assertEquals(2, cache.size())

        cache.put("three.com", DnsPacketCodec.TYPE_A, "family", buildSampleResponse(3, "three.com", 60))
        assertEquals(2, cache.size())
        assertNull(cache.get("one.com", DnsPacketCodec.TYPE_A, "family", 10))
        assertNotNull(cache.get("two.com", DnsPacketCodec.TYPE_A, "family", 10))
        assertNotNull(cache.get("three.com", DnsPacketCodec.TYPE_A, "family", 10))

        cache.clear()
        assertEquals(0, cache.size())
    }
}
