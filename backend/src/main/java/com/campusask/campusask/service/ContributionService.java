package com.campusask.campusask.service;

import com.campusask.campusask.entity.Contribution;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.ContributionMapper;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContributionService {

    @Autowired
    private ContributionMapper contributionMapper;

    @Autowired
    private KnowledgeMapper knowledgeMapper;

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
    public void approveContribution(Long id, String category, String keywords, Long reviewerId) {
        Contribution contribution = contributionMapper.selectById(id);
        if (contribution == null) {
            throw new RuntimeException("投稿不存在或已被处理");
        }

        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(contribution.getQuestion());
        knowledge.setContent(contribution.getAnswer());
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
