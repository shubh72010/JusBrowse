package com.jusdots.jusbrowse.data.models

data class DnsProvider(
    val name: String,
    val description: String,
    val type: String,
    val primaryIp: String,
    val dohUrl: String,
    val isFamilyFilter: Boolean = false,
    val isMalwareFilter: Boolean = false
)

object DnsPresets {
    val providers = listOf(
        DnsProvider("Google Public DNS", "Stable & global; no filtering", "Standard", "8.8.8.8", "https://dns.google/resolve"),
        DnsProvider("Cloudflare DNS", "Ultra-fast, privacy-forward", "Fastest", "1.1.1.1", "https://cloudflare-dns.com/dns-query"),
        DnsProvider("Cloudflare Security", "Blocks malware", "Secure", "1.1.1.2", "https://security.cloudflare-dns.com/dns-query", isMalwareFilter = true),
        DnsProvider("Cloudflare Family", "Blocks malware + adult", "Family", "1.1.1.3", "https://family.cloudflare-dns.com/dns-query", isFamilyFilter = true, isMalwareFilter = true),
        DnsProvider("Quad9", "Malware protection; privacy focused", "Security", "9.9.9.9", "https://dns.quad9.net/dns-query", isMalwareFilter = true),
        DnsProvider("OpenDNS Home", "Classic Cisco resolver", "Standard", "208.67.222.222", "https://doh.opendns.com/dns-query"),
        DnsProvider("OpenDNS FamilyShield", "Pre-configured adult filter", "Family", "208.67.222.123", "https://doh.familyshield.opendns.com/dns-query", isFamilyFilter = true),
        DnsProvider("AdGuard Default", "Blocks ads & tracking", "Ads & Trackers", "94.140.14.14", "https://dns.adguard-dns.com/dns-query", isMalwareFilter = true),
        DnsProvider("AdGuard Family", "Ads + adult content block", "Family", "94.140.14.15", "https://family.adguard-dns.com/dns-query", isFamilyFilter = true, isMalwareFilter = true),
        DnsProvider("AdGuard Unfiltered", "No filtering", "Unfiltered", "94.140.14.140", "https://unfiltered.adguard-dns.com/dns-query"),
        DnsProvider("NextDNS", "Full custom filtering", "Custom", "45.90.28.0", "https://dns.nextdns.io"),
        DnsProvider("CleanBrowsing Security", "Blocks malware + phishing", "Secure", "185.228.168.9", "https://doh.cleanbrowsing.org/doh/security-filter/", isMalwareFilter = true),
        DnsProvider("CleanBrowsing Adult", "Blocks adult content", "Secure", "185.228.168.10", "https://doh.cleanbrowsing.org/doh/adult-filter/", isFamilyFilter = true),
        DnsProvider("CleanBrowsing Family", "Family safe filtering", "Family", "185.228.168.168", "https://doh.cleanbrowsing.org/doh/family-filter/", isFamilyFilter = true, isMalwareFilter = true),
        DnsProvider("ControlD Unfiltered", "Standard", "Standard", "76.76.2.0", "https://freedns.controld.com/p0"),
        DnsProvider("ControlD Malware", "Blocks ads + malware", "Secure", "76.76.19.19", "https://freedns.controld.com/p1", isMalwareFilter = true),
        DnsProvider("ControlD Family", "Family filter", "Family", "76.76.2.3", "https://freedns.controld.com/p3", isFamilyFilter = true, isMalwareFilter = true),
        DnsProvider("Alternate DNS", "Ads blocker", "Standard", "76.76.19.19", "https://dns.alternate-dns.com/dns-query", isMalwareFilter = true)
    )
    
    fun findBestMatches(query: String): List<DnsProvider> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return providers
        
        return providers.map { provider ->
            var score = 0
            if (provider.dohUrl.lowercase() == q || provider.primaryIp == q) score += 100
            else if (provider.name.lowercase().startsWith(q)) score += 50
            else if (provider.primaryIp.startsWith(q)) score += 40
            else if (provider.dohUrl.lowercase().contains(q)) score += 30
            else if (provider.name.lowercase().contains(q)) score += 20
            else if (provider.type.lowercase().contains(q)) score += 10
            Pair(provider, score)
        }
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
        .map { it.first }
    }
}
