package com.campusask.campusask.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class KnowledgeExcelDTO {
    @ExcelProperty("标题")
    private String title;
    @ExcelProperty("内容")
    private String content;
    @ExcelProperty("分类")
    private String category;
    @ExcelProperty("关键词")
    private String keywords;
}