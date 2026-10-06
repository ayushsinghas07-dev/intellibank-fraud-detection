package com.intellibank.util.dsa;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * HashSet-based duplicate transaction detector.
 * Generates composite hash keys of (accountId + amount + merchant + 2-minute-time-bucket)
 * for instant O(1) duplicate transaction detection.
 */
public class DuplicateHashSet {
    private final Set<String> hashSet = new HashSet<>();

    public static String buildHashKey(Long accountId, BigDecimal amount, String merchant, long epochSeconds, int windowMinutes) {
        long bucket = epochSeconds / (windowMinutes * 60L);
        String merchantClean = (merchant == null) ? "UNKNOWN" : merchant.trim().toLowerCase();
        return accountId + ":" + amount.stripTrailingZeros().toPlainString() + ":" + merchantClean + ":" + bucket;
    }

    public synchronized boolean isDuplicate(String hashKey) {
        return hashSet.contains(hashKey);
    }

    public synchronized boolean addIfAbsent(String hashKey) {
        return hashSet.add(hashKey);
    }

    public synchronized void clear() {
        hashSet.clear();
    }
}
