package com.lifelog.app.collect

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lifelog.app.data.AnalyticsService
import com.lifelog.app.data.LifelogRepository
import com.lifelog.app.data.backup.BackupCodec
import com.lifelog.app.data.backup.BackupManager
import com.lifelog.app.data.backup.RestoreMode
import com.lifelog.app.data.db.LifelogDatabase
import com.lifelog.app.domain.CoverageKind
import com.lifelog.app.domain.MetricType
import com.lifelog.app.domain.RawUsageEvent
import com.lifelog.app.domain.UsageEventCodes as C
import com.lifelog.app.domain.engine.DaySource
import com.lifelog.app.domain.engine.IntervalMath
import com.lifelog.app.platform.AppBucket
import com.lifelog.app.platform.AppInfo
import com.lifelog.app.platform.AppInfoSource
import com.lifelog.app.platform.BatteryReading
import com.lifelog.app.platform.BatterySource
import com.lifelog.app.platform.BucketInterval
import com.lifelog.app.platform.Clock
import com.lifelog.app.platform.DeviceInfo
import com.lifelog.app.platform.DeviceInfoSource
import com.lifelog.app.platform.ScreenBucket
import com.lifelog.app.platform.UsageStatsSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId

/** Fake Android: an event log with a retention window, plus optional aggregate buckets. */
class FakeUsage : UsageStatsSource {
    var granted = true
    var retentionStart = 0L
    val events = mutableListOf<RawUsageEvent>()
    val dailyBuckets = mutableListOf<AppBucket>()
    val screenDaily = mutableListOf<ScreenBucket>()
    var queries = mutableListOf<Pair<Long, Long>>()
    override fun hasUsageAccess() = granted
    override fun queryEvents(begin: Long, end: Long): List<RawUsageEvent>? {
        if (!granted) return null
        queries += begin to end
        return events.filter { it.timestampUtc >= maxOf(begin, retentionStart) && it.timestampUtc < end }
    }
    override fun queryAppBuckets(interval: BucketInterval, begin: Long, end: Long) =
        if (interval == BucketInterval.DAILY) dailyBuckets.filter { it.bucketEnd > begin && it.bucketStart < end } else emptyList()
    override fun queryScreenBuckets(interval: BucketInterval, begin: Long, end: Long) =
        if (interval == BucketInterval.DAILY) screenDaily.filter { it.bucketEnd > begin && it.bucketStart < end } else emptyList()
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CollectorIntegrationTest {
    private val zone = ZoneId.of("UTC")
    private val H = 3_600_000L
    private val day0 = IntervalMath.dayBounds(LocalDate.of(2026, 11, 20), zone).start
    private var now = day0 + 30 * 24 * H
    private lateinit var db: LifelogDatabase
    private lateinit var repo: LifelogRepository
    private val usage = FakeUsage()
    private var tracking = true
    private lateinit var collector: Collector
    private val clock = Clock { now }

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), LifelogDatabase::class.java).allowMainThreadQueries().build()
        repo = LifelogRepository(db)
        collector = Collector(
            repo, usage,
            object : AppInfoSource { override fun info(packageName: String) = AppInfo(packageName, packageName.uppercase(), true) },
            object : BatterySource { override fun read() = BatteryReading(80, "DISCHARGING", "UNPLUGGED") },
            object : DeviceInfoSource { override fun read() = DeviceInfo("TestMaker", "T1", "15", 35); override fun isInteractive() = true },
            clock,
        ) { tracking }
        now = day0 + 10 * H
    }

    @After fun tearDown() = db.close()

    private fun session(pkg: String, s: Long, e: Long) {
        usage.events += RawUsageEvent(s, C.ACTIVITY_RESUMED, pkg, "$pkg.Main")
        usage.events += RawUsageEvent(e, C.ACTIVITY_PAUSED, pkg, "$pkg.Main")
    }

    private fun appTotal(day: LocalDate) = runBlocking {
        AnalyticsService(repo, clock).days(day, day, zone, emptySet()).single()
    }

    @Test fun repeatedCollectionIsIdempotent() = runBlocking {
        session("a", day0 + H, day0 + 2 * H)
        session("b", day0 + 3 * H, day0 + 4 * H)
        collector.collect()
        val first = repo.usage.all().map { it.copy(id = 0, collectedAtUtc = 0, sourceQueryStartUtc = 0, sourceQueryEndUtc = 0) }.toSet()
        repeat(3) { now += 60_000; collector.collect() }
        val after = repo.usage.all().map { it.copy(id = 0, collectedAtUtc = 0, sourceQueryStartUtc = 0, sourceQueryEndUtc = 0) }.toSet()
        assertEquals(first, after)
        assertEquals(2 * H, appTotal(LocalDate.of(2026, 11, 20)).anyAppForegroundMs)
    }

    @Test fun overlappingWindowsAndOpenSessionDoNotDoubleCount() = runBlocking {
        usage.events += RawUsageEvent(day0 + 9 * H, C.ACTIVITY_RESUMED, "a", "a.Main") // still open at first run
        collector.collect()
        assertEquals(0L, appTotal(LocalDate.of(2026, 11, 20)).anyAppForegroundMs)
        assertEquals(day0 + 9 * H, repo.checkpoint()!!.openSinceUtc)
        usage.events += RawUsageEvent(day0 + 11 * H, C.ACTIVITY_PAUSED, "a", "a.Main")
        now = day0 + 12 * H
        collector.collect()
        now = day0 + 13 * H
        collector.collect()
        assertTrue("second query started at or before the open session", usage.queries[1].first <= day0 + 9 * H)
        assertEquals(2 * H, appTotal(LocalDate.of(2026, 11, 20)).anyAppForegroundMs)
    }

    @Test fun revokedAccessPausesWithoutDeleting() = runBlocking {
        session("a", day0 + H, day0 + 2 * H)
        collector.collect()
        val before = repo.usage.count()
        usage.granted = false
        now += 5 * H
        assertEquals(CollectionOutcome.NotGranted, collector.collect())
        assertEquals(before, repo.usage.count())
        assertEquals(1, repo.coverage.allOfKind(CoverageKind.NOT_GRANTED.name).size)
        usage.granted = true
        now += H
        assertTrue(collector.collect() is CollectionOutcome.Success)
    }

    @Test fun trackingDisabledCollectsNothing() = runBlocking {
        tracking = false
        session("a", day0 + H, day0 + 2 * H)
        assertEquals(CollectionOutcome.TrackingDisabled, collector.collect())
        assertEquals(0, repo.usage.count())
    }

    @Test fun firstRunCoverageStartsAtEarliestRetainedEventAndMarksHistorical() = runBlocking {
        usage.retentionStart = day0 - 2 * 24 * H
        session("old", day0 - 30 * 24 * H, day0 - 30 * 24 * H + H) // not retained by "Android"
        session("a", day0 - 24 * H, day0 - 23 * H)                 // retained, before tracking starts
        collector.collect()
        val recs = repo.usage.allOfMetric(MetricType.APP_FOREGROUND.name)
        assertEquals(listOf("a"), recs.map { it.packageName })
        assertTrue("events before first tracking are historical", recs.single().isHistorical)
        assertEquals(day0 - 24 * H, repo.coverage.earliestStart(CoverageKind.EVENTS.name))
    }

    @Test fun dailyBucketsOnlyImportedBeforeEventCoverage() = runBlocking {
        session("a", day0 + H, day0 + 2 * H)
        usage.retentionStart = day0
        usage.dailyBuckets += AppBucket("a", day0 - 24 * H, day0, 3 * H, day0 - 2 * H)  // before coverage -> kept
        usage.dailyBuckets += AppBucket("a", day0, day0 + 24 * H, 7 * H, day0 + 2 * H)  // overlaps coverage -> dropped
        collector.collect()
        val daily = repo.usage.allOfMetric(MetricType.API_APP_DAILY.name)
        assertEquals(1, daily.size); assertEquals(3 * H, daily.single().durationMs)
        val prev = appTotal(LocalDate.of(2026, 11, 19))
        assertEquals(DaySource.ANDROID_DAILY_SUMMARY, prev.source)
        assertEquals(H, appTotal(LocalDate.of(2026, 11, 20)).perAppMs["a"])
    }

    @Test fun backupRoundTripMergeAndReplace() = runBlocking {
        session("a", day0 + H, day0 + 2 * H)
        collector.collect()
        val mgr = BackupManager(repo, clock, "test")
        val out = ByteArrayOutputStream()
        mgr.export(out, "password123".toCharArray())
        val bytes = out.toByteArray()
        val (text, size) = mgr.readText(ByteArrayInputStream(bytes))
        val v = BackupCodec.decodeAndValidate(text, size, "password123".toCharArray())

        // Merge into the same DB: nothing duplicated.
        val countBefore = repo.usage.count()
        mgr.apply(v, RestoreMode.MERGE)
        assertEquals(countBefore, repo.usage.count())
        assertEquals(H, appTotal(LocalDate.of(2026, 11, 20)).anyAppForegroundMs)

        // Delete everything, then replace from backup: data comes back identically.
        repo.deleteAll()
        assertEquals(0, repo.usage.count())
        mgr.apply(v, RestoreMode.REPLACE)
        assertEquals(countBefore, repo.usage.count())
        assertEquals(H, appTotal(LocalDate.of(2026, 11, 20)).anyAppForegroundMs)
        assertEquals("TestMaker", repo.profile()!!.manufacturer)
    }

    @Test fun deleteAllRemovesEveryTable() = runBlocking {
        session("a", day0 + H, day0 + 2 * H)
        collector.collect()
        repo.deleteAll()
        assertTrue(repo.recordCounts().values.all { it == 0 })
        assertEquals(null, repo.profile())
        assertEquals(null, repo.checkpoint())
    }
}
