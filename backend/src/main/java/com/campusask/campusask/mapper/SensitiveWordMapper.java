package com.campusask.campusask.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.campusask.campusask.entity.SensitiveWord;

@Mapper
public interface SensitiveWordMapper {
    List<SensitiveWord> findAllSensitiveWords();
}
