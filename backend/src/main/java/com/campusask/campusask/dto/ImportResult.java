package com.campusask.campusask.dto;

import lombok.Data;

import java.util.List;

/**
 * 导入结果DTO
 */
@Data
public class ImportResult {
    /**
     * 成功导入数量
     */
    private Integer successCount;
    
    /**
     * 失败数量
     */
    private Integer failedCount;
    
    /**
     * 错误行信息列表
     */
    private List<String> errorRows;
}