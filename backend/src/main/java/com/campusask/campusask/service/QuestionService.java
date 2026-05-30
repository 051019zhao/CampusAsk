package com.campusask.campusask.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.entity.QuestionLog;
import com.campusask.campusask.mapper.HotKeywordMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.campusask.campusask.mapper.QuestionLogMapper;

@Service
public class QuestionService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private HotKeywordMapper hotKeywordMapper;

    @Autowired
    private QuestionLogMapper questionLogMapper;

    @Autowired
    private DeepSeekService deepSeekService;

    public Object search(String keyword) {
        return search(keyword, null);
    }

    public Object search(String keyword, Long userId) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        String trimmed = keyword.trim();
        List<Knowledge> results = knowledgeMapper.searchByKeyword(trimmed);
        Object answer;
        if (!results.isEmpty()) {
            answer = results;
        } else {
            answer = deepSeekService.ask(trimmed);
        }
        recordSearch(userId, trimmed, answer);
        return answer;
    }

    public List<String> getHotKeywords(int limit) {
        return hotKeywordMapper.findTopKeywords(limit)
                .stream()
                .map(item -> item.getKeyword())
                .collect(Collectors.toList());
    }

    public List<String> getRecentQuestions(Long userId, int limit) {
        if (userId == null) {
            return List.of();
        }
        return questionLogMapper.findRecentByUserId(userId, limit)
                .stream()
                .map(QuestionLog::getQuestion)
                .collect(Collectors.toList());
    }

    private void recordSearch(Long userId, String question, Object answer) {
        hotKeywordMapper.upsertKeyword(question);
        if (userId == null) {
            return;
        }
        QuestionLog log = new QuestionLog();
        log.setUserId(userId);
        log.setQuestion(question);
        if (answer instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Knowledge knowledge) {
            log.setAnswer(knowledge.getContent());
            log.setSource("knowledge");
        } else if (answer instanceof String text) {
            log.setAnswer(text);
            log.setSource("ai");
        } else {
            log.setSource("unknown");
        }
        questionLogMapper.insertLog(log);
    }
}