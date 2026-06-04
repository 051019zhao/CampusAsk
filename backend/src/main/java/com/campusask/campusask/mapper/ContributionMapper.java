package com.campusask.campusask.mapper;

import com.campusask.campusask.entity.Contribution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ContributionMapper {
    void insertContribution(Contribution contribution);

    Contribution selectById(@Param("id") Long id);

    List<Contribution> selectPending();

    int deleteById(@Param("id") Long id);

    int updateStatus(@Param("id") Long id, @Param("status") String status, @Param("reason") String reason, @Param("reviewedBy") Long reviewedBy);

    List<Contribution> selectByUserId(@Param("userId") Long userId);
}
