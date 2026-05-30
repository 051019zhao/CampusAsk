package com.campusask.campusask.service;

import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class QuestionService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private DeepSeekService deepSeekService;

    public Object search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        List<Knowledge> results = knowledgeMapper.searchByKeyword(keyword.trim());
        if (!results.isEmpty()) {
            return results;
        }
        return deepSeekService.ask(keyword);
    }
}