package com.lifelog.app.engine

import com.lifelog.app.domain.AppSession
import com.lifelog.app.domain.Interval
import com.lifelog.app.domain.RawUsageEvent
import com.lifelog.app.domain.SystemEventType
import com.lifelog.app.domain.UsageEventCodes as C
import com.lifelog.app.domain.engine.EventReconstructor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventReconstructorTest {
    private fun ev(t: Long, type: Int, pkg: String? = null, cls: String? = null) = RawUsageEvent(t, type, pkg, cls ?: pkg?.let { "$it.Main" })

    @Test fun resumePausePairBecomesSession() {
        val r = EventReconstructor.reconstruct(listOf(ev(100, C.ACTIVITY_RESUMED, "a"), ev(500, C.ACTIVITY_PAUSED, "a")))
        assertEquals(listOf(AppSession("a", Interval(100, 500))), r.sessions)
        assertNull(r.openSince)
    }

    @Test fun stoppedAlsoClosesSession() {
        val r = EventReconstructor.reconstruct(listOf(ev(100, C.ACTIVITY_RESUMED, "a"), ev(300, C.ACTIVITY_STOPPED, "a")))
        assertEquals(200, r.sessions.single().interval.length)
    }

    @Test fun duplicateEventsAreIdempotent() {
        val base = listOf(ev(100, C.ACTIVITY_RESUMED, "a"), ev(500, C.ACTIVITY_PAUSED, "a"))
        val r = EventReconstructor.reconstruct(base + base + base)
        assertEquals(listOf(AppSession("a", Interval(100, 500))), r.sessions)
    }

    @Test fun outOfOrderDeliveryIsSorted() {
        val r = EventReconstructor.reconstruct(listOf(ev(500, C.ACTIVITY_PAUSED, "a"), ev(100, C.ACTIVITY_RESUMED, "a")))
        assertEquals(Interval(100, 500), r.sessions.single().interval)
    }

    @Test fun missingEndProducesNoDurationAndIsCarriedForward() {
        val r = EventReconstructor.reconstruct(listOf(ev(100, C.ACTIVITY_RESUMED, "a"), ev(200, C.ACTIVITY_RESUMED, "b"), ev(250, C.ACTIVITY_PAUSED, "b")))
        assertEquals(listOf(AppSession("b", Interval(200, 250))), r.sessions)
        assertEquals(100L, r.openSince)
    }

    @Test fun closeWithoutObservedOpenIsIgnored() {
        val r = EventReconstructor.reconstruct(listOf(ev(100, C.ACTIVITY_PAUSED, "a")))
        assertTrue(r.sessions.isEmpty())
    }

    @Test fun multipleActivitiesOfOneAppAreUnioned() {
        val r = EventReconstructor.reconstruct(listOf(
            ev(100, C.ACTIVITY_RESUMED, "a", "A1"), ev(150, C.ACTIVITY_RESUMED, "a", "A2"),
            ev(300, C.ACTIVITY_PAUSED, "a", "A1"), ev(400, C.ACTIVITY_PAUSED, "a", "A2"),
        ))
        assertEquals(listOf(AppSession("a", Interval(100, 400))), r.sessions)
    }

    @Test fun shutdownDiscardsOpenIntervalsPerAndroidDocs() {
        val r = EventReconstructor.reconstruct(listOf(
            ev(100, C.ACTIVITY_RESUMED, "a"), ev(110, C.SCREEN_INTERACTIVE), ev(200, C.DEVICE_SHUTDOWN),
            ev(900, C.DEVICE_STARTUP), ev(950, C.ACTIVITY_PAUSED, "a"),
        ))
        assertTrue("no fabricated session across shutdown", r.sessions.isEmpty())
        assertTrue(r.screenOn.isEmpty())
        assertEquals(2, r.discardedUnmatched)
        assertTrue(r.markers.any { it.type == SystemEventType.DEVICE_SHUTDOWN } && r.markers.any { it.type == SystemEventType.DEVICE_STARTUP })
    }

    @Test fun screenIntervalsAndDoubleInteractive() {
        val r = EventReconstructor.reconstruct(listOf(
            ev(0, C.SCREEN_INTERACTIVE), ev(100, C.SCREEN_INTERACTIVE), ev(160, C.SCREEN_NON_INTERACTIVE),
            ev(300, C.SCREEN_INTERACTIVE),
        ))
        // First INTERACTIVE has no observed end -> discarded; last one is still open.
        assertEquals(listOf(Interval(100, 160)), r.screenOn)
        assertEquals(1, r.discardedUnmatched)
        assertEquals(300L, r.openSince)
    }

    @Test fun screenTimeIsIndependentOfAppEvents() {
        val r = EventReconstructor.reconstruct(listOf(ev(0, C.ACTIVITY_RESUMED, "a"), ev(1000, C.ACTIVITY_PAUSED, "a")))
        assertTrue(r.screenOn.isEmpty())
    }

    @Test fun entriesIgnoreShortActivityTransitions() {
        val s = listOf(Interval(0, 100), Interval(150, 300), Interval(10_000, 10_500))
        assertEquals(2, EventReconstructor.countEntries(s, 2_000))
    }
}
