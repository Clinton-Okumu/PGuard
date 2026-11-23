package com.example.pguard.engine.dns

import com.example.pguard.engine.vpn.IpUdpPacketCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class DnsEngineTest {

    private val database = AdultDomainDatabase.fromLines(
        sequenceOf(
            "# comment",
            "pornhub.com",
            "xvideos.com",
            "onlyfans.com",
            "heavy-r.com"
        )
    )
    private val filter = DnsDomainFilter(database)

    @Test
    fun `adult domain database matches exact and subdomains without lookalikes`() {
        assertTrue(database.containsDomain("pornhub.com"))
        assertTrue(database.containsDomain("www.pornhub.com."))
        assertTrue(database.containsDomain("cdn.media.xvideos.com"))
        assertTrue(database.containsDomain("heavy-r.com"))
        assertFalse(database.containsDomain("notpornhub.com"))
        assertFalse(database.containsDomain("google.com"))
    }

    @Test
    fun `dns domain filter prioritizes doh opt-out whitelist custom blocklist tiktok and adult rules`() {
        val config = DnsFilterConfig(
            blockAdultContent = true,
            blockTikTok = true,
            customBlocklist = listOf("reddit.com", "casino"),
            whitelist = listOf("old.reddit.com", "onlyfans.com")
        )

        val doh = filter.evaluate("use-application-dns.net", config)
        assertTrue(doh is DnsDecision.Block && !doh.recordInterception)

        // Whitelist overrides custom blocklist and adult database
        assertEquals(DnsDecision.AllowUnfiltered, filter.evaluate("old.reddit.com", config))
        assertEquals(DnsDecision.AllowUnfiltered, filter.evaluate("onlyfans.com", config))

        // Custom domain blocks non-whitelisted subdomains
        val custom = filter.evaluate("www.reddit.com", config)
        assertTrue(custom is DnsDecision.Block && custom.reason == "custom-blocklist")

        // TikTok blocked when enabled
        val tiktok = filter.evaluate("api16-normal-c-useast1a.tiktokv.com", config)
        assertTrue(tiktok is DnsDecision.Block && tiktok.reason == "tiktok")

        // Adult domain and adult TLD blocked
        val adultDb = filter.evaluate("m.pornhub.com", config)
        assertTrue(adultDb is DnsDecision.Block && adultDb.reason == "adult-domain")
        val adultTld = filter.evaluate("random.xxx", config)
        assertTrue(adultTld is DnsDecision.Block && adultTld.reason == "adult-domain")

        // SafeSearch enforced for Google / Bing / DuckDuckGo
        assertEquals(
            DnsDecision.SafeSearchRedirect("forcesafesearch.google.com"),
            filter.evaluate("www.google.com", config)
        )
        assertEquals(
            DnsDecision.SafeSearchRedirect("safe.duckduckgo.com"),
            filter.evaluate("duckduckgo.com", config)
        )

        // General traffic routes via family-filtered upstream
        assertEquals(DnsDecision.AllowFamilyFiltered, filter.evaluate("wikipedia.org", config))
    }

    @Test
    fun `dns packet codec parses query builds nxdomain and rewrites safesearch A record`() {
        val query = DnsPacketCodec.buildSingleQuery(
            transactionId = 0x1234,
            domain = "www.google.com",
            qType = DnsPacketCodec.TYPE_A
        )
        val parsed = DnsPacketCodec.parseQuery(query, query.size)
        assertNotNull(parsed)
        parsed!!
        assertEquals(0x1234, parsed.transactionId)
        assertEquals("www.google.com", parsed.domain)
        assertEquals(DnsPacketCodec.TYPE_A, parsed.qType)

        val nxDomain = DnsPacketCodec.buildNxDomainResponse(query, query.size, parsed)
        assertEquals(0x12, nxDomain[0].toInt() and 0xFF)
        assertEquals(0x34, nxDomain[1].toInt() and 0xFF)
        // QR=1 (0x80) and RCODE=3 (NXDOMAIN)
        assertEquals(0x80, nxDomain[2].toInt() and 0x80)
        assertEquals(3, nxDomain[3].toInt() and 0x0F)

        // Build synthetic upstream response for forcesafesearch.google.com returning A 216.239.38.120
        val upstreamQuery = DnsPacketCodec.buildSingleQuery(
            transactionId = 0x5678,
            domain = "forcesafesearch.google.com",
            qType = DnsPacketCodec.TYPE_A
        )
        val upstreamOut = ByteArrayOutputStream()
        upstreamOut.write(upstreamQuery)
        // Pointer to question name 0xC00C, TYPE A (1), CLASS IN (1), TTL 300, RDLEN 4, IP 216.239.38.120
        upstreamOut.write(byteArrayOf(0xC0.toByte(), 0x0C, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00, 0x01, 0x2C, 0x00, 0x04))
        upstreamOut.write(byteArrayOf(216.toByte(), 239.toByte(), 38, 120))
        val upstreamResp = upstreamOut.toByteArray()
        upstreamResp[2] = 0x81.toByte()
        upstreamResp[3] = 0x80.toByte()
        upstreamResp[7] = 0x01 // ANCOUNT = 1

        val rewritten = DnsPacketCodec.rewriteAnswersForQuestion(query, parsed, upstreamResp, upstreamResp.size)
        assertNotNull(rewritten)
        rewritten!!
        assertEquals(0x12, rewritten[0].toInt() and 0xFF)
        assertEquals(0x34, rewritten[1].toInt() and 0xFF)
        assertEquals(0, rewritten[3].toInt() and 0x0F) // NOERROR
        assertEquals(1, ((rewritten[6].toInt() and 0xFF) shl 8) or (rewritten[7].toInt() and 0xFF))
        // Last 4 bytes must equal 216.239.38.120
        assertArrayEquals(
            byteArrayOf(216.toByte(), 239.toByte(), 38, 120),
            rewritten.copyOfRange(rewritten.size - 4, rewritten.size)
        )
    }

    @Test
    fun `ip udp codec parses ipv4 and ipv6 packets and emits valid checksummed responses`() {
        val dnsQuery = DnsPacketCodec.buildSingleQuery(0x2222, "pornhub.com", DnsPacketCodec.TYPE_A)

        // IPv4 request: 10.111.222.1:51234 -> 10.111.222.2:53
        val ipv4Req = IpUdpPacketCodec.UdpPacket(
            ipVersion = 4,
            srcAddress = byteArrayOf(10, 111, 222.toByte(), 2),
            dstAddress = byteArrayOf(10, 111, 222.toByte(), 1),
            srcPort = 53,
            dstPort = 51234,
            payload = byteArrayOf()
        )
        val rawIpv4 = IpUdpPacketCodec.buildUdpResponse(ipv4Req, dnsQuery)
        // Verify IPv4 header checksum validates to 0
        assertEquals(0, IpUdpPacketCodec.onesComplementChecksum(rawIpv4, 0, 20, 0))

        val parsedV4 = IpUdpPacketCodec.parseUdp(rawIpv4, rawIpv4.size)
        assertNotNull(parsedV4)
        parsedV4!!
        assertEquals(4, parsedV4.ipVersion)
        assertEquals(51234, parsedV4.srcPort)
        assertEquals(53, parsedV4.dstPort)
        assertArrayEquals(dnsQuery, parsedV4.payload)

        val v4RespRaw = IpUdpPacketCodec.buildUdpResponse(parsedV4, byteArrayOf(1, 2, 3, 4))
        assertEquals(0, IpUdpPacketCodec.onesComplementChecksum(v4RespRaw, 0, 20, 0))
        val parsedV4Resp = IpUdpPacketCodec.parseUdp(v4RespRaw, v4RespRaw.size)!!
        assertEquals(53, parsedV4Resp.srcPort)
        assertEquals(51234, parsedV4Resp.dstPort)

        // IPv6 request
        val v6Client = ByteArray(16) { if (it == 15) 1 else 0 }
        v6Client[0] = 0xFD.toByte()
        val v6Dns = ByteArray(16) { if (it == 15) 2 else 0 }
        v6Dns[0] = 0xFD.toByte()

        val v6Seed = IpUdpPacketCodec.UdpPacket(
            ipVersion = 6,
            srcAddress = v6Dns,
            dstAddress = v6Client,
            srcPort = 53,
            dstPort = 40000,
            payload = byteArrayOf()
        )
        val rawIpv6 = IpUdpPacketCodec.buildUdpResponse(v6Seed, dnsQuery)
        val parsedV6 = IpUdpPacketCodec.parseUdp(rawIpv6, rawIpv6.size)
        assertNotNull(parsedV6)
        parsedV6!!
        assertEquals(6, parsedV6.ipVersion)
        assertEquals(40000, parsedV6.srcPort)
        assertEquals(53, parsedV6.dstPort)
        assertArrayEquals(dnsQuery, parsedV6.payload)

        // Verify IPv6 UDP checksum over pseudo-header + UDP section validates to 0
        val udpLenV6 = rawIpv6.size - 40
        val pseudoSumV6 = IpUdpPacketCodec.sumBytes(v6Client, 0, 16) +
            IpUdpPacketCodec.sumBytes(v6Dns, 0, 16) +
            udpLenV6 + 17
        assertEquals(0, IpUdpPacketCodec.onesComplementChecksum(rawIpv6, 40, udpLenV6, pseudoSumV6))
    }
}
