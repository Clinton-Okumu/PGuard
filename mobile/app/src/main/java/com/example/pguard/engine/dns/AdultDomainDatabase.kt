package com.example.pguard.engine.dns

import android.content.Context
import com.example.pguard.R
import java.util.Locale

/**
 * Lookup table for known adult domains loaded from the bundled `res/raw/adult_domains.txt` resource.
 * Supports O(labels) exact and parent-subdomain matching without matching lookalikes.
 */
class AdultDomainDatabase(private val domains: Set<String>) {

    val size: Int get() = domains.size

    fun containsDomain(rawHost: String): Boolean {
        val host = rawHost.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (host.isEmpty() || !host.contains('.')) return false

        var candidate = host
        while (true) {
            if (candidate in domains) return true
            val dotIdx = candidate.indexOf('.')
            if (dotIdx < 0 || dotIdx + 1 >= candidate.length) break
            candidate = candidate.substring(dotIdx + 1)
            if (!candidate.contains('.')) break
        }
        return false
    }

    companion object {
        @Volatile
        private var cachedInstance: AdultDomainDatabase? = null

        fun fromLines(lines: Sequence<String>): AdultDomainDatabase {
            val parsed = HashSet<String>(512)
            for (rawLine in lines) {
                val cleaned = rawLine
                    .substringBefore('#')
                    .trim()
                    .lowercase(Locale.ROOT)
                    .trimEnd('.')
                    .removePrefix("www.")
                if (cleaned.isNotEmpty() && cleaned.contains('.') && cleaned.none { it.isWhitespace() }) {
                    parsed.add(cleaned)
                }
            }
            return AdultDomainDatabase(parsed)
        }

        fun loadFromResources(context: Context): AdultDomainDatabase {
            cachedInstance?.let { return it }
            synchronized(this) {
                cachedInstance?.let { return it }
                val loaded = try {
                    context.applicationContext.resources
                        .openRawResource(R.raw.adult_domains)
                        .bufferedReader()
                        .useLines { fromLines(it) }
                } catch (_: Exception) {
                    AdultDomainDatabase(emptySet())
                }
                cachedInstance = loaded
                return loaded
            }
        }
    }
}
