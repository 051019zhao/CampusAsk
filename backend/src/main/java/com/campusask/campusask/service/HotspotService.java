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
import com.campusask.campusask.mapper.QuestionLogMapper;

@Service
public class HotspotService {

    // Cache TTL（毫秒），默认 10 分钟，可通过配置覆盖
    @Value("${hotspot.cache.ttl-millis:600000}")
    private long ttlMillis;

    private final HotspotMapper hotspotMapper;
    private final HotKeywordMapper hotKeywordMapper;
    private final QuestionLogMapper questionLogMapper;
    private final Object cacheLock = new Object();
    private volatile Map<String, RangeCache> rangeCache = new ConcurrentHashMap<>();

    /**
     * 同义词归并表：value 中的每个关键词都归并到 key 这个标准词。
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
    public HotspotService(HotspotMapper hotspotMapper, HotKeywordMapper hotKeywordMapper,
                          QuestionLogMapper questionLogMapper) {
        this.hotspotMapper = hotspotMapper;
        this.hotKeywordMapper = hotKeywordMapper;
        this.questionLogMapper = questionLogMapper;
    }

    // V4: 热点问题最低频次阈值（低于此值不进入排行榜）
    private static final int MIN_HOT_COUNT = 15;

    /**
     * V4: 常见问题排行榜 —— 带同义归并 + 最低频次过滤
     * 提取问题中的核心实体词作为归并键，合并后过滤低频次，不足时多取数据
     */
    public List<Map<String, Object>> getTopQuestionsByRange(String startDate, String endDate, int limit) {
        // 多取原始数据，归并后可能合并掉大量条目
        List<Map<String, Object>> raw = questionLogMapper.findTopQuestionsByRange(startDate, endDate, 200);
        if (raw == null || raw.isEmpty()) return List.of();

        Map<String, Long> merged = new java.util.LinkedHashMap<>();
        Map<String, String> bestExample = new HashMap<>();

        for (Map<String, Object> row : raw) {
            Object nameObj = row.get("name");
            Object valObj = row.get("value");
            if (nameObj == null) continue;
            String question = nameObj.toString().trim();
            long count = valObj instanceof Number ? ((Number) valObj).longValue() : 0L;

            String canonical = normalizeQuestionForRanking(question);
            merged.merge(canonical, count, Long::sum);

            String existing = bestExample.get(canonical);
            if (existing == null || question.length() < existing.length()) {
                bestExample.put(canonical, question);
            }
        }

        // 计算最大值用于前端进度条
        long maxVal = merged.values().stream().mapToLong(Long::longValue).max().orElse(1L);

        return merged.entrySet().stream()
                .filter(e -> e.getKey().length() > 1)
                .filter(e -> e.getValue() >= MIN_HOT_COUNT)  // 低频次过滤
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    String entity = e.getKey();
                    String example = bestExample.get(entity);
                    m.put("name", entity.length() <= 10 ? entity :
                            (example != null && example.length() < entity.length() ? example : entity));
                    m.put("value", e.getValue());
                    m.put("maxValue", maxVal);
                    return m;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 问题归一化：提取核心实体词作为归并键。
     * "食堂几点开" / "食堂" / "十堂几点开门啊" → 都归并到 "食堂"
     * "华南理工大学在哪" / "华南理工大学" / "华工在哪" → 都归并到 "华南理工大学"
     */
    private String normalizeQuestionForRanking(String q) {
        if (q == null || q.isBlank()) return "";
        String s = q.trim();

        // 1) 拼写纠错 + 同义词替换
        s = s.replace("十堂", "食堂")
             .replace("饭堂", "食堂").replace("餐厅", "食堂").replace("吃饭", "食堂")
             .replace("寝室", "宿舍").replace("住宿", "宿舍")
             .replace("班车", "校车").replace("校园巴士", "校车")
             .replace("校园卡", "一卡通").replace("饭卡", "一卡通").replace("学生卡", "一卡通")
             .replace("课室", "教室").replace("教学楼", "教室")
             .replace("校医", "医务室").replace("校医院", "医务室")
             .replace("图书室", "图书馆")
             .replace("scut", "华南理工大学").replace("SCUT", "华南理工大学")
             .replace("华工", "华南理工大学")
             .replace("wifi", "校园网").replace("WiFi", "校园网").replace("无线网", "校园网")
             .replace("复印", "打印").replace("打印店", "打印")
             .replace("借书", "借阅").replace("还书", "借阅");

        // 2) 提取核心实体词：匹配到校园实体关键词就直接返回它
        String[] entities = {"华南理工大学","食堂","宿舍","图书馆","校车","一卡通","校园网","打印","教室","医务室","选课","奖学金","学费","快递","社团","放假","考试","毕业","门禁","校园"};
        for (String e : entities) {
            if (s.contains(e)) return e;
        }

        // 3) 没匹配到实体词：去噪声后返回
        s = s.toLowerCase()
             .replaceAll("请问|我想问|我想知道|告诉我|知不知道|可以告诉我|想问一下|问一下|他|它|她|这个|那个|这些|那些", "")
             .replaceAll("什么时候|啥时候|几时|几点|啥时|何时|几点开|几点关|几点关门|几点开门", "")
             .replaceAll("在哪里|什么地方|位置在哪|在哪|地点在哪|位于|怎么走", "")
             .replaceAll("如何|咋|怎样|怎么|怎么办|申请|补办|办理|流程|步骤", "")
             .replaceAll("多少钱|收费标准|费用|多少", "")
             .replaceAll("开放时间|开门时间|关门时间|营业时间", "")
             .replaceAll("啊|呢|呀|吗|吧|哦|嘛|的|是|了", "")
             .replaceAll("[\\s\\p{Punct}]+", "")
             .trim();
        return s.length() >= 2 ? s : q.replaceAll("[?\\s]+", "").trim();
    }

    /** 旧接口兼容保留 */
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

    private List<Map<String, Object>> buildRangeHotspots(String startDate, String endDate, int limit) {
        List<Map<String, Object>> rows = hotspotMapper.findTopKeywordsByRange(startDate, endDate, Math.max(limit * 3, 30));
        if (rows == null || rows.isEmpty()) return List.of();

        Map<String, Long> merged = new java.util.LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object kObj = row.get("keyword");
            Object fObj = row.get("freq");
            if (kObj == null) continue;
            String keyword = kObj.toString().trim();
            long freq = fObj instanceof Number ? ((Number) fObj).longValue() : 0L;

            String canonical = resolveCanonicalKeyword(keyword);
            merged.merge(canonical, freq, Long::sum);
        }

        return merged.entrySet().stream()
                .filter(e -> e.getKey().length() > 1)
                .filter(e -> !e.getKey().matches("^\\d+$"))
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(limit)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("keyword", e.getKey());
                    m.put("freq", e.getValue());
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
        String exact = SYNONYM_REVERSE.get(raw);
        if (exact != null) entity = exact;
        if (entity == null && SYNONYM_MAP.containsKey(raw)) entity = raw;

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
                if (bestScore == Integer.MAX_VALUE) break;
            }
        }

        return buildIntentCanonical(raw, bestCanonical);
    }

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
        s = s.replace((char) 0x3000, ' ').toLowerCase();
        s = s.replaceAll("[\\p{Punct}\\s]+", "");
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
