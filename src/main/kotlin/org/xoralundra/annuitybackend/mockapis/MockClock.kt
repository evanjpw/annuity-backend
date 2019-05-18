package org.xoralundra.annuitybackend.mockapis

import org.xoralundra.annuitybackend.core.TheClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

object MockClock : TheClock {
    var instant: Instant? = null
    var zoneId: ZoneId? = null

    override val clock: Clock get() {
        val defClock = Clock.systemDefaultZone()
        return if (instant == null && zoneId == null) {
            defClock
        } else if (zoneId == null) {
            val defZoneId = defClock.zone
            Clock.fixed(instant, defZoneId)
        } else if (instant == null) {
            defClock.withZone(zoneId)
        } else {
            Clock.fixed(instant, zoneId)
        }
    }

    override val now: ZonedDateTime get() = ZonedDateTime.now(clock)
}
