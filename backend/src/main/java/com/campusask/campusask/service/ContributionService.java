package com.campusask.campusask.service;

import com.campusask.campusask.entity.Contribution;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.ContributionMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContributionService {

    @Autowired
    private ContributionMapper contributionMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Autowired
    private DeepSeekService deepSeekService;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 DeepSeek 检测文本是否包含敏感内容。
     * 返回 Map：{ "sensitive": true/false, "reason": "具体触发原因" }
     */
    public Map<String, Object> checkSensitive(String text) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String json = deepSeekService.checkSensitive(text);
            JsonNode node = objectMapper.readTree(json);
            boolean isSensitive = node.has("isSensitive") && node.get("isSensitive").asBoolean();
            String reason = node.has("reason") ? node.get("reason").asText() : "";
            result.put("sensitive", isSensitive);
            result.put("reason", reason);
        } catch (Exception e) {
            // 解析失败时默认放行，避免误拦截
            result.put("sensitive", false);
            result.put("reason", "");
        }
        return result;
    }

    public void submitContribution(Contribution contribution) {
        if (contribution.getStatus() == null || contribution.getStatus().isBlank()) {
            contribution.setStatus("pending");
        }
        contributionMapper.insertContribution(contribution);
    }

    public List<Contribution> listPendingContributions() {
        return contributionMapper.selectPending();
    }

    public List<Contribution> listRejectedContributions() {
        return contributionMapper.selectRejected();
    }

    public Contribution getContributionById(Long id) {
        return contributionMapper.selectById(id);
    }

    @Transactional
    public void approveContribution(Long id, String category, String keywords, Long reviewerId,
                                    String editedTitle, String editedContent) {
        Contribution contribution = contributionMapper.selectById(id);
        if (contribution == null) {
            throw new RuntimeException("投稿不存在或已被处理");
        }

        // V4: 支持管理员编辑后再入库
        String title = (editedTitle != null && !editedTitle.isBlank())
                ? editedTitle.trim() : contribution.getQuestion();
        String content = (editedContent != null && !editedContent.isBlank())
                ? editedContent.trim() : contribution.getAnswer();

        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        knowledge.setContent(content);
        knowledge.setCategory(category == null || category.isBlank() ? "未分类" : category.trim());
        knowledge.setKeywords(keywords == null ? "" : keywords.trim());
        knowledge.setViewCount(0);
        knowledge.setCreatedBy(reviewerId != null ? reviewerId : 1L);

        knowledgeMapper.insert(knowledge);
        contributionMapper.deleteById(id);
    }

    @Transactional
    public void rejectContribution(Long id, String reason, Long reviewerId) {
        Contribution contribution = contributionMapper.selectById(id);
        if (contribution == null) {
            throw new RuntimeException("投稿不存在或已被处理");
        }
        contributionMapper.updateStatus(id, "rejected", reason, reviewerId);
    }

    public List<Contribution> getUserContributions(Long userId) {
        return contributionMapper.selectByUserId(userId);
    }
}
