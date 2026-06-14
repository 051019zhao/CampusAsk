package com.campusask.campusask.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.campusask.campusask.mapper.HotKeywordMapper;
import com.campusask.campusask.mapper.HotspotMapper;

@Service
public class HotspotService {

    // Cache TTL（毫秒），默认 10 分钟，可通过配置覆盖
    @Value("${hotspot.cache.ttl-millis:600000}")
    private long ttlMillis;

    private final HotspotMapper hotspotMapper;
    private final HotKeywordMapper hotKeywordMapper;
    private final Object cacheLock = new Object();
    private volatile Map<String, RangeCache> rangeCache = new ConcurrentHashMap<>();

    /**
     * 同义词归并表：value 中的每个关键词都归并到 key 这个标准词。
     * 例如"图书馆几点关门"、"图书馆开放时间"都归并到"图书馆"统一计数。
     */
    private static final Map<String, List<String>> SYNONYM_MAP = new HashMap<>();

    static {
        SYNONYM_MAP.put("图书馆", List.of("图书馆开放时间", "图书馆几点关门", "图书馆几点开门",
                "图书馆自习", "图书馆预约", "图书馆借书"));
        SYNONYM_MAP.put("食堂", List.of("食堂几点开", "食堂几点关", "食堂营业时间",
            "餐厅营业时间", "食堂菜单", "今天吃什么", "饭堂", "餐厅", "吃饭"));
        SYNONYM_MAP.put("宿舍", List.of("宿舍几点关门", "宿舍门禁时间", "宿舍申请",
                "宿舍报修", "宿舍楼管"));
        SYNONYM_MAP.put("选课", List.of("选课系统", "选课时间", "怎么选课", "选课入口",
                "课程表", "课程查询"));
        SYNONYM_MAP.put("学费", List.of("学费缴纳", "学费多少", "学费查询", "缴费时间"));
        SYNONYM_MAP.put("奖学金", List.of("奖学金申请", "奖学金条件", "奖学金评定",
                "助学金", "助学贷款"));
        SYNONYM_MAP.put("校车", List.of("校车时刻表", "校车路线", "班车", "校巴"));
        SYNONYM_MAP.put("WiFi", List.of("校园网", "网络连接", "怎么连wifi", "网速慢", "wifi", "WIFI"));
        SYNONYM_MAP.put("一卡通", List.of("饭卡", "校园卡", "学生卡", "一卡通补办", "饭卡补办"));
        SYNONYM_MAP.put("华南理工大学", List.of("华工", "scut", "SCUT"));
    }

    // 反向查找：变体词 → 标准词
    private static final Map<String, String> SYNONYM_REVERSE;
    static {
        Map<String, String> rev = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
            for (String variant : entry.getValue()) {
                rev.put(variant, entry.getKey());
            }
        }
        SYNONYM_REVERSE = Collections.unmodifiableMap(rev);
    }

        private static final Set<String> NOISE_TOKENS = Set.of(
            "请问", "我想问", "我想知道", "问一下", "一下", "吗", "呢", "呀", "啊", "吧"
        );

        private static final Set<String> TIME_INTENT_TOKENS = Set.of(
            "几点", "几时", "时间", "什么时候", "何时", "营业", "开放", "开门", "关门", "时刻"
        );

        private static final Set<String> LOCATION_INTENT_TOKENS = Set.of(
            "在哪", "哪里", "在哪儿", "地点", "位置", "位于", "怎么走"
        );

        private static final Set<String> PROCESS_INTENT_TOKENS = Set.of(
            "怎么", "如何", "怎么办", "怎样", "申请", "补办", "办理", "流程"
        );

        private static final Set<String> FEE_INTENT_TOKENS = Set.of(
            "多少钱", "多少", "收费", "费用", "价格"
        );

    @Autowired
    public HotspotService(HotspotMapper hotspotMapper, HotKeywordMapper hotKeywordMapper) {
        this.hotspotMapper = hotspotMapper;
        this.hotKeywordMapper = hotKeywordMapper;
    }

    /** 旧接口兼容保留，内部走按范围查询 */
    public List<String> getTopKeywords(int limit) {
        java.time.LocalDate now = java.time.LocalDate.now();
        String start = now.minusDays(6).toString();
        String end = now.toString();
        List<Map<String, Object>> rows = getTopKeywordsByRange(start, end, limit);
        List<String> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object k = row.get("keyword");
            if (k != null) result.add(k.toString());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 按时间范围查询热词（带同义词归并 + 去噪 + 频次），缓存 10 分钟。
     * 供 Controller 调用，返回带 keyword/freq 的 Map 列表。
     */
    public List<Map<String, Object>> getTopKeywordsByRange(String startDate, String endDate, int limit) {
        String cacheKey = startDate + "_" + endDate + "_" + limit;
        long now = System.currentTimeMillis();
        RangeCache cached = rangeCache.get(cacheKey);
        if (cached != null && now - cached.timestamp < ttlMillis) {
            return cached.data;
        }
        synchronized (cacheLock) {
            cached = rangeCache.get(cacheKey);
            if (cached != null && now - cached.timestamp < ttlMillis) {
                return cached.data;
            }
            List<Map<String, Object>> built = buildRangeHotspots(startDate, endDate, limit);
            Map<String, RangeCache> newMap = new HashMap<>(rangeCache);
            newMap.put(cacheKey, new RangeCache(built, now));
            rangeCache = newMap;
            return built;
        }
    }

    public void invalidateCache() {
        rangeCache = new ConcurrentHashMap<>();
    }

    /** 核心构建：DB 聚合 → 同义词归并 → 去噪 → 排序截取 */
    private List<Map<String, Object>> buildRangeHotspots(String startDate, String endDate, int limit) {
        // 多取一些原始数据，归并后可能合并掉部分条目
        List<Map<String, Object>> rows = hotspotMapper.findTopKeywordsByRange(startDate, endDate, Math.max(limit * 3, 30));
        if (rows == null || rows.isEmpty()) return List.of();

        // 同义词归并：把变体词的频次合并到标准词
        Map<String, Long> merged = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object kObj = row.get("keyword");
            Object fObj = row.get("freq");
            if (kObj == null) continue;
            String keyword = kObj.toString().trim();
            long freq = fObj instanceof Number ? ((Number) fObj).longValue() : 0L;

            // 归并到标准词（支持同义词 + 轻量模糊语义匹配）
            String canonical = resolveCanonicalKeyword(keyword);
            merged.merge(canonical, freq, Long::sum);
        }

        // 去噪：过滤单字词和纯数字（SQL 已做一层，这里做最终保障）
        // 并且按频次降序排列，取 top limit
        return merged.entrySet().stream()
                .filter(e -> e.getKey().length() > 1)
                .filter(e -> !e.getKey().matches("^\\d+$"))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("keyword", e.getKey());
                    m.put("freq", e.getValue());

                    // 异步写入 hot_keyword 表
                    try { hotKeywordMapper.upsertKeyword(e.getKey()); } catch (Exception ignored) {}
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    private String resolveCanonicalKeyword(String keyword) {
        if (keyword == null) return "";
        String raw = keyword.trim();
        if (raw.isEmpty()) return raw;

        String entity = null;

        // 1) 精确同义词映射
        String exact = SYNONYM_REVERSE.get(raw);
        if (exact != null) entity = exact;
        if (entity == null && SYNONYM_MAP.containsKey(raw)) entity = raw;

        // 2) 归一化后做模糊匹配
        String normalized = normalizeForMatch(raw);
        if (entity == null && normalized.isEmpty()) return raw;

        String bestCanonical = entity == null ? raw : entity;
        int bestScore = 0;

        if (entity == null) {
            for (Map.Entry<String, List<String>> entry : SYNONYM_MAP.entrySet()) {
                String canonical = entry.getKey();
                Set<String> candidates = new LinkedHashSet<>();
                candidates.add(canonical);
                candidates.addAll(entry.getValue());

                for (String candidate : candidates) {
                    String cNorm = normalizeForMatch(candidate);
                    if (cNorm.isEmpty()) continue;
                    if (normalized.equals(cNorm)) {
                        bestCanonical = canonical;
                        bestScore = Integer.MAX_VALUE;
                        break;
                    }
                    if (normalized.contains(cNorm) || cNorm.contains(normalized)) {
                        int score = cNorm.length();
                        if (score > bestScore) {
                            bestScore = score;
                            bestCanonical = canonical;
                        }
                    }
                }
                if (bestScore == Integer.MAX_VALUE) {
                    break;
                }
            }
        }

        return buildIntentCanonical(raw, bestCanonical);
    }

    /** 把实体词进一步归并到“语义意图词”，例如：食堂 + 时间意图 -> 食堂几点开 */
    private String buildIntentCanonical(String raw, String entity) {
        if (entity == null || entity.isBlank()) return raw;

        String intent = detectIntent(raw);
        if (intent == null) return entity;

        return switch (intent) {
            case "time" -> entity + "几点开";
            case "location" -> entity + "在哪";
            case "process" -> entity + "怎么办";
            case "fee" -> entity + "多少钱";
            default -> entity;
        };
    }

    private String detectIntent(String raw) {
        String q = raw == null ? "" : raw.toLowerCase();
        if (q.isEmpty()) return null;

        if (containsAny(q, TIME_INTENT_TOKENS)) return "time";
        if (containsAny(q, LOCATION_INTENT_TOKENS)) return "location";
        if (containsAny(q, PROCESS_INTENT_TOKENS)) return "process";
        if (containsAny(q, FEE_INTENT_TOKENS)) return "fee";
        return null;
    }

    private boolean containsAny(String text, Set<String> tokens) {
        for (String token : tokens) {
            if (text.contains(token)) return true;
        }
        return false;
    }

    private String normalizeForMatch(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return "";
        // 全角空格/标点简化，英文统一小写
        s = s.replace('　', ' ').toLowerCase();
        s = s.replaceAll("[\\p{Punct}，。！？、；：‘’“”（）《》【】\\s]+", "");
        for (String token : NOISE_TOKENS) {
            s = s.replace(token, "");
        }
        return s;
    }

    private static final class RangeCache {
        final List<Map<String, Object>> data;
        final long timestamp;
        RangeCache(List<Map<String, Object>> data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
    }
}
