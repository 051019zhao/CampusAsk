package com.campusask.campusask.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 知识缺口实体 —— 赵丹丹 V3
 * 记录 AI 联网搜索 / 自主回答 / 降级兜底 产生的问答，
 * 供管理员审核后决定是否沉淀到知识库。
 */
@Data
public class KnowledgeGap {
    private Long id;
    private String question;
    private String answer;
    private String source;       // ai_search / ai_web / fallback
    private String urls;         // V3: 联网搜索来源链接（逗号分隔）
    private Integer searchCount; // 同一问题被问次数（自动合并累加）
    private String status;       // pending / approved / ignored
    private String category;
    private String keywords;
    private Long reviewedBy;
    private LocalDateTime reviewTime;
    private LocalDateTime createdAt;
}
