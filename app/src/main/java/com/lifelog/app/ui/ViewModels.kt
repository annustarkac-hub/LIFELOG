package com.lifelog.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifelog.app.AppContainer
import com.lifelog.app.collect.CollectionOutcome
import com.lifelog.app.collect.CollectionWorker
import com.lifelog.app.data.AndroidSummaryRow
import com.lifelog.app.data.AppSettings
import com.lifelog.app.data.ThemeMode
import com.lifelog.app.data.TimeFormat
import com.lifelog.app.data.db.AppIdentityEntity
import com.lifelog.app.data.db.CollectionCheckpointEntity
import com.lifelog.app.data.db.DeviceProfileEntity
import com.lifelog.app.domain.MetricType
import com.lifelog.app.domain.engine.DaySummary
import com.lifelog.app.domain.engine.IntervalMath
import com.lifelog.app.domain.engine.RangeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Generic load state: no screen ever renders placeholder numbers while loading. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

// ---------------------------------------------------------------- Main / global

data class MainState(
    val settings: AppSettings? = null,
    val hasUsageAccess: Boolean = false,
    val collecting: Boolean = false,
    val lastOutcome: CollectionOutcome? = null,
    val checkpoint: CollectionCheckpointEntity? = null,
    val profile: DeviceProfileEntity? = null,
)

class MainViewModel(private val c: AppContainer) : ViewModel() {
    private val access = MutableStateFlow(c.usageSource.hasUsageAccess())
    private val collecting = MutableStateFlow(false)
    private val outcome = MutableStateFlow<CollectionOutcome?>(null)

    val state: StateFlow<MainState> = combine(
        c.settings.settings, access, collecting, outcome,
        combine(c.repo.meta.checkpointFlow(com.lifelog.app.data.LifelogRepository.EVENTS_COLLECTOR), c.repo.meta.profileFlow()) { a, b -> a to b },
    ) { s, a, col, o, cp ->
        MainState(s, a, col, o, cp.first, cp.second)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MainState())

    /** Called on every resume: Usage Access can change in Settings at any time. */
    fun onResume() {
        access.value = c.usageSource.hasUsageAccess()
        collectNow()
    }

    fun collectNow() {
        if (collecting.value) return
        viewModelScope.launch {
            collecting.value = true
            try {
                outcome.value = c.collector.collect()
            } catch (e: Exception) {
                outcome.value = null
            } finally {
                access.value = c.usageSource.hasUsageAccess()
                collecting.value = false
                c.bumpData()
            }
        }
    }

    fun acceptOnboarding() = viewModelScope.launch { c.settings.setOnboardingAccepted(true) }
    fun setTracking(enabled: Boolean) = viewModelScope.launch {
        c.settings.setTrackingEnabled(enabled)
        if (enabled) { CollectionWorker.schedule(c.appContext); collectNow() } else CollectionWorker.cancel(c.appContext)
    }
    fun setTheme(t: ThemeMode) = viewModelScope.launch { c.settings.setTheme(t) }
    fun setWeekStart(d: DayOfWeek) = viewModelScope.launch { c.settings.setWeekStart(d); c.bumpData() }
    fun setTimeFormat(f: TimeFormat) = viewModelScope.launch { c.settings.setTimeFormat(f) }
    fun setExcluded(pkg: String, excluded: Boolean) = viewModelScope.launch { c.settings.setExcluded(pkg, excluded); c.bumpData() }

    fun setPurchaseDate(date: LocalDate?) = viewModelScope.launch {
        val p = c.repo.profile()
        val info = c.deviceInfo.read()
        val base = p ?: DeviceProfileEntity(
            manufacturer = info.manufacturer, model = info.model, androidRelease = info.androidRelease, apiLevel = info.apiLevel,
            firstLifelogTrackingAtUtc = null, userPurchaseDate = null, purchaseDateSource = null, lastSuccessfulCollectionAtUtc = null,
        )
        c.repo.saveProfile(base.copy(userPurchaseDate = date?.toString(), purchaseDateSource = date?.let { DeviceProfileEntity.PURCHASE_SOURCE_USER }))
    }

    private val _apps = MutableStateFlow<List<AppIdentityEntity>>(emptyList())
    val knownApps = _apps.asStateFlow()
    fun loadKnownApps() = viewModelScope.launch { _apps.value = c.analytics.apps().values.sortedBy { (it.displayName ?: it.packageName).lowercase() } }

    private val _probe = MutableStateFlow<Load<String>?>(null)
    val probe = _probe.asStateFlow()
    fun runProbe() = viewModelScope.launch {
        _probe.value = Load.Loading
        _probe.value = try { Load.Ready(c.probe.run()) } catch (e: Exception) { Load.Failed(e.message ?: e.javaClass.simpleName) }
    }
}

// ---------------------------------------------------------------- Home

data class TopApp(val packageName: String, val label: String?, val ms: Long)

data class HomeData(
    val today: DaySummary,
    val last7: RangeSummary,
    val last30: RangeSummary,
    val lifetime: RangeSummary?,
    val lifetimeSince: LocalDate?,
    val topToday: List<TopApp>,
    val top7: List<TopApp>,
    val labels: Map<String, AppIdentityEntity>,
)

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<Load<HomeData>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(c.dataVersion, c.settings.settings) { _, s -> s }.collect { s -> load(s.excludedPackages) }
        }
    }

    private suspend fun load(excluded: Set<String>) {
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val days30 = c.analytics.days(today.minusDays(29), today, zone, excluded)
            val last7 = com.lifelog.app.domain.engine.Analytics.summarize(days30.takeLast(7))
            val last30 = com.lifelog.app.domain.engine.Analytics.summarize(days30)
            val since = c.analytics.earliestDataDate(zone)
            val lifetime = since?.let { if (it.isBefore(today.minusDays(29))) c.analytics.range(it, today, zone, excluded) else last30 }
            val labels = c.analytics.apps()
            fun top(m: Map<String, Long>) = m.entries.sortedByDescending { it.value }.take(5).map { TopApp(it.key, labels[it.key]?.displayName, it.value) }
            _state.value = Load.Ready(HomeData(days30.last(), last7, last30, lifetime, since, top(days30.last().perAppMs), top(last7.perAppMs), labels))
        } catch (e: Exception) {
            _state.value = Load.Failed(e.message ?: "Could not load dashboard")
        }
    }
}

// ---------------------------------------------------------------- Apps

enum class AppsPeriod(val label: String) { TODAY("Today"), WEEK("7 days"), MONTH("30 days"), LIFETIME("Lifetime") }
enum class AppsSort(val label: String) { USAGE("Most used"), NAME("Name") }

data class AppRow(val packageName: String, val label: String?, val installed: Boolean, val periodMs: Long, val lifetimeMs: Long, val excluded: Boolean)
data class AppsData(val rows: List<AppRow>, val period: AppsPeriod, val sort: AppsSort, val query: String, val summaryDaysInLifetime: Int)

class AppsViewModel(private val c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(AppsPeriod.WEEK)
    private val sort = MutableStateFlow(AppsSort.USAGE)
    private val query = MutableStateFlow("")
    private val _state = MutableStateFlow<Load<AppsData>>(Load.Loading)
    val state = _state.asStateFlow()
    private var cache: Triple<Map<AppsPeriod, Map<String, Long>>, Map<String, AppIdentityEntity>, Int>? = null
    private var excluded: Set<String> = emptySet()

    init {
        viewModelScope.launch {
            combine(c.dataVersion, c.settings.settings) { _, s -> s }.collect { s -> excluded = s.excludedPackages; reload() }
        }
        viewModelScope.launch { combine(period, sort, query) { _, _, _ -> }.collect { publish() } }
    }

    fun setPeriod(p: AppsPeriod) { period.value = p }
    fun setSort(s: AppsSort) { sort.value = s }
    fun setQuery(q: String) { query.value = q }

    private suspend fun reload() {
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            // Per-app values include excluded apps here so they can still be found and re-included.
            val days30 = c.analytics.days(today.minusDays(29), today, zone, emptySet())
            val since = c.analytics.earliestDataDate(zone)
            val life = if (since != null && since.isBefore(today.minusDays(29))) c.analytics.range(since, today, zone, emptySet()) else com.lifelog.app.domain.engine.Analytics.summarize(days30)
            fun sum(d: List<DaySummary>) = com.lifelog.app.domain.engine.Analytics.summarize(d).perAppMs
            cache = Triple(
                mapOf(
                    AppsPeriod.TODAY to sum(days30.takeLast(1)), AppsPeriod.WEEK to sum(days30.takeLast(7)),
                    AppsPeriod.MONTH to sum(days30), AppsPeriod.LIFETIME to life.perAppMs,
                ),
                c.analytics.apps(), life.summaryOnlyDays,
            )
            publish()
        } catch (e: Exception) {
            _state.value = Load.Failed(e.message ?: "Could not load apps")
        }
    }

    private fun publish() {
        val (byPeriod, apps, summaryDays) = cache ?: return
        val life = byPeriod[AppsPeriod.LIFETIME].orEmpty()
        val cur = byPeriod[period.value].orEmpty()
        val pkgs = (apps.keys + life.keys)
        val q = query.value.trim().lowercase()
        var rows = pkgs.map { p ->
            val a = apps[p]
            AppRow(p, a?.displayName, a?.isCurrentlyInstalled ?: false, cur[p] ?: 0, life[p] ?: 0, p in excluded)
        }.filter { it.lifetimeMs > 0 || it.periodMs > 0 }
            .filter { q.isEmpty() || it.packageName.lowercase().contains(q) || (it.label?.lowercase()?.contains(q) ?: false) }
        rows = when (sort.value) {
            AppsSort.USAGE -> rows.sortedWith(compareByDescending<AppRow> { it.periodMs }.thenByDescending { it.lifetimeMs })
            AppsSort.NAME -> rows.sortedBy { (it.label ?: it.packageName).lowercase() }
        }
        _state.value = Load.Ready(AppsData(rows, period.value, sort.value, query.value, summaryDays))
    }
}

// ---------------------------------------------------------------- App detail

data class AppDetailData(
    val packageName: String,
    val app: AppIdentityEntity?,
    val days: List<DaySummary>,
    val last30: RangeSummary,
    val lifetime: RangeSummary,
    val lifetimeSince: LocalDate?,
    val monthly: List<AndroidSummaryRow>,
)

class AppDetailViewModel(private val c: AppContainer, private val pkg: String) : ViewModel() {
    private val _state = MutableStateFlow<Load<AppDetailData>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch { c.dataVersion.collect { load() } }
    }

    private suspend fun load() {
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val days = c.analytics.days(today.minusDays(29), today, zone, emptySet(), pkg)
            val since = c.analytics.earliestDataDate(zone)
            val last30 = com.lifelog.app.domain.engine.Analytics.summarize(days)
            val life = if (since != null && since.isBefore(today.minusDays(29))) c.analytics.range(since, today, zone, emptySet(), pkg) else last30
            val monthly = c.analytics.androidSummaries(MetricType.API_APP_MONTHLY).filter { it.packageName == pkg }.sortedByDescending { it.start }
            _state.value = Load.Ready(AppDetailData(pkg, c.analytics.apps()[pkg], days, last30, life, since, monthly))
        } catch (e: Exception) {
            _state.value = Load.Failed(e.message ?: "Could not load app")
        }
    }
}

// ---------------------------------------------------------------- Timeline

enum class TimelineType(val label: String) { APP("App sessions"), SCREEN("Screen"), LOCK("Lock/unlock"), POWER("Startup/shutdown"), BATTERY("Battery samples") }

data class TimelineItem(
    val time: Long,
    val end: Long?,
    val type: TimelineType,
    val title: String,
    val packageName: String?,
    val source: String,
    val historical: Boolean,
)

data class TimelineData(val from: LocalDate, val days: Int, val types: Set<TimelineType>, val app: String?, val items: List<TimelineItem>, val appChoices: List<AppIdentityEntity>)

class TimelineViewModel(private val c: AppContainer) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now())
    private val span = MutableStateFlow(1)
    private val types = MutableStateFlow(TimelineType.entries.toSet())
    private val app = MutableStateFlow<String?>(null)
    private val _state = MutableStateFlow<Load<TimelineData>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(c.dataVersion, date, span, types, app) { _, d, s, t, a -> Quad(d, s, t, a) }.collect { load(it) }
        }
    }

    private data class Quad(val d: LocalDate, val s: Int, val t: Set<TimelineType>, val a: String?)

    fun shift(days: Int) { date.value = date.value.plusDays(days.toLong()) }
    fun setDate(d: LocalDate) { date.value = d }
    fun setSpan(d: Int) { span.value = d }
    fun toggle(t: TimelineType) { types.value = if (t in types.value) types.value - t else types.value + t }
    fun setApp(p: String?) { app.value = p }

    private suspend fun load(q: Quad) {
        try {
            val zone = ZoneId.systemDefault()
            val to = q.d
            val from = q.d.minusDays((q.s - 1).toLong())
            val start = IntervalMath.dayBounds(from, zone).start
            val end = IntervalMath.dayBounds(to, zone).end
            val apps = c.analytics.apps()
            val firstTracking = c.repo.profile()?.firstLifelogTrackingAtUtc
            val items = ArrayList<TimelineItem>()
            if (TimelineType.APP in q.t) {
                c.analytics.sessions(start, end, q.a).forEach {
                    val p = it.packageName ?: return@forEach
                    items += TimelineItem(it.start, it.end, TimelineType.APP, apps[p]?.displayName ?: p, p, "UsageStatsManager.queryEvents (ACTIVITY_RESUMED → PAUSED/STOPPED)", it.isHistorical)
                }
            }
            if (q.a == null) {
                c.analytics.systemEvents(start, end).forEach { e ->
                    val (type, title) = when (e.eventType) {
                        "SCREEN_INTERACTIVE" -> TimelineType.SCREEN to "Screen became interactive"
                        "SCREEN_NON_INTERACTIVE" -> TimelineType.SCREEN to "Screen became non-interactive"
                        "KEYGUARD_HIDDEN" -> TimelineType.LOCK to "Keyguard hidden (unlocked)"
                        "KEYGUARD_SHOWN" -> TimelineType.LOCK to "Keyguard shown"
                        "DEVICE_STARTUP" -> TimelineType.POWER to "Android runtime started"
                        "DEVICE_SHUTDOWN" -> TimelineType.POWER to "Android runtime shut down"
                        else -> return@forEach
                    }
                    if (type in q.t) items += TimelineItem(e.eventTimeUtc, null, type, title, null, "${e.sourceApi} (event ${e.sourceEventType})", firstTracking != null && e.eventTimeUtc < firstTracking)
                }
                if (TimelineType.BATTERY in q.t) {
                    c.analytics.battery(start, end).forEach { b ->
                        items += TimelineItem(b.capturedAtUtc, null, TimelineType.BATTERY,
                            "Battery ${b.batteryLevelPercent?.let { "$it%" } ?: "unknown"} · ${b.chargingState?.lowercase()?.replace('_', ' ') ?: "state unknown"}",
                            null, "${b.sourceApi} — point sample when LIFELOG ran", false)
                    }
                }
            }
            items.sortByDescending { it.time }
            _state.value = Load.Ready(TimelineData(from, q.s, q.t, q.a, items, apps.values.sortedBy { (it.displayName ?: it.packageName).lowercase() }))
        } catch (e: Exception) {
            _state.value = Load.Failed(e.message ?: "Could not load timeline")
        }
    }
}

// ---------------------------------------------------------------- Reports

enum class ReportPeriod(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year"), CUSTOM("Custom") }

data class ReportData(
    val period: ReportPeriod,
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DaySummary>,
    val summary: RangeSummary,
    val previous: RangeSummary,
    val heatmap: Array<LongArray>,
    val labels: Map<String, AppIdentityEntity>,
    val yearlyScreen: List<AndroidSummaryRow>,
    val yearlyApps: List<AndroidSummaryRow>,
    val monthlyScreen: List<AndroidSummaryRow>,
)

class ReportsViewModel(private val c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(ReportPeriod.WEEK)
    private val anchor = MutableStateFlow(LocalDate.now())
    private val custom = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)
    private val _state = MutableStateFlow<Load<ReportData>>(Load.Loading)
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(c.dataVersion, c.settings.settings, period, anchor, custom) { _, s, p, a, cu -> listOf(s, p, a, cu) }.collect {
                load(it[0] as AppSettings, it[1] as ReportPeriod, it[2] as LocalDate, @Suppress("UNCHECKED_CAST") (it[3] as Pair<LocalDate, LocalDate>?))
            }
        }
    }

    fun setPeriod(p: ReportPeriod) { period.value = p }
    fun shift(n: Int) {
        anchor.value = when (period.value) {
            ReportPeriod.DAY, ReportPeriod.CUSTOM -> anchor.value.plusDays(n.toLong())
            ReportPeriod.WEEK -> anchor.value.plusWeeks(n.toLong())
            ReportPeriod.MONTH -> anchor.value.plusMonths(n.toLong())
            ReportPeriod.YEAR -> anchor.value.plusYears(n.toLong())
        }
    }
    fun setCustom(from: LocalDate, to: LocalDate) { custom.value = if (from <= to) from to to else to to from; period.value = ReportPeriod.CUSTOM }

    fun bounds(p: ReportPeriod, a: LocalDate, weekStart: DayOfWeek, cu: Pair<LocalDate, LocalDate>?): Pair<LocalDate, LocalDate> = when (p) {
        ReportPeriod.DAY -> a to a
        ReportPeriod.WEEK -> a.with(TemporalAdjusters.previousOrSame(weekStart)).let { it to it.plusDays(6) }
        ReportPeriod.MONTH -> a.withDayOfMonth(1).let { it to it.plusMonths(1).minusDays(1) }
        ReportPeriod.YEAR -> a.withDayOfYear(1).let { it to it.plusYears(1).minusDays(1) }
        ReportPeriod.CUSTOM -> cu ?: (a.minusDays(6) to a)
    }

    private suspend fun load(s: AppSettings, p: ReportPeriod, a: LocalDate, cu: Pair<LocalDate, LocalDate>?) {
        try {
            val zone = ZoneId.systemDefault()
            val (from, to) = bounds(p, a, s.weekStart, cu)
            val len = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1
            val days = c.analytics.days(from, to, zone, s.excludedPackages)
            val prev = c.analytics.range(from.minusDays(len), from.minusDays(1), zone, s.excludedPackages)
            _state.value = Load.Ready(
                ReportData(
                    p, from, to, days, com.lifelog.app.domain.engine.Analytics.summarize(days), prev,
                    c.analytics.heatmap(from, to, zone, s.excludedPackages), c.analytics.apps(),
                    c.analytics.androidSummaries(MetricType.API_SCREEN_YEARLY).sortedByDescending { it.start },
                    c.analytics.androidSummaries(MetricType.API_APP_YEARLY).filter { it.packageName !in s.excludedPackages },
                    c.analytics.androidSummaries(MetricType.API_SCREEN_MONTHLY).sortedByDescending { it.start },
                )
            )
        } catch (e: Exception) {
            _state.value = Load.Failed(e.message ?: "Could not load report")
        }
    }
}
