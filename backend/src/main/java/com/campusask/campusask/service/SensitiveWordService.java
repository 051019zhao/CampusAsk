package com.campusask.campusask.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campusask.campusask.entity.SensitiveWord;
import com.campusask.campusask.mapper.SensitiveWordMapper;

@Service
public class SensitiveWordService {

    @Autowired
    private SensitiveWordMapper sensitiveWordMapper;

    public List<String> getAllWords() {
        List<SensitiveWord> records = sensitiveWordMapper.findAllSensitiveWords();
        List<String> words = new ArrayList<>();
        for (SensitiveWord record : records) {
            if (record != null && record.getWord() != null && !record.getWord().trim().isEmpty()) {
                words.add(record.getWord().trim());
            }
        }
        return words;
    }

    public List<String> findMatchingWords(String text) {
        List<String> matched = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return matched;
        }

        String normalizedText = text.toLowerCase();
        for (String word : getAllWords()) {
            if (word == null || word.isBlank()) {
                continue;
            }
            String normalizedWord = word.toLowerCase();
            if (normalizedText.contains(normalizedWord) && !matched.contains(word)) {
                matched.add(word);
            }
        }
        return matched;
    }
}
