package com.android.CaveArt.xposed

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.LruCache
import com.android.CaveArt.lockscreenkit.LockNotifAction
import com.android.CaveArt.lockscreenkit.LockNotifAppGroup
import com.android.CaveArt.lockscreenkit.LockNotifMessage
import io.github.libxposed.api.XposedModule
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

object CaveArtNotifSystemBridge {

    @Volatile
    private var hooksInstalled = false

    @Volatile
    var listenerService: NotificationListenerService? = null

    @Volatile
    var notifPipelineObj: Any? = null

    @Volatile
    var activityStarterObj: Any? = null

    @Volatile
    var centralSurfacesObj: Any? = null

    @Volatile
    var activeGroups: List<LockNotifAppGroup> = emptyList()
        private set

    private val listeners = CopyOnWriteArrayList<(List<LockNotifAppGroup>) -> Unit>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val DISMISS_SUPPRESS_TTL_MS = 4000L
    private const val RECENT_POST_TTL_MS = 2500L
    private const val PROGRESS_THROTTLE_MS = 110L
    private val workerExecutor = Executors.newSingleThreadExecutor()
    private val dismissedPendingKeys = HashMap<String, Long>()
    private val recentPostedCache = HashMap<String, Pair<StatusBarNotification, Long>>()
    private val recentRemovedCache = HashMap<String, Long>()
    private val refreshLock = Any()
    private var delayedFollowupRunnable: Runnable? = null
    private val hookedHandlerClasses = java.util.Collections.synchronizedSet(HashSet<Class<*>>())

    private val appNameCache = LruCache<String, String>(64)
    private val appIconCache = LruCache<String, Bitmap>(64)
    private val smallIconCache = LruCache<String, Bitmap>(128)
    private val avatarCache = LruCache<String, Bitmap>(48)
    private val pictureCache = LruCache<String, Bitmap>(16)

    private var pendingRefreshRunnable: Runnable? = null
    private var lastRefreshDispatchedMs = 0L
    private val refreshGeneration = AtomicLong(0L)

    fun addListener(listener: (List<LockNotifAppGroup>) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (List<LockNotifAppGroup>) -> Unit) {
        listeners.remove(listener)
    }

    fun installHooks(module: XposedModule, classLoader: ClassLoader) {
        if (hooksInstalled) return
        hooksInstalled = true

        try {
            val nlClass = classLoader.loadClass("com.android.systemui.statusbar.NotificationListener")
            nlClass.declaredConstructors.forEach { ctor ->
                module.hook(ctor).intercept { chain ->
                    val res = chain.proceed()
                    (chain.thisObject as? NotificationListenerService)?.let { svc ->
                        listenerService = svc
                        scheduleRefresh(svc)
                    }
                    res
                }
            }
            listOf(
                "onListenerConnected",
                "onNotificationPosted",
                "onNotificationRemoved",
                "onNotificationRankingUpdate"
            ).forEach { methodName ->
                nlClass.declaredMethods.filter { it.name == methodName }.forEach { m ->
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        (chain.thisObject as? NotificationListenerService)?.let { svc ->
                            listenerService = svc
                            val argSbn = chain.args.firstOrNull() as? StatusBarNotification
                            if (methodName == "onNotificationPosted" && argSbn?.key != null) {
                                val k = argSbn.key
                                synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(k) }
                                synchronized(recentRemovedCache) { recentRemovedCache.remove(k) }
                                synchronized(recentPostedCache) {
                                    recentPostedCache[k] = argSbn to System.currentTimeMillis()
                                }
                            } else if (methodName == "onNotificationRemoved" && argSbn?.key != null) {
                                val k = argSbn.key
                                synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(k) }
                                synchronized(recentPostedCache) { recentPostedCache.remove(k) }
                                synchronized(recentRemovedCache) { recentRemovedCache[k] = System.currentTimeMillis() }
                            }
                            try {
                                val isUrgent = methodName == "onNotificationPosted" ||
                                    methodName == "onNotificationRemoved" ||
                                    methodName == "onListenerConnected"
                                scheduleRefresh(svc, immediate = isUrgent)
                                if (methodName == "onNotificationPosted") {
                                    delayedFollowupRunnable?.let { mainHandler.removeCallbacks(it) }
                                    val follow = Runnable {
                                        delayedFollowupRunnable = null
                                        scheduleRefresh(svc, immediate = false)
                                    }
                                    delayedFollowupRunnable = follow
                                    mainHandler.postDelayed(follow, 80L)
                                }
                            } catch (_: Throwable) {}
                        }
                        res
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            listOf("onListenerConnected", "onNotificationPosted", "onNotificationRemoved").forEach { mName ->
                NotificationListenerService::class.java.declaredMethods.filter { it.name == mName }.forEach { m ->
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        (chain.thisObject as? NotificationListenerService)?.let { svc ->
                            if (listenerService == null) {
                                listenerService = svc
                                scheduleRefresh(svc, immediate = true)
                            }
                        }
                        res
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            val ncClass = classLoader.loadClass("com.android.systemui.statusbar.notification.collection.NotifCollection")
            listOf("onNotificationPosted", "onNotificationRemoved", "onNotificationRankingUpdate").forEach { mName ->
                ncClass.declaredMethods.filter { it.name == mName }.forEach { m ->
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        val argSbn = chain.args.firstOrNull() as? StatusBarNotification
                        if (mName == "onNotificationPosted" && argSbn?.key != null) {
                            val k = argSbn.key
                            synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(k) }
                            synchronized(recentRemovedCache) { recentRemovedCache.remove(k) }
                            synchronized(recentPostedCache) {
                                recentPostedCache[k] = argSbn to System.currentTimeMillis()
                            }
                        } else if (mName == "onNotificationRemoved" && argSbn?.key != null) {
                            val k = argSbn.key
                            synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(k) }
                            synchronized(recentPostedCache) { recentPostedCache.remove(k) }
                            synchronized(recentRemovedCache) { recentRemovedCache[k] = System.currentTimeMillis() }
                        }
                        val ctx = listenerService ?: CaveArtXposedModule.activeClock?.context
                        if (ctx != null) {
                            scheduleRefresh(ctx, immediate = mName != "onNotificationRankingUpdate")
                        }
                        res
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            val pipelineClass = classLoader.loadClass("com.android.systemui.statusbar.notification.collection.NotifPipeline")
            pipelineClass.declaredConstructors.forEach { ctor ->
                module.hook(ctor).intercept { chain ->
                    val res = chain.proceed()
                    notifPipelineObj = chain.thisObject
                    CaveArtXposedModule.activeClock?.context?.let { scheduleRefresh(it) }
                    res
                }
            }
        } catch (_: Throwable) {}

        try {
            val shadeListClass = classLoader.loadClass("com.android.systemui.statusbar.notification.collection.ShadeListBuilder")
            shadeListClass.declaredMethods.filter { it.name == "buildList" }.forEach { m ->
                module.hook(m).intercept { chain ->
                    val res = chain.proceed()
                    try {
                        val ctx = listenerService ?: CaveArtXposedModule.activeClock?.context
                            ?: CaveArtXposedModule.activeRootLayout?.context
                        if (ctx != null) scheduleRefresh(ctx, immediate = false)
                    } catch (_: Throwable) {}
                    res
                }
            }
        } catch (_: Throwable) {}

        try {
            val nlClass = classLoader.loadClass("com.android.systemui.statusbar.NotificationListener")
            nlClass.declaredMethods.filter { it.name == "addNotificationHandler" }.forEach { m ->
                module.hook(m).intercept { chain ->
                    val res = chain.proceed()
                    try {
                        val handlerObj = chain.args.firstOrNull()
                        val handlerCls = handlerObj?.javaClass
                        if (handlerCls != null && hookedHandlerClasses.add(handlerCls)) {
                            listOf(
                                "onNotificationPosted",
                                "onNotificationBatchPosted",
                                "onNotificationRemoved",
                                "onNotificationRankingUpdate"
                            ).forEach { hMethod ->
                                handlerCls.declaredMethods.filter { it.name == hMethod }.forEach { hm ->
                                    module.hook(hm).intercept { hChain ->
                                        val hRes = hChain.proceed()
                                        try {
                                            val argSbn = hChain.args.firstOrNull() as? StatusBarNotification
                                            if (hMethod == "onNotificationPosted" && argSbn?.key != null) {
                                                val k = argSbn.key
                                                synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(k) }
                                                synchronized(recentRemovedCache) { recentRemovedCache.remove(k) }
                                                synchronized(recentPostedCache) {
                                                    recentPostedCache[k] = argSbn to System.currentTimeMillis()
                                                }
                                            } else if (hMethod == "onNotificationRemoved" && argSbn?.key != null) {
                                                val k = argSbn.key
                                                synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(k) }
                                                synchronized(recentPostedCache) { recentPostedCache.remove(k) }
                                                synchronized(recentRemovedCache) {
                                                    recentRemovedCache[k] = System.currentTimeMillis()
                                                }
                                            }
                                            val ctx = listenerService ?: CaveArtXposedModule.activeClock?.context
                                                ?: CaveArtXposedModule.activeRootLayout?.context
                                            if (ctx != null) {
                                                scheduleRefresh(ctx, immediate = hMethod == "onNotificationRemoved")
                                            }
                                        } catch (_: Throwable) {}
                                        hRes
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {}
                    res
                }
            }
        } catch (_: Throwable) {}

        try {
            val entryClass = classLoader.loadClass("com.android.systemui.statusbar.notification.collection.NotificationEntry")
            entryClass.declaredMethods.filter { it.name == "setSbn" }.forEach { m ->
                module.hook(m).intercept { chain ->
                    val res = chain.proceed()
                    try {
                        val sbn = chain.args.firstOrNull() as? StatusBarNotification
                        if (sbn?.key != null) {
                            synchronized(dismissedPendingKeys) { dismissedPendingKeys.remove(sbn.key) }
                            synchronized(recentRemovedCache) { recentRemovedCache.remove(sbn.key) }
                            synchronized(recentPostedCache) {
                                recentPostedCache[sbn.key] = sbn to System.currentTimeMillis()
                            }
                        }
                        val ctx = listenerService ?: CaveArtXposedModule.activeClock?.context
                            ?: CaveArtXposedModule.activeRootLayout?.context
                        if (ctx != null) scheduleRefresh(ctx, immediate = false)
                    } catch (_: Throwable) {}
                    res
                }
            }
        } catch (_: Throwable) {}

        try {
            val liveStoreClass = classLoader.loadClass("com.android.systemui.statusbar.notification.collection.NotifLiveDataStoreImpl")
            liveStoreClass.declaredMethods.filter { it.name == "setActiveNotifList" }.forEach { m ->
                module.hook(m).intercept { chain ->
                    val res = chain.proceed()
                    try {
                        val ctx = listenerService ?: CaveArtXposedModule.activeClock?.context
                            ?: CaveArtXposedModule.activeRootLayout?.context
                        if (ctx != null) scheduleRefresh(ctx, immediate = false)
                    } catch (_: Throwable) {}
                    res
                }
            }
        } catch (_: Throwable) {}

        listOf(
            "com.android.systemui.statusbar.phone.ActivityStarterImpl",
            "com.android.systemui.statusbar.phone.StatusBarActivityStarter"
        ).forEach { starterName ->
            try {
                val cls = classLoader.loadClass(starterName)
                cls.declaredConstructors.forEach { ctor ->
                    module.hook(ctor).intercept { chain ->
                        val res = chain.proceed()
                        if (activityStarterObj == null || starterName.endsWith("ActivityStarterImpl")) {
                            activityStarterObj = chain.thisObject
                        }
                        res
                    }
                }
            } catch (_: Throwable) {}
        }

        listOf(
            "com.android.systemui.statusbar.phone.CentralSurfacesImpl",
            "com.android.systemui.statusbar.phone.StatusBar"
        ).forEach { csName ->
            try {
                val cls = classLoader.loadClass(csName)
                cls.declaredConstructors.forEach { ctor ->
                    module.hook(ctor).intercept { chain ->
                        val res = chain.proceed()
                        centralSurfacesObj = chain.thisObject
                        res
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun scheduleRefresh(context: Context, immediate: Boolean = false) {
        val appCtx = context.applicationContext ?: context
        synchronized(refreshLock) {
            val now = System.currentTimeMillis()
            if (immediate) {
                pendingRefreshRunnable?.let { mainHandler.removeCallbacks(it) }
                val delayMs = if (now - lastRefreshDispatchedMs < 35L) 20L else 0L
                val r = Runnable {
                    synchronized(refreshLock) {
                        pendingRefreshRunnable = null
                        lastRefreshDispatchedMs = System.currentTimeMillis()
                    }
                    refreshNow(appCtx)
                }
                pendingRefreshRunnable = r
                if (delayMs == 0L) mainHandler.post(r) else mainHandler.postDelayed(r, delayMs)
            } else if (pendingRefreshRunnable == null) {
                val elapsed = now - lastRefreshDispatchedMs
                val delayMs = if (elapsed >= PROGRESS_THROTTLE_MS) 12L else (PROGRESS_THROTTLE_MS - elapsed)
                val r = Runnable {
                    synchronized(refreshLock) {
                        pendingRefreshRunnable = null
                        lastRefreshDispatchedMs = System.currentTimeMillis()
                    }
                    refreshNow(appCtx)
                }
                pendingRefreshRunnable = r
                mainHandler.postDelayed(r, delayMs)
            }
        }
    }

    private fun snapshotPipelineEntriesOnMainThread(): List<Pair<StatusBarNotification, NotificationListenerService.Ranking?>>? {
        val pipeline = notifPipelineObj ?: return null
        return try {
            val entries = pipeline.javaClass.getMethod("getAllNotifs").invoke(pipeline) as? Collection<*> ?: return null
            val out = ArrayList<Pair<StatusBarNotification, NotificationListenerService.Ranking?>>(entries.size)
            for (entry in entries) {
                val sbn = entry?.javaClass?.getMethod("getSbn")?.invoke(entry) as? StatusBarNotification ?: continue
                val ranking = try {
                    entry.javaClass.getMethod("getRanking").invoke(entry) as? NotificationListenerService.Ranking
                } catch (_: Throwable) { null }
                out.add(sbn to ranking)
            }
            out
        } catch (_: Throwable) {
            null
        }
    }

    fun refreshNow(context: Context) {
        val appCtx = context.applicationContext ?: context
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { refreshNow(appCtx) }
            return
        }
        val pipelineSnapshot = snapshotPipelineEntriesOnMainThread()
        val gen = refreshGeneration.incrementAndGet()
        workerExecutor.execute {
            val groups = try {
                collectGroupedNotifications(appCtx, pipelineSnapshot)
            } catch (_: Throwable) {
                emptyList()
            }
            mainHandler.post {
                if (gen != refreshGeneration.get()) return@post
                try {
                    activeGroups = groups
                    listeners.forEach { it(groups) }
                    CaveArtXposedModule.onNotificationsChanged(appCtx, groups)
                } catch (_: Throwable) {}
            }
        }
    }

    private fun pruneClearedFromGroup(g: LockNotifAppGroup): LockNotifAppGroup? {
        val kept = g.items.filterNot { it.isClearable }
        if (kept.isEmpty()) return null
        val lead = kept.first()
        return g.copy(
            items = kept,
            latestPostTimeMs = lead.postTimeMs,
            primaryTitle = lead.title,
            primaryText = lead.text,
            primaryIntent = lead.contentIntent,
            avatarIcon = lead.avatarIcon,
            pictureBitmap = lead.pictureBitmap,
            isRedacted = kept.any { it.isRedacted },
            isClearable = false
        )
    }

    fun dismissGroup(context: Context, group: LockNotifAppGroup) {
        val keys = group.items.filter { it.isClearable }.map { it.key }
        if (keys.isEmpty()) return
        val svc = listenerService
        if (svc != null) {
            val now = System.currentTimeMillis()
            synchronized(dismissedPendingKeys) {
                keys.forEach { dismissedPendingKeys[it] = now }
            }
            val remaining = activeGroups.mapNotNull {
                if (it.groupId == group.groupId) pruneClearedFromGroup(it) else it
            }
            activeGroups = remaining
            listeners.forEach { it(remaining) }
            CaveArtXposedModule.onNotificationsChanged(context, remaining)
            keys.forEach { key ->
                try {
                    svc.cancelNotification(key)
                } catch (_: Throwable) {}
            }
        }
        scheduleRefresh(context)
    }

    fun dismissAllClearable(context: Context) {
        val keys = activeGroups.flatMap { it.items }.filter { it.isClearable }.map { it.key }
        if (keys.isEmpty()) return
        val svc = listenerService
        if (svc != null) {
            val now = System.currentTimeMillis()
            synchronized(dismissedPendingKeys) {
                keys.forEach { dismissedPendingKeys[it] = now }
            }
            val remaining = activeGroups.mapNotNull { pruneClearedFromGroup(it) }
            activeGroups = remaining
            listeners.forEach { it(remaining) }
            CaveArtXposedModule.onNotificationsChanged(context, remaining)
            keys.forEach { key ->
                try {
                    svc.cancelNotification(key)
                } catch (_: Throwable) {}
            }
        }
        scheduleRefresh(context)
    }

    fun launchPendingIntent(context: Context, intent: PendingIntent?) {
        if (intent == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !intent.isActivity) {
            try {
                intent.send()
                return
            } catch (_: Throwable) {}
        }

        val starter = activityStarterObj
        if (starter != null) {
            try {
                val m1 = starter.javaClass.methods.firstOrNull {
                    it.name == "postStartActivityDismissingKeyguard" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == PendingIntent::class.java
                }
                if (m1 != null) {
                    m1.invoke(starter, intent)
                    return
                }
                val m2 = starter.javaClass.methods.firstOrNull {
                    it.name == "postStartActivityDismissingKeyguard" &&
                        it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == PendingIntent::class.java &&
                        !it.parameterTypes[1].isPrimitive
                }
                if (m2 != null) {
                    m2.invoke(starter, intent, null)
                    return
                }
            } catch (_: Throwable) {}
        }

        val cs = centralSurfacesObj
        if (cs != null) {
            try {
                val m1 = cs.javaClass.methods.firstOrNull {
                    it.name == "postStartActivityDismissingKeyguard" &&
                        it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == PendingIntent::class.java
                }
                if (m1 != null) {
                    m1.invoke(cs, intent)
                    return
                }
                val m2 = cs.javaClass.methods.firstOrNull {
                    it.name == "postStartActivityDismissingKeyguard" &&
                        it.parameterTypes.size == 2 &&
                        it.parameterTypes[0] == PendingIntent::class.java &&
                        !it.parameterTypes[1].isPrimitive
                }
                if (m2 != null) {
                    m2.invoke(cs, intent, null)
                    return
                }
            } catch (_: Throwable) {}
        }

        try {
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            if (km?.isKeyguardLocked != true) {
                intent.send()
            }
        } catch (_: Throwable) {}
    }

    fun expandNotificationsShade() {
        val cs = centralSurfacesObj ?: return
        try {
            cs.javaClass.methods.firstOrNull {
                it.name == "animateExpandNotificationsPanel" && it.parameterTypes.isEmpty()
            }?.invoke(cs)
        } catch (_: Throwable) {}
    }

    private fun fetchRawEntries(
        nowMs: Long,
        pipelineSnapshot: List<Pair<StatusBarNotification, NotificationListenerService.Ranking?>>?
    ): List<Pair<StatusBarNotification, NotificationListenerService.Ranking?>> {
        val byKey = LinkedHashMap<String, Pair<StatusBarNotification, NotificationListenerService.Ranking?>>()
        var pipelineActive = false

        if (pipelineSnapshot != null) {
            pipelineActive = true
            for ((sbn, ranking) in pipelineSnapshot) {
                val key = sbn.key ?: continue
                byKey[key] = sbn to ranking
            }
        }

        val svc = listenerService
        if (!pipelineActive && svc != null) {
            try {
                val arr = svc.activeNotifications
                if (arr != null) {
                    for (sbn in arr) {
                        val key = sbn?.key ?: continue
                        val removedRecently = synchronized(recentRemovedCache) {
                            val rmAt = recentRemovedCache[key]
                            if (rmAt != null && (nowMs - rmAt) < RECENT_POST_TTL_MS) {
                                sbn.postTime <= rmAt
                            } else {
                                if (rmAt != null) recentRemovedCache.remove(key)
                                false
                            }
                        }
                        if (removedRecently) continue
                        val existing = byKey[key]
                        if (existing == null || sbn.postTime > existing.first.postTime) {
                            byKey[key] = sbn to existing?.second
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        synchronized(recentPostedCache) {
            val it = recentPostedCache.entries.iterator()
            while (it.hasNext()) {
                val e = it.next()
                val age = nowMs - e.value.second
                if (age > RECENT_POST_TTL_MS || age < 0L) {
                    it.remove()
                } else {
                    val sbn = e.value.first
                    val removedAfter = synchronized(recentRemovedCache) {
                        val rmAt = recentRemovedCache[e.key]
                        rmAt != null && (nowMs - rmAt) < RECENT_POST_TTL_MS && sbn.postTime <= rmAt
                    }
                    val existing = byKey[e.key]
                    val canUpdateActive = existing != null && sbn.postTime >= existing.first.postTime
                    if (!removedAfter && (!pipelineActive || canUpdateActive)) {
                        if (existing == null || sbn.postTime >= existing.first.postTime) {
                            byKey[e.key] = sbn to existing?.second
                        }
                    }
                }
            }
        }

        return byKey.values.toList()
    }

    private fun collectGroupedNotifications(
        context: Context,
        pipelineSnapshot: List<Pair<StatusBarNotification, NotificationListenerService.Ranking?>>? = null
    ): List<LockNotifAppGroup> {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val isLocked = km?.isKeyguardLocked == true
        if (isLocked) {
            val showLockNotifs = try {
                Settings.Secure.getInt(context.contentResolver, "lock_screen_show_notifications", 1) != 0
            } catch (_: Throwable) { true }
            if (!showLockNotifs) return emptyList()
        }

        val isTrustUnlocked = try {
            val ksc = CaveArtXposedModule.keyguardStateControllerObj
            (ksc?.javaClass?.getMethod("canDismissLockScreen")?.invoke(ksc) as? Boolean) == true
        } catch (_: Throwable) { false }
        val mustRedactLockscreen = isLocked && (km?.isKeyguardSecure == true) && !isTrustUnlocked

        val allowPrivate = if (mustRedactLockscreen) {
            try {
                Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications", 1) != 0
            } catch (_: Throwable) { true }
        } else true

        val nowMs = System.currentTimeMillis()
        val rawEntries = fetchRawEntries(nowMs, pipelineSnapshot)
        if (rawEntries.isEmpty()) return emptyList()

        val rankingMap = try { listenerService?.currentRanking } catch (_: Throwable) { null }
        val rankingOut = NotificationListenerService.Ranking()

        val groupsWithChildren = HashSet<String>()
        for ((sbn, _) in rawEntries) {
            val isSummary = (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (!isSummary) {
                groupsWithChildren.add(sbn.groupKey)
            }
        }

        val byApp = LinkedHashMap<String, MutableList<Pair<StatusBarNotification, LockNotifMessage>>>()
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        val avatarPx = (40f * density).toInt().coerceIn(40, 112)

        for ((sbn, entryRanking) in rawEntries) {
            val n = sbn.notification ?: continue
            val key = sbn.key ?: continue
            val isDismissed = synchronized(dismissedPendingKeys) {
                val ts = dismissedPendingKeys[key]
                if (ts != null && (nowMs - ts) < DISMISS_SUPPRESS_TTL_MS) {
                    true
                } else {
                    if (ts != null) dismissedPendingKeys.remove(key)
                    false
                }
            }
            if (isDismissed) continue

            val isSummary = (n.flags and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isSummary && groupsWithChildren.contains(sbn.groupKey)) continue
            if (n.category == Notification.CATEGORY_TRANSPORT) continue

            val pkg = sbn.packageName ?: continue
            val extras = n.extras
            val rawProgMax = extras?.getInt(Notification.EXTRA_PROGRESS_MAX, 0) ?: 0
            val rawProgVal = extras?.getInt(Notification.EXTRA_PROGRESS, -1) ?: -1
            val rawProgIndet = extras?.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ?: false
            val template = extras?.getString("android.template").orEmpty()
            val hasProgressMeta = rawProgIndet || rawProgMax > 0 || template.contains("ProgressStyle", ignoreCase = true)
            val isProgressOrInstallerPkg = pkg == "com.android.vending" ||
                pkg == "com.android.providers.downloads" ||
                pkg.contains("packageinstaller")
            val isPromotedOngoing = (n.flags and 0x00040000) != 0 ||
                (extras?.getBoolean("android.requestPromotedOngoing", false) == true)

            val isEssentialOngoing = hasProgressMeta ||
                isProgressOrInstallerPkg ||
                isPromotedOngoing ||
                n.category == Notification.CATEGORY_CALL ||
                n.category == Notification.CATEGORY_ALARM ||
                n.category == Notification.CATEGORY_NAVIGATION ||
                n.category == Notification.CATEGORY_PROGRESS ||
                n.category == "stopwatch"

            val hasRanking = if (entryRanking != null) {
                true
            } else {
                rankingMap != null && rankingMap.getRanking(key, rankingOut)
            }
            val effRanking = entryRanking ?: if (hasRanking) rankingOut else null
            val visOverride = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && effRanking != null) {
                effRanking.lockscreenVisibilityOverride
            } else -1000
            val effectiveVisibility = if (visOverride != -1000) visOverride else n.visibility
            if (isLocked && effectiveVisibility == Notification.VISIBILITY_SECRET) continue

            val importance = effRanking?.importance ?: NotificationManager.IMPORTANCE_DEFAULT
            val isAmbient = effRanking?.isAmbient == true

            if (importance <= NotificationManager.IMPORTANCE_MIN || (isAmbient && !isEssentialOngoing)) continue

            val isSystemBackgroundNoise = !sbn.isClearable && !isEssentialOngoing &&
                (pkg == "android" || pkg == "com.android.systemui")
            if (isSystemBackgroundNoise) continue

            val includeSilent = CaveArtXposedModule.activeClock?.clockConfig?.notificationIncludeSilent ?: true
            if (!includeSilent && importance <= NotificationManager.IMPORTANCE_LOW) {
                if (n.category != Notification.CATEGORY_CALL &&
                    n.category != Notification.CATEGORY_ALARM &&
                    n.category != Notification.CATEGORY_NAVIGATION
                ) continue
            }

            val isBackgroundServiceNoise = (n.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0 &&
                !sbn.isClearable && !isEssentialOngoing && importance <= NotificationManager.IMPORTANCE_LOW
            if (isBackgroundServiceNoise) continue

            val hasSensitiveContent = if (effRanking != null) {
                try {
                    (effRanking.javaClass.getMethod("hasSensitiveContent").invoke(effRanking) as? Boolean) == true
                } catch (_: Throwable) { false }
            } else false

            val appName = resolveAppName(pm, pkg)
            val isRedacted = mustRedactLockscreen && (
                hasSensitiveContent ||
                (!allowPrivate && effectiveVisibility == Notification.VISIBILITY_PRIVATE)
            )

            val (title, text) = if (isRedacted) {
                val pub = n.publicVersion
                if (pub != null) {
                    extractTitleAndText(pub, appName)
                } else {
                    appName to "Contents hidden"
                }
            } else {
                extractTitleAndText(n, appName)
            }

            val progMax = if (!isRedacted) rawProgMax else 0
            val progVal = if (!isRedacted) rawProgVal else -1
            val progIndet = if (!isRedacted) {
                rawProgIndet || (template.contains("ProgressStyle", ignoreCase = true) && rawProgMax <= 0)
            } else false
            val actions = if (!isRedacted) extractActions(n) else emptyList()
            val avHint = if (hasProgressMeta) key else "$key:${sbn.postTime}"
            val avatarBmp = if (!isRedacted) {
                resolveIconBitmap(context, pkg, extractAvatarIcon(n), avatarPx, avatarCache, "av", avHint)
            } else null
            val picBmp = if (!isRedacted) {
                extractPictureBitmap(context, pkg, n, "$key:${sbn.postTime}")
            } else null

            val msg = LockNotifMessage(
                key = key,
                title = title,
                text = text,
                postTimeMs = sbn.postTime,
                contentIntent = n.contentIntent,
                isClearable = sbn.isClearable,
                isRedacted = isRedacted,
                avatarIcon = avatarBmp,
                pictureBitmap = picBmp,
                progress = progVal,
                progressMax = progMax,
                progressIndeterminate = progIndet,
                actions = actions
            )
            val groupId = "$pkg:${sbn.user}"
            byApp.getOrPut(groupId) { mutableListOf() }.add(sbn to msg)
        }

        if (byApp.isEmpty()) return emptyList()

        val smallPx = (24f * density).toInt().coerceIn(24, 64)
        val appPx = (42f * density).toInt().coerceIn(40, 112)

        val result = ArrayList<LockNotifAppGroup>(byApp.size)
        for ((groupId, pairs) in byApp) {
            pairs.sortByDescending { it.second.postTimeMs }
            val leadSbn = pairs.first().first
            val leadMsg = pairs.first().second
            val pkg = leadSbn.packageName
            val appName = resolveAppName(pm, pkg)

            val smHint = if (leadMsg.hasProgress) leadSbn.key else "${leadSbn.key}:${leadMsg.postTimeMs}"
            val smallBmp = resolveIconBitmap(context, pkg, leadSbn.notification.smallIcon, smallPx, smallIconCache, "sm", smHint)
            val appBmp = resolveAppIcon(pm, pkg, appPx)
            val msgs = pairs.map { it.second }

            result.add(
                LockNotifAppGroup(
                    groupId = groupId,
                    packageName = pkg,
                    appName = appName,
                    smallIcon = smallBmp,
                    appIcon = appBmp,
                    avatarIcon = leadMsg.avatarIcon,
                    pictureBitmap = leadMsg.pictureBitmap,
                    accentColor = leadSbn.notification.color,
                    items = msgs,
                    latestPostTimeMs = leadMsg.postTimeMs,
                    primaryTitle = leadMsg.title,
                    primaryText = leadMsg.text,
                    primaryIntent = leadMsg.contentIntent,
                    isRedacted = msgs.any { it.isRedacted },
                    isClearable = msgs.any { it.isClearable }
                )
            )
        }

        result.sortByDescending { it.latestPostTimeMs }
        return result
    }

    @Suppress("DEPRECATION")
    private fun extractTitleAndText(n: Notification, fallbackAppName: String): Pair<String, String> {
        val extras = n.extras ?: return fallbackAppName to ""
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (!messages.isNullOrEmpty()) {
            val lastBundle = messages.lastOrNull() as? Bundle
            if (lastBundle != null) {
                val msgText = lastBundle.getCharSequence("text")?.toString().orEmpty()
                val sender = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    (lastBundle.getParcelable("sender_person") as? Person)?.name?.toString()
                        ?: lastBundle.getCharSequence("sender")?.toString()
                } else {
                    lastBundle.getCharSequence("sender")?.toString()
                }
                val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
                val title = convTitle ?: sender ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: fallbackAppName
                val finalBody = if (convTitle != null && !sender.isNullOrBlank() && sender != convTitle) {
                    "$sender: $msgText"
                } else {
                    msgText
                }
                if (finalBody.isNotBlank()) return title to finalBody
            }
        }

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.takeIf { it.isNotBlank() }
            ?: fallbackAppName
        val bigLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.lastOrNull()?.toString()?.takeIf { it.isNotBlank() }
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: bigLines
            ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: ""
        return title to text
    }

    private fun resolveAppName(pm: android.content.pm.PackageManager, pkg: String): String {
        appNameCache.get(pkg)?.let { return it }
        val resolved = try {
            val ai = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(ai).toString()
        } catch (_: Throwable) {
            pkg.substringAfterLast('.')
        }
        appNameCache.put(pkg, resolved)
        return resolved
    }

    private fun resolveAppIcon(pm: android.content.pm.PackageManager, pkg: String, sizePx: Int): Bitmap? {
        val cacheKey = "$pkg:$sizePx"
        appIconCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        return try {
            val drawable = pm.getApplicationIcon(pkg)
            drawableToBitmap(drawable, sizePx)?.also { appIconCache.put(cacheKey, it) }
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun extractPictureBitmap(context: Context, pkg: String, n: Notification, hint: String): Bitmap? {
        val cacheKey = "pic:$pkg:$hint"
        pictureCache.get(cacheKey)?.let { if (!it.isRecycled) return it }
        val extras = n.extras ?: return null
        return try {
            var src: Bitmap? = extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap
            if (src == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val picIcon = extras.getParcelable(Notification.EXTRA_PICTURE_ICON) as? Icon
                val d = picIcon?.loadDrawable(context)
                if (d != null) {
                    val w = d.intrinsicWidth.coerceIn(64, 360)
                    val h = d.intrinsicHeight.coerceIn(64, 360)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    d.setBounds(0, 0, w, h)
                    d.draw(c)
                    src = bmp
                }
            }
            val raw = src ?: return null
            if (raw.isRecycled) return null
            val swRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && raw.config == Bitmap.Config.HARDWARE) {
                raw.copy(Bitmap.Config.ARGB_8888, false)
            } else raw
            val maxSide = maxOf(swRaw.width, swRaw.height)
            val scaled = if (maxSide > 320) {
                val scale = 320f / maxSide.toFloat()
                Bitmap.createScaledBitmap(swRaw, (swRaw.width * scale).toInt().coerceAtLeast(1), (swRaw.height * scale).toInt().coerceAtLeast(1), true)
            } else if (swRaw === raw) {
                swRaw.copy(Bitmap.Config.ARGB_8888, false)
            } else swRaw
            if (swRaw !== raw && swRaw !== scaled) swRaw.recycle()
            pictureCache.put(cacheKey, scaled)
            scaled
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun extractAvatarIcon(n: Notification): Icon? {
        val extras = n.extras
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && extras != null) {
            val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            if (!messages.isNullOrEmpty()) {
                val lastBundle = messages.lastOrNull() as? Bundle
                val person = lastBundle?.getParcelable("sender_person") as? Person
                if (person?.icon != null) return person.icon
            }
            val msgPerson = extras.getParcelable(Notification.EXTRA_MESSAGING_PERSON) as? Person
            if (msgPerson?.icon != null) return msgPerson.icon
        }
        return n.getLargeIcon()
    }

    private fun extractActions(n: Notification): List<LockNotifAction> {
        val raw = n.actions ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        val out = ArrayList<LockNotifAction>(3)
        for (a in raw) {
            val title = a?.title?.toString()?.trim().orEmpty()
            if (title.isEmpty()) continue
            val remoteInputs: Array<RemoteInput>? = a.remoteInputs
            if (!remoteInputs.isNullOrEmpty()) continue
            out.add(LockNotifAction(title = title, intent = a.actionIntent))
            if (out.size >= 3) break
        }
        return out
    }

    private fun buildIconCacheKey(
        pkg: String,
        icon: Icon,
        sizePx: Int,
        prefix: String,
        stableHint: String
    ): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (icon.type) {
                Icon.TYPE_RESOURCE -> "$prefix:$pkg:res:${icon.resPackage}:${icon.resId}:$sizePx"
                Icon.TYPE_URI, Icon.TYPE_URI_ADAPTIVE_BITMAP -> "$prefix:$pkg:uri:${icon.uri}:$sizePx"
                else -> "$prefix:$pkg:t${icon.type}:$stableHint:$sizePx"
            }
        } else {
            "$prefix:$pkg:$stableHint:$sizePx"
        }
    }

    private fun resolveIconBitmap(
        context: Context,
        pkg: String,
        icon: Icon?,
        sizePx: Int,
        cache: LruCache<String, Bitmap>,
        prefix: String,
        stableHint: String
    ): Bitmap? {
        if (icon == null) return null
        return try {
            val iconKey = buildIconCacheKey(pkg, icon, sizePx, prefix, stableHint)
            cache.get(iconKey)?.let { if (!it.isRecycled) return it }
            val drawable = icon.loadDrawable(context) ?: return null
            drawableToBitmap(drawable, sizePx)?.also { cache.put(iconKey, it) }
        } catch (_: Throwable) {
            null
        }
    }

    private fun drawableToBitmap(drawable: Drawable, sizePx: Int): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null && !drawable.bitmap.isRecycled) {
            val src = drawable.bitmap
            val swSrc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && src.config == Bitmap.Config.HARDWARE) {
                src.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else src
            val result = if (swSrc.width == sizePx && swSrc.height == sizePx) {
                if (swSrc === src) swSrc.copy(Bitmap.Config.ARGB_8888, false) else swSrc
            } else if (swSrc.width == swSrc.height) {
                Bitmap.createScaledBitmap(swSrc, sizePx, sizePx, true)
            } else {
                val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                val scale = maxOf(sizePx / swSrc.width.toFloat(), sizePx / swSrc.height.toFloat())
                val dx = (sizePx - swSrc.width * scale) * 0.5f
                val dy = (sizePx - swSrc.height * scale) * 0.5f
                val m = Matrix().apply {
                    postScale(scale, scale)
                    postTranslate(dx, dy)
                }
                c.drawBitmap(swSrc, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                bmp
            }
            if (swSrc !== src && swSrc !== result) swSrc.recycle()
            return result
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bmp
    }
}
