package org.xoralundra.annuitybackend.core

import java.time.Clock
import java.time.ZonedDateTime

interface TheClock {
    val clock: Clock
    val now: ZonedDateTime
}

fun TheClock.dateOrNow(dateAsOf: ZonedDateTime? = null): ZonedDateTime = dateAsOf ?: this.now
