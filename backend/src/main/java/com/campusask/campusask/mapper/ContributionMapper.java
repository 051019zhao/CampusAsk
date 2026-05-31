package com.campusask.campusask.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.campusask.campusask.entity.Contribution;

@Mapper
public interface ContributionMapper {
    void insertContribution(Contribution contribution);
}
