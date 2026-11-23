package com.example.pguard.engine.dns

import com.example.pguard.engine.filter.ContentFilter
import java.util.Locale

sealed interface DnsDecision {
    /** Forward to standard public resolver (1.1.1.1 / 8.8.8.8). */
    data object AllowUnfiltered : DnsDecision

    /** Forward to adult-filtering resolver (Cloudflare for Families 1.1.1.3 / 1.0.0.3). */
    data object AllowFamilyFiltered : DnsDecision

    /** Synthesize an immediate NXDOMAIN reply inside the local VPN. */
    data class Block(
        val reason: String,
        val recordInterception: Boolean = true
    ) : DnsDecision

    /** Resolve [safeHost] via 1.1.1.3 and rewrite its A/AAAA records for SafeSearch enforcement. */
    data class SafeSearchRedirect(val safeHost: String) : DnsDecision
}

data class DnsFilterConfig(
    val blockAdultContent: Boolean,
    val blockTikTok: Boolean,
    val customBlocklist: Collection<String>,
    val whitelist: Collection<String>
)

/**
 * Pure-Kotlin DNS decision engine shared by [com.example.pguard.engine.vpn.PGuardVpnService]
 * and JVM unit tests.
 */
class DnsDomainFilter(
    private val adultDomains: AdultDomainDatabase,
    private val contentFilter: ContentFilter = ContentFilter.Default
) {

    fun evaluate(rawDomain: String, config: DnsFilterConfig): DnsDecision {
        val domain = rawDomain.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (domain.isBlank()) return DnsDecision.AllowUnfiltered

        // Opt browsers out of bypassing local DNS via DoH / DDR discovery
        if (domain == "use-application-dns.net" || domain == "_dns.resolver.arpa") {
            return DnsDecision.Block(reason = "doh-opt-out", recordInterception = false)
        }

        // User whitelist overrides adult + custom blocklists
        if (contentFilter.matchesDomainEntries(domain, config.whitelist)) {
            return DnsDecision.AllowUnfiltered
        }

        // Custom domain blocklist
        if (contentFilter.matchesDomainEntries(domain, config.customBlocklist)) {
            return DnsDecision.Block(reason = "custom-blocklist")
        }

        // TikTok full-network block
        if (config.blockTikTok && matchesAnyRoot(domain, TIKTOK_DOMAINS)) {
            return DnsDecision.Block(reason = "tiktok")
        }

        // Adult content block + SafeSearch enforcement + family DNS fallback
        if (config.blockAdultContent) {
            if (adultDomains.containsDomain(domain) || contentFilter.isAdultDomain(domain)) {
                return DnsDecision.Block(reason = "adult-domain")
            }
            SAFE_SEARCH_HOSTS[domain]?.let { safeHost ->
                return DnsDecision.SafeSearchRedirect(safeHost)
            }
            return DnsDecision.AllowFamilyFiltered
        }

        return DnsDecision.AllowUnfiltered
    }

    private fun matchesAnyRoot(domain: String, roots: Set<String>): Boolean {
        return roots.any { root -> domain == root || domain.endsWith(".$root") }
    }

    companion object {
        private val TIKTOK_DOMAINS = setOf(
            "tiktok.com",
            "tiktokv.com",
            "tiktokcdn.com",
            "tiktokcdn-us.com",
            "musical.ly",
            "tik-tokapi.com",
            "byteoversea.com"
        )

        private val SAFE_SEARCH_HOSTS = mapOf(
            "google.com" to "forcesafesearch.google.com",
            "www.google.com" to "forcesafesearch.google.com",
            "bing.com" to "strict.bing.com",
            "www.bing.com" to "strict.bing.com",
            "duckduckgo.com" to "safe.duckduckgo.com",
            "www.duckduckgo.com" to "safe.duckduckgo.com"
        )
    }
}
