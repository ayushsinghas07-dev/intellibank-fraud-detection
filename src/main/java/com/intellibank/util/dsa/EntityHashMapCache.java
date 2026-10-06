package com.intellibank.util.dsa;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Custom HashMap-backed entity cache for fast O(1) in-memory lookup
 * of Customer and Account entity profiles during transaction evaluation.
 */
public class EntityHashMapCache<K, V> {
    private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();

    public V get(K key) {
        return cache.get(key);
    }

    public V getOrLoad(K key, Function<K, V> loader) {
        return cache.computeIfAbsent(key, loader);
    }

    public void put(K key, V value) {
        cache.put(key, value);
    }

    public void invalidate(K key) {
        cache.remove(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }
}
