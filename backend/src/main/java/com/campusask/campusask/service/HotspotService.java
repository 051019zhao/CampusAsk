package com.campusask.campusask.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campusask.campusask.mapper.HotKeywordMapper;

@Service
public class HotspotService {

    // Cache TTL: 1 days (in milliseconds). 原来是 60s，调整为 1 天重置一次。
    private static final long TTL_MILLIS = 1L * 24 * 60 * 60 * 1000; // 86400000L
    private final HotKeywordMapper hotKeywordMapper;
    private final Object cacheLock = new Object();
    // cache map keyed by limit value
    private volatile Map<Integer, CacheEntry> cacheMap = new ConcurrentHashMap<>();

    @Autowired
    public HotspotService(HotKeywordMapper hotKeywordMapper) {
        this.hotKeywordMapper = hotKeywordMapper;
    }

    public List<String> getTopKeywords(int limit) {
        long now = System.currentTimeMillis();
        CacheEntry snapshot = cacheMap.get(limit);
        if (snapshot != null && now - snapshot.timestamp < TTL_MILLIS) {
            return snapshot.keywords;
        }
        synchronized (cacheLock) {
            snapshot = cacheMap.get(limit);
            if (snapshot != null && now - snapshot.timestamp < TTL_MILLIS) {
                return snapshot.keywords;
            }
            List<String> keywords = buildHotspots(limit);
            CacheEntry entry = new CacheEntry(keywords, now);
            Map<Integer, CacheEntry> newMap = new HashMap<>(cacheMap);
            newMap.put(limit, entry);
            cacheMap = newMap;
            return entry.keywords;
        }
    }

    public void invalidateCache() {
        cacheMap = new ConcurrentHashMap<>();
    }

    private List<String> buildHotspots(int limit) {
        // 热点榜直接读 hot_keyword，由异步处理器维护
        List<String> list = hotKeywordMapper.findTopKeywordNames(limit);
        if (list == null || list.isEmpty()) return List.of();
        return Collections.unmodifiableList(list);
    }

    // normalization and heuristic grouping removed; aggregation now done in SQL

    private static final class CacheEntry {
        private final List<String> keywords;
        private final long timestamp;

        private CacheEntry(List<String> keywords, long timestamp) {
            this.keywords = keywords;
            this.timestamp = timestamp;
        }
    }
}
