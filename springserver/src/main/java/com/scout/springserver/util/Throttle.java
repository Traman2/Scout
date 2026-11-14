package com.scout.springserver.util;

/**
 * Serializes calls to at most one per interval, app-wide for whichever client
 * owns the instance — same module-level `lastRequestAt` throttle the
 * Nominatim and Overpass clients use in the Express server.
 */
public final class Throttle {

    private final long minIntervalMs;
    private long lastRequestAt = 0;

    public Throttle(long minIntervalMs) {
        this.minIntervalMs = minIntervalMs;
    }

    public synchronized void acquire() {
        long elapsed = System.currentTimeMillis() - lastRequestAt;
        long wait = elapsed < minIntervalMs ? minIntervalMs - elapsed : 0;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while throttling", e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }
}
