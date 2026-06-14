package com.campusask.campusask.config;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class QuestionLogSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(QuestionLogSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public QuestionLogSchemaInitializer(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void ensureProcessedColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'question_log' AND COLUMN_NAME = 'processed'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute("ALTER TABLE question_log ADD COLUMN processed TINYINT(1) NOT NULL DEFAULT 0 AFTER source");
                log.info("已自动补充 question_log.processed 字段");
            }
        } catch (Exception ex) {
            log.warn("检查/补充 question_log.processed 失败：{}", ex.getMessage());
        }
    }
}