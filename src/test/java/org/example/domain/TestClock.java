package org.example.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/** Deterministic clock for tests: fixed, or stepping forward on every read. */
public class TestClock extends Clock {
    public static final Instant START = Instant.parse("2026-01-01T10:00:00Z");

    private final AtomicReference<Instant> now = new AtomicReference<>(START);
    private final Duration step;

    private TestClock(Duration step) {
        this.step = step;
    }

    public static TestClock fixed() {
        return new TestClock(Duration.ZERO);
    }

    public static TestClock stepping(Duration step) {
        return new TestClock(step);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now.getAndUpdate(i -> i.plus(step));
    }
}
