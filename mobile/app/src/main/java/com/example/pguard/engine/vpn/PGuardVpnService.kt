package com.example.pguard.engine.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.pguard.MainActivity
import com.example.pguard.R
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accountability.SecurityAlertReporter
import com.example.pguard.engine.dns.AdultDomainDatabase
import com.example.pguard.engine.dns.DnsDecision
import com.example.pguard.engine.dns.DnsDomainFilter
import com.example.pguard.engine.dns.DnsFilterConfig
import com.example.pguard.engine.dns.DnsPacketCodec
import com.example.pguard.engine.dns.DnsQuestion
import com.example.pguard.engine.dns.DnsResponseCache
import com.example.pguard.presentation.habits.HabitsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Split-tunnel local [VpnService] that intercepts ONLY DNS traffic (port 53) to block
 * adult/custom domains across every browser and in-app WebView without routing other traffic.
 */
class PGuardVpnService : VpnService() {

    companion object {
        private const val TAG = "PGuardVpnService"
        private const val CHANNEL_ID = "pguard_dns_vpn"
        private const val NOTIFICATION_ID = 4102
        private const val DNS_PORT = 53
        private const val MTU = 1500
        private const val UPSTREAM_TIMEOUT_MS = 1200
        private const val MAX_SELF_HEAL_RESTARTS = 5
        private const val INTERCEPTION_DEDUP_MS = 5000L
        private const val MAX_INTERCEPTION_CACHE = 512

        const val ACTION_START = "com.example.pguard.vpn.ACTION_START"
        const val ACTION_STOP = "com.example.pguard.vpn.ACTION_STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun needsPermission(context: Context): Boolean = prepare(context) != null

        fun start(context: Context) {
            val intent = Intent(context, PGuardVpnService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PGuardVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunOutput: FileOutputStream? = null
    private val tunWriteLock = Any()
    private var readerThread: Thread? = null
    private var workerPool: ExecutorService? = null

    @Volatile
    private var running = false

    private val queryIdSeq = AtomicInteger(1000)
    private val recentInterceptions = ConcurrentHashMap<String, Long>()
    private val dnsCache = DnsResponseCache()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var consecutiveRestartFailures = 0
    private var restartRunnable: Runnable? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private lateinit var dnsFilter: DnsDomainFilter

    override fun onCreate() {
        super.onCreate()
        BlockingPreferences.loadSettings(applicationContext)
        val db = AdultDomainDatabase.loadFromResources(applicationContext)
        dnsFilter = DnsDomainFilter(db)
        Log.i(TAG, "Initialized local DNS database with ${db.size} adult domains")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopVpn()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification()
        BlockingPreferences.loadSettings(applicationContext)
        startVpnIfNeeded()
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        ensureNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.vpn_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.vpn_notification_channel_desc)
                    setShowBadge(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pguard_globe_shield)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    @Synchronized
    private fun startVpnIfNeeded() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null

        if (running && tunInterface != null) return

        val pfd = establishTun()
        if (pfd == null) {
            Log.e(TAG, "Failed to establish local DNS VPN interface (permission missing or revoked)")
            _isRunning.value = false
            stopForegroundCompat()
            stopSelf()
            return
        }

        dnsCache.clear()
        tunInterface = pfd
        tunOutput = FileOutputStream(pfd.fileDescriptor)
        workerPool = ThreadPoolExecutor(
            2,
            16,
            20L,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(128),
            { runnable -> Thread(runnable, "pguard-dns-worker").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy()
        ).apply {
            allowCoreThreadTimeOut(true)
        }
        running = true
        _isRunning.value = true
        registerNetworkCallback()

        readerThread = Thread({ runPacketLoop(pfd) }, "pguard-tun-reader").apply {
            isDaemon = true
            start()
        }
        Log.i(TAG, "PGuard DNS Shield VPN started")
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                dnsCache.clear()
            }

            override fun onLost(network: Network) {
                dnsCache.clear()
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {}
    }

    @Synchronized
    private fun handleUnexpectedReaderExit(failedPfd: ParcelFileDescriptor) {
        if (tunInterface !== failedPfd) return
        Log.w(TAG, "TUN reader loop exited unexpectedly; resetting broken VPN interface")
        stopVpn()

        if (BlockingPreferences.isDnsVpnEnabled &&
            prepare(applicationContext) == null &&
            consecutiveRestartFailures < MAX_SELF_HEAL_RESTARTS
        ) {
            val delayMs = when (consecutiveRestartFailures) {
                0 -> 1000L
                1 -> 2000L
                else -> 5000L
            }
            consecutiveRestartFailures++
            val runnable = Runnable {
                restartRunnable = null
                if (BlockingPreferences.isDnsVpnEnabled && prepare(applicationContext) == null) {
                    BlockingPreferences.loadSettings(applicationContext)
                    startForegroundWithNotification()
                    startVpnIfNeeded()
                } else {
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            restartRunnable = runnable
            mainHandler.postDelayed(runnable, delayMs)
        } else {
            stopForegroundCompat()
            stopSelf()
        }
    }

    private fun establishTun(): ParcelFileDescriptor? {
        return tryBuildTun(includeIpv6 = true) ?: tryBuildTun(includeIpv6 = false)
    }

    private fun tryBuildTun(includeIpv6: Boolean): ParcelFileDescriptor? {
        return try {
            val builder = Builder()
                .setSession(getString(R.string.vpn_service_label))
                .setMtu(MTU)
                .addAddress("10.111.222.1", 32)
                .addDnsServer("10.111.222.2")
                .addRoute("10.111.222.2", 32)

            if (includeIpv6) {
                builder
                    .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
                    .addDnsServer("fd00:1:fd00:1:fd00:1:fd00:2")
                    .addRoute("fd00:1:fd00:1:fd00:1:fd00:2", 128)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false)
            }

            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not disallow own package on VPN builder: ${e.message}")
            }

            builder.establish()
        } catch (e: Exception) {
            Log.w(TAG, "VPN establish (ipv6=$includeIpv6) failed: ${e.message}")
            null
        }
    }

    private fun runPacketLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buffer = ByteArray(MTU)

        try {
            while (running && tunInterface === pfd) {
                try {
                    val readLen = input.read(buffer)
                    if (readLen <= 0) continue
                    consecutiveRestartFailures = 0

                    val packetCopy = buffer.copyOf(readLen)
                    val udpPacket = IpUdpPacketCodec.parseUdp(packetCopy, readLen) ?: continue
                    if (udpPacket.dstPort != DNS_PORT) continue

                    val pool = workerPool
                    if (pool != null && !pool.isShutdown) {
                        pool.execute { handleDnsUdpPacket(udpPacket) }
                    }
                } catch (e: Exception) {
                    if (running && tunInterface === pfd) {
                        Log.w(TAG, "TUN read loop interrupted: ${e.message}")
                    }
                    break
                }
            }
        } finally {
            if (running && tunInterface === pfd) {
                mainHandler.post { handleUnexpectedReaderExit(pfd) }
            }
        }
    }

    private fun handleDnsUdpPacket(request: IpUdpPacketCodec.UdpPacket) {
        try {
            val dnsPayload = request.payload
            val question = DnsPacketCodec.parseQuery(dnsPayload, dnsPayload.size) ?: return

            val config = DnsFilterConfig(
                blockAdultContent = BlockingPreferences.isAdultBlockingEnabled,
                blockTikTok = BlockingPreferences.isTikTokBlockingEnabled,
                customBlocklist = BlockingPreferences.customBlocklist.toList(),
                whitelist = BlockingPreferences.whitelistedKeywords.toList()
            )

            val responseDns: ByteArray = when (val decision = dnsFilter.evaluate(question.domain, config)) {
                is DnsDecision.Block -> {
                    if (decision.recordInterception) {
                        recordThrottledInterception(question.domain, decision.reason)
                    }
                    DnsPacketCodec.buildNxDomainResponse(dnsPayload, dnsPayload.size, question)
                }

                is DnsDecision.SafeSearchRedirect -> {
                    val modeKey = "safe:${decision.safeHost}"
                    dnsCache.get(question.domain, question.qType, modeKey, question.transactionId)
                        ?: resolveSafeSearch(dnsPayload, question, decision.safeHost)?.also { resolved ->
                            dnsCache.put(question.domain, question.qType, modeKey, resolved)
                        }
                        ?: DnsPacketCodec.buildServFailResponse(dnsPayload, dnsPayload.size, question)
                }

                DnsDecision.AllowFamilyFiltered -> {
                    dnsCache.get(question.domain, question.qType, "family", question.transactionId)
                        ?: forwardDnsUpstream(dnsPayload, primary = "1.1.1.3", fallback = "1.0.0.3")?.also { resolved ->
                            dnsCache.put(question.domain, question.qType, "family", resolved)
                        }
                        ?: DnsPacketCodec.buildServFailResponse(dnsPayload, dnsPayload.size, question)
                }

                DnsDecision.AllowUnfiltered -> {
                    dnsCache.get(question.domain, question.qType, "unfiltered", question.transactionId)
                        ?: forwardDnsUpstream(dnsPayload, primary = "1.1.1.1", fallback = "8.8.8.8")?.also { resolved ->
                            dnsCache.put(question.domain, question.qType, "unfiltered", resolved)
                        }
                        ?: DnsPacketCodec.buildServFailResponse(dnsPayload, dnsPayload.size, question)
                }
            }

            val responseIpPacket = IpUdpPacketCodec.buildUdpResponse(request, responseDns)
            writeToTun(responseIpPacket)
        } catch (t: Throwable) {
            Log.w(TAG, "Error processing DNS packet: ${t.message}")
        }
    }

    private fun resolveSafeSearch(
        originalQuery: ByteArray,
        question: DnsQuestion,
        safeHost: String
    ): ByteArray? {
        if (question.qType == DnsPacketCodec.TYPE_HTTPS || question.qType == DnsPacketCodec.TYPE_SVCB) {
            return DnsPacketCodec.buildNoDataResponse(originalQuery, originalQuery.size, question)
        }
        if (question.qType != DnsPacketCodec.TYPE_A && question.qType != DnsPacketCodec.TYPE_AAAA) {
            return forwardDnsUpstream(originalQuery, primary = "1.1.1.3", fallback = "1.0.0.3")
        }

        val txId = queryIdSeq.incrementAndGet() and 0xFFFF
        val rewrittenQuery = DnsPacketCodec.buildSingleQuery(
            transactionId = txId,
            domain = safeHost,
            qType = question.qType,
            qClass = question.qClass
        )
        val upstreamResp = forwardDnsUpstream(rewrittenQuery, primary = "1.1.1.3", fallback = "1.0.0.3")
            ?: return forwardDnsUpstream(originalQuery, primary = "1.1.1.3", fallback = "1.0.0.3")

        return DnsPacketCodec.rewriteAnswersForQuestion(
            originalQuery = originalQuery,
            originalQuestion = question,
            upstreamResponse = upstreamResp,
            upstreamLength = upstreamResp.size
        ) ?: forwardDnsUpstream(originalQuery, primary = "1.1.1.3", fallback = "1.0.0.3")
    }

    private fun forwardDnsUpstream(
        queryPayload: ByteArray,
        primary: String,
        fallback: String
    ): ByteArray? {
        return queryUpstreamServer(queryPayload, primary) ?: queryUpstreamServer(queryPayload, fallback)
    }

    private fun queryUpstreamServer(queryPayload: ByteArray, serverIp: String): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            if (!protect(socket)) {
                Log.w(TAG, "protect(socket) returned false for $serverIp")
            }
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            val addr = InetAddress.getByName(serverIp)
            val outPacket = DatagramPacket(queryPayload, queryPayload.size, addr, DNS_PORT)
            socket.send(outPacket)

            val respBuf = ByteArray(MTU)
            val inPacket = DatagramPacket(respBuf, respBuf.size)
            socket.receive(inPacket)
            respBuf.copyOf(inPacket.length)
        } catch (_: Exception) {
            null
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    private fun writeToTun(packet: ByteArray) {
        synchronized(tunWriteLock) {
            if (!running) return
            try {
                tunOutput?.write(packet)
            } catch (_: Exception) {}
        }
    }

    private fun recordThrottledInterception(domain: String, reason: String) {
        val now = System.currentTimeMillis()
        if (recentInterceptions.size >= MAX_INTERCEPTION_CACHE) {
            recentInterceptions.clear()
        }
        val prev = recentInterceptions[domain]
        if (prev != null && (now - prev) < INTERCEPTION_DEDUP_MS) return
        recentInterceptions[domain] = now

        Log.w(TAG, "DNS BLOCKED ($reason): $domain")
        HabitsManager.recordBlockInterception(applicationContext)
    }

    @Synchronized
    private fun stopVpn() {
        running = false
        _isRunning.value = false
        unregisterNetworkCallback()
        dnsCache.clear()

        try {
            tunInterface?.close()
        } catch (_: Exception) {}
        tunInterface = null
        tunOutput = null

        try {
            readerThread?.interrupt()
        } catch (_: Exception) {}
        readerThread = null

        try {
            workerPool?.shutdownNow()
        } catch (_: Exception) {}
        workerPool = null

        Log.i(TAG, "PGuard DNS Shield VPN stopped")
    }

    private fun stopForegroundCompat() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (BlockingPreferences.isDnsVpnEnabled && prepare(applicationContext) == null) {
            try {
                start(applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "Could not restart DNS VPN from onTaskRemoved: ${e.message}")
            }
        }
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by system or another VPN app")
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
        if (BlockingPreferences.isDnsVpnEnabled) {
            SecurityAlertReporter.reportIfPartnerLinked(
                applicationContext,
                "permission_revoked",
                "PGuard Network DNS Shield VPN was disconnected or revoked."
            )
        }
        stopVpn()
        stopForegroundCompat()
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        restartRunnable?.let { mainHandler.removeCallbacks(it) }
        restartRunnable = null
        stopVpn()
        stopForegroundCompat()
        super.onDestroy()
    }
}
