package com.campusask.campusask.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campusask.campusask.mapper.QuestionLogMapper;

@Service
public class HotspotService {

    // Cache TTL: 3 days (in milliseconds). 原来是 60s，调整为 3 天重置一次。
    private static final long TTL_MILLIS = 3L * 24 * 60 * 60 * 1000; // 259200000L
    private static final int SAMPLE_LIMIT = 500;

    private final QuestionLogMapper questionLogMapper;
    private final Object cacheLock = new Object();
    private volatile CacheEntry cache = new CacheEntry(List.of(), 0L);

    @Autowired
    public HotspotService(QuestionLogMapper questionLogMapper) {
        this.questionLogMapper = questionLogMapper;
    }

    public List<String> getTopKeywords(int limit) {
        long now = System.currentTimeMillis();
        CacheEntry snapshot = cache;
        if (now - snapshot.timestamp < TTL_MILLIS) {
            return snapshot.keywords;
        }
        synchronized (cacheLock) {
            snapshot = cache;
            if (now - snapshot.timestamp < TTL_MILLIS) {
                return snapshot.keywords;
            }
            List<String> keywords = buildHotspots(limit);
            cache = new CacheEntry(keywords, now);
            return cache.keywords;
        }
    }

    public void invalidateCache() {
        cache = new CacheEntry(List.of(), 0L);
    }

    private List<String> buildHotspots(int limit) {
        List<String> recent = questionLogMapper.findRecentQuestions(SAMPLE_LIMIT);
        if (recent == null || recent.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (String question : recent) {
            String topic = normalizeTopic(question);
            if (topic.isEmpty()) {
                continue;
            }
            counts.put(topic, counts.getOrDefault(topic, 0) + 1);
        }
        if (counts.isEmpty()) {
            return List.of();
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> {
            int cmp = b.getValue().compareTo(a.getValue());
            if (cmp != 0) return cmp;
            return a.getKey().compareTo(b.getKey());
        });
        List<String> result = new ArrayList<>(limit);
        for (Map.Entry<String, Integer> entry : entries) {
            result.add(entry.getKey());
            if (result.size() >= limit) break;
        }
        return Collections.unmodifiableList(result);
    }

    private String normalizeTopic(String question) {
        if (question == null) return "";
        String q = question.trim();
        if (q.isEmpty()) return "";
        q = q.replace("？", "").replace("?", "").replace("。", "")
             .replace("，", "").replace(",", "").replace("！", "")
             .replace("!", "").replace(" ", "");

        if (contains(q, "图书馆") && containsAny(q, "几点", "开门", "关门", "开放", "时间")) {
            return "图书馆开放时间";
        }
        if (containsAny(q, "食堂", "饭堂") && containsAny(q, "好吃", "推荐", "哪家", "菜", "必吃")) {
            return "食堂推荐";
        }
        if (containsAny(q, "宿舍", "寝室") && containsAny(q, "门禁", "几点", "关门", "开门")) {
            return "宿舍门禁时间";
        }
        if (containsAny(q, "快递", "驿站")) {
            return "快递点";
        }
        if (containsAny(q, "一卡通", "饭卡", "校园卡")) {
            return "校园卡";
        }
        if (containsAny(q, "校车", "班车")) {
            return "校车";
        }
        if (containsAny(q, "打印", "复印")) {
            return "打印服务";
        }
        if (contains(q, "图书馆")) {
            return "图书馆";
        }
        if (containsAny(q, "食堂", "饭堂")) {
            return "食堂";
        }
        if (containsAny(q, "宿舍", "寝室")) {
            return "宿舍";
        }
        return q.length() > 20 ? q.substring(0, 20) : q;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean contains(String text, String keyword) {
        return text.contains(keyword);
    }

    private static final class CacheEntry {
        private final List<String> keywords;
        private final long timestamp;

        private CacheEntry(List<String> keywords, long timestamp) {
            this.keywords = keywords;
            this.timestamp = timestamp;
        }
    }
}
