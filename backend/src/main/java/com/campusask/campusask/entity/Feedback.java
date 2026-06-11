package com.campusask.campusask.entity;

import java.time.LocalDateTime;

public class Feedback {
    private Long id;
    private String question;
    private String answer;
    private String source;
    private Boolean helpful;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Boolean getHelpful() { return helpful; }
    public void setHelpful(Boolean helpful) { this.helpful = helpful; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
