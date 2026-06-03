package com.campusask.campusask.service;

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
    public void importFromExcel(MultipartFile file) {
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
 
                    Knowledge knowledge = new Knowledge();
                    knowledge.setTitle(title);
                    knowledge.setContent(content);
                    knowledge.setCategory(isBlank(category) ? "未分类" : category);
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
 
            // 如果有错误行，抛出包含错误信息的异常
            if (!errorRows.isEmpty()) {
                throw new RuntimeException("导入完成，但存在错误行:\n" + String.join("\n", errorRows));
            }
 
        } catch (RuntimeException e) {
            throw e;
            
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
