package com.campusask.campusask.service;
import com.campusask.campusask.dto.ImportResult;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Override
    @Transactional
    public ImportResult importFromExcel(MultipartFile file) {
        List<Knowledge> validList = new ArrayList<>();
        List<String> errorRows = new ArrayList<>();
 
        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            int totalRows = sheet.getLastRowNum();
            // 跳过表头，从第二行开始读
             for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    // 获取单元格值，处理空值情况
                    String title = getCellStringValue(row.getCell(0));
                    String content = getCellStringValue(row.getCell(1));
                    String category = getCellStringValue(row.getCell(2));
                    String keywords = getCellStringValue(row.getCell(3));
 
                    // 校验必填字段
                    if (isBlank(title)) {
                        errorRows.add("第" + (i + 1) + "行：标题为空");
                        continue;
                    }
                    if (isBlank(content)) {
                        errorRows.add("第" + (i + 1) + "行：内容为空");
                        continue;
                    }
                     if (isBlank(category)) {
                        errorRows.add("第" + (i + 1) + "行：分类为空");
                        continue;
                    }
  // 检测乱码
                    if (containsGarbled(title)) {
                        errorRows.add("第" + (i + 1) + "行：标题包含乱码");
                        continue;
                    }
                    if (containsGarbled(content)) {
                        errorRows.add("第" + (i + 1) + "行：内容包含乱码");
                        continue;
                    }
                    if (containsGarbled(category)) {
                        errorRows.add("第" + (i + 1) + "行：分类包含乱码");
                        continue;
                    }
 
                    Knowledge knowledge = new Knowledge();
                    knowledge.setTitle(title);
                    
                    knowledge.setContent(content);
                     knowledge.setCategory(category);
                    knowledge.setKeywords(isBlank(keywords) ? "" : keywords);
                    knowledge.setViewCount(0);
                    knowledge.setCreatedBy(1L); // 这里可以改成实际的用户名
                    validList.add(knowledge);
 
                } catch (Exception e) {
                    errorRows.add("第" + (i + 1) + "行：解析错误 - " + e.getMessage());
                }
            }
 
            // 批量插入有效数据
            if (!validList.isEmpty()) {
                knowledgeMapper.batchInsert(validList);
            }
 
             // 返回导入结果
            ImportResult result = new ImportResult();
            result.setSuccessCount(validList.size());
            result.setFailedCount(errorRows.size());
            result.setErrorRows(errorRows);
            return result;
            
        } 
        catch (Exception e) {
            throw new RuntimeException("Excel导入失败：" + e.getMessage());
        }
    }
/**
     * 获取单元格的字符串值
     */
    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return "";
        }
    }
 
    /**
     * 判断字符串是否为空
     */
    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
  /**
     * 检测字符串是否包含乱码
     * 检测规则：
     * 1. 包含 Unicode 替换字符 \uFFFD（显示为 �）
     * 2. 包含大量不可打印的控制字符
     * 3. 包含连续的问号或特殊符号（常见乱码表现）
     */
    private boolean containsGarbled(String str) {
        if (str == null) {
            return false;
        }
        // 检测 Unicode 替换字符（乱码的典型特征）
        if (str.contains("\uFFFD")) {
            return true;
        }
        // 检测连续的问号（常见于编码错误）
        if (str.matches(".*\\?{3,}.*")) {
            return true;
        }
        // 检测不可打印字符比例超过50%
        int controlCharCount = 0;
        for (char c : str.toCharArray()) {
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                controlCharCount++;
            }
        }
        if (str.length() > 0 && controlCharCount > str.length() / 2) {
            return true;
        }
        // 检测是否包含非法 UTF-8 字节序列的迹象
        if (str.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F].*")) {
            return true;
        }
        return false;
    }
    @Override
    public List<Knowledge> listAll() {
        return knowledgeMapper.listAll();
    }

 @Override
    public Knowledge getById(Long id) {
        return knowledgeMapper.selectById(id);
    }
 
    @Override
    @Transactional
    public void update(Knowledge knowledge) {
        knowledgeMapper.update(knowledge);
    }
 
    @Override
    @Transactional
    public void deleteById(Long id) {
        knowledgeMapper.deleteById(id);
    }
}
