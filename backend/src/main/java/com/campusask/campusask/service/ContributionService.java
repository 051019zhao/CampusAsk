package com.campusask.campusask.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.campusask.campusask.entity.Contribution;
import com.campusask.campusask.mapper.ContributionMapper;

@Service
public class ContributionService {

    @Autowired
    private ContributionMapper contributionMapper;

    public void submitContribution(Contribution contribution) {
        if (contribution.getStatus() == null || contribution.getStatus().isBlank()) {
            contribution.setStatus("pending");
        }
        contributionMapper.insertContribution(contribution);
    }
}
