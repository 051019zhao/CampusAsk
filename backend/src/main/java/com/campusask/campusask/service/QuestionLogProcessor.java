package com.campusask.campusask.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.campusask.campusask.entity.HotKeyword;
import com.campusask.campusask.entity.QuestionLog;
import com.campusask.campusask.mapper.HotKeywordMapper;
import com.campusask.campusask.mapper.QuestionLogMapper;

@Component
public class QuestionLogProcessor {

    @Autowired
    private QuestionLogMapper questionLogMapper;

    @Autowired
    private HotKeywordMapper hotKeywordMapper;

    @Autowired
    private DeepSeekService deepSeekService;

    // 常用停用词（可扩展）
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "的","了","在","是","和","我","你","他","她","它","吗","呢","怎么","请","能"
    ));

    @Scheduled(fixedDelayString = "${keyword.processor.delay:60000}")
    public void process() {
        List<QuestionLog> logs = questionLogMapper.findUnprocessed(200);
        if (logs == null || logs.isEmpty()) return;

        List<HotKeyword> existing = hotKeywordMapper.findTopKeywords(1000);
        List<String> existingNames = existing == null ? List.of() : existing.stream()
                .map(HotKeyword::getKeyword)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        for (QuestionLog log : logs) {
            String q = log.getQuestion();
            if (!StringUtils.hasText(q)) {
                questionLogMapper.markProcessed(log.getId());
                continue;
            }

            List<String> candidates = deepSeekService.normalizeHotKeywords(q, existingNames);
            if (candidates == null || candidates.isEmpty()) {
                candidates = extractCandidates(q);
            }
            for (String cand : candidates) {
                String matched = findSynonym(cand, existing);
                if (matched != null) {
                    hotKeywordMapper.upsertKeyword(matched);
                    if (!existingNames.contains(matched)) {
                        existingNames.add(matched);
                    }
                } else {
                    hotKeywordMapper.upsertKeyword(cand);
                    if (!existingNames.contains(cand)) {
                        existingNames.add(cand);
                    }
                }
            }

            questionLogMapper.markProcessed(log.getId());
        }
    }

    private List<String> extractCandidates(String text) {
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}\\s]+", " ").trim();
        String[] parts = norm.split(" ");
        List<String> res = new ArrayList<>();
        for (String p : parts) {
            if (p.length() < 2) continue;
            if (STOPWORDS.contains(p)) continue;
            res.add(p);
        }
        return res;
    }

    private String findSynonym(String cand, List<HotKeyword> existing) {
        if (existing == null) return null;
        String nc = normalize(cand);
        for (HotKeyword hk : existing) {
            String k = hk.getKeyword();
            if (k == null) continue;
            String nk = normalize(k);
            if (nk.equals(nc)) return k;
            if (nk.contains(nc) || nc.contains(nk)) return k;
            int d = levenshtein(nk, nc);
            if (d <= 2 && Math.max(nk.length(), nc.length()) >= 4) return k;
        }
        return null;
    }

    private String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^\u4e00-\u9fa5a-z0-9]", "");
    }

    private int levenshtein(String a, String b) {
        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) costs[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }
        return costs[b.length()];
    }
}
