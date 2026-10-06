package com.intellibank.util.dsa;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Sliding Window algorithm using ArrayDeque for tracking timestamps
 * within a moving time window (e.g. 5 minutes). Used for velocity and
 * rapid consecutive large transaction detection.
 */
public class SlidingWindowTimestampDeque {
    private final Deque<Long> timestamps = new ArrayDeque<>();
    private final long windowMillis;

    public SlidingWindowTimestampDeque(long windowMinutes) {
        this.windowMillis = windowMinutes * 60 * 1000L;
    }

    public synchronized void addTimestamp(long timestampMillis) {
        evictExpired(timestampMillis);
        timestamps.addLast(timestampMillis);
    }

    public synchronized int getCountInWindow(long currentTimestampMillis) {
        evictExpired(currentTimestampMillis);
        return timestamps.size();
    }

    private void evictExpired(long currentTimestampMillis) {
        long cutoff = currentTimestampMillis - windowMillis;
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
    }

    public synchronized void clear() {
        timestamps.clear();
    }
}
