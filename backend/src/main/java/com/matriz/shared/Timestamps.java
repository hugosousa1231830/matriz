package com.matriz.shared;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Timestamps written to the database. */
public final class Timestamps {

	private Timestamps() {
	}

	/**
	 * The current time at PostgreSQL's precision (microseconds), so the value
	 * returned after a write is identical to the one read back later.
	 */
	public static Instant now(Clock clock) {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

}
