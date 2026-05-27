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

    public List<Knowledge> search(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        return knowledgeMapper.searchByKeyword(keyword.trim());
    }
}