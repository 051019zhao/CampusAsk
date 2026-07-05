package com.campusask.campusask.service;
import com.campusask.campusask.service.SensitiveWordService;
import com.campusask.campusask.dto.ImportResult;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;
 // 使用现有的 SensitiveWordService
    @Autowired
    private SensitiveWordService sensitiveWordService;
   @Override
@Transactional
public ImportResult importFromExcel(MultipartFile file) {
    List<Knowledge> validList = new ArrayList<>();
    List<String> errorRows = new ArrayList<>();

    try {
        String fileName = file.getOriginalFilename();
        // 判断文件类型
        if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
            // CSV 文件解析
            parseCsvFile(file, validList, errorRows);
        } else {
            // Excel 文件解析
            try (InputStream is = file.getInputStream();
                 Workbook workbook = new XSSFWorkbook(is)) {
                Sheet sheet = workbook.getSheetAt(0);
                int totalRows = sheet.getLastRowNum();
                for (int i = 1; i <= totalRows; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                    processRow(row, i, validList, errorRows);
                }
            }
        }

        if (!validList.isEmpty()) {
            knowledgeMapper.batchInsert(validList);
        }

        ImportResult result = new ImportResult();
        result.setSuccessCount(validList.size());
        result.setFailedCount(errorRows.size());
        result.setErrorRows(errorRows);
        return result;

    } catch (Exception e) {
        throw new RuntimeException("文件导入失败：" + e.getMessage());
    }
}

/**
 * 解析 CSV 文件
 */
private void parseCsvFile(MultipartFile file, List<Knowledge> validList, List<String> errorRows) throws Exception {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
        String line;
        int rowIndex = 0;
        while ((line = reader.readLine()) != null) {
            rowIndex++;
            // 跳过表头
            if (rowIndex == 1) continue;
            
            try {
                // 使用逗号分隔，处理带引号的字段
                String[] parts = parseCsvLine(line);
                String title = parts.length > 0 ? parts[0].trim() : "";
                String content = parts.length > 1 ? parts[1].trim() : "";
                String category = parts.length > 2 ? parts[2].trim() : "";
                String keywords = parts.length > 3 ? parts[3].trim() : "";

                // 校验必填字段
                if (isBlank(title)) {
                    errorRows.add("第" + rowIndex + "行：问题为空");
                    continue;
                }
                if (isBlank(content)) {
                    errorRows.add("第" + rowIndex + "行：答案为空");
                    continue;
                }
                if (isBlank(category)) {
                    errorRows.add("第" + rowIndex + "行：分类为空");
                    continue;
                }
                // 检测关键词为空
if (isBlank(keywords)) {
    errorRows.add("第" + rowIndex + "行：关键词为空");
    continue;
}
 // ========== 新增：检测乱码 ==========
                if (containsGarbled(title)) {
                    errorRows.add("第" + rowIndex + "行：问题包含乱码");
                    continue;
                }
                if (containsGarbled(content)) {
                    errorRows.add("第" + rowIndex + "行：答案包含乱码");
                    continue;
                }
                if (containsGarbled(category)) {
                    errorRows.add("第" + rowIndex + "行：分类包含乱码");
                    continue;
                }

                // 敏感词检测
                List<String> questionViolations = sensitiveWordService.findMatchingWords(title);
                List<String> answerViolations = sensitiveWordService.findMatchingWords(content);
                if (!questionViolations.isEmpty() || !answerViolations.isEmpty()) {
                    StringBuilder violationMsg = new StringBuilder("第" + rowIndex + "行：");
                    if (!questionViolations.isEmpty()) {
                        violationMsg.append("问题包含违规内容：" + String.join("、", questionViolations));
                    }
                    if (!answerViolations.isEmpty()) {
                        if (!questionViolations.isEmpty()) {
                            violationMsg.append("；");
                        }
                        violationMsg.append("答案包含违规内容：" + String.join("、", answerViolations));
                    }
                    errorRows.add(violationMsg.toString());
                    continue;
                }

                // V4: 重复检测 - 检查数据库中是否已存在相同标题
                if (knowledgeMapper.selectByTitle(title) != null) {
                    errorRows.add("第" + rowIndex + "行：标题「" + title + "」已存在，跳过");
                    continue;
                }
                // V4: 重复检测 - 检查本批次内是否已导入过
                boolean dupInBatch = validList.stream().anyMatch(k -> k.getTitle().equals(title));
                if (dupInBatch) {
                    errorRows.add("第" + rowIndex + "行：标题「" + title + "」在文件中重复，跳过");
                    continue;
                }

                Knowledge knowledge = new Knowledge();
                knowledge.setTitle(title);
                knowledge.setContent(content);
                knowledge.setCategory(category);
                knowledge.setKeywords(isBlank(keywords) ? "" : keywords);
                knowledge.setViewCount(0);
                knowledge.setCreatedBy(1L);
                validList.add(knowledge);

            } catch (Exception e) {
                errorRows.add("第" + rowIndex + "行：解析错误 - " + e.getMessage());
            }
        }
    }
}
private String[] parseCsvLine(String line) {
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inQuotes = false;

    for (int i = 0; i < line.length(); i++) {
        char c = line.charAt(i);
        
        if (c == '"') {
            // 处理转义的引号 ""
            if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                current.append('"');
                i++;
            } else {
                inQuotes = !inQuotes;
            }
        } else if (c == ',' && !inQuotes) {
            parts.add(current.toString());
            current = new StringBuilder();
        } else {
            current.append(c);
        }
    }
    parts.add(current.toString());
    
    return parts.toArray(new String[0]);
}

/**
 * 处理 Excel 行数据
 */
private void processRow(Row row, int rowIndex, List<Knowledge> validList, List<String> errorRows) {
    try {
        String title = getCellStringValue(row.getCell(0));
        String content = getCellStringValue(row.getCell(1));
        String category = getCellStringValue(row.getCell(2));
        String keywords = getCellStringValue(row.getCell(3));

        // 校验必填字段
        if (isBlank(title)) {
            errorRows.add("第" + (rowIndex + 1) + "行：问题为空");
            return;
        }
        if (isBlank(content)) {
            errorRows.add("第" + (rowIndex + 1) + "行：答案为空");
            return;
        }
        if (isBlank(category)) {
            errorRows.add("第" + (rowIndex + 1) + "行：分类为空");
            return;
        }
// 检测关键词为空
    if (isBlank(keywords)) {
        errorRows.add("第" + (rowIndex + 1) + "行：关键词为空");
        return;
    }
        // 敏感词检测
        List<String> questionViolations = sensitiveWordService.findMatchingWords(title);
        List<String> answerViolations = sensitiveWordService.findMatchingWords(content);
        if (!questionViolations.isEmpty() || !answerViolations.isEmpty()) {
            StringBuilder violationMsg = new StringBuilder("第" + (rowIndex + 1) + "行：");
            if (!questionViolations.isEmpty()) {
                violationMsg.append("问题包含违规内容：" + String.join("、", questionViolations));
            }
            if (!answerViolations.isEmpty()) {
                if (!questionViolations.isEmpty()) {
                    violationMsg.append("；");
                }
                violationMsg.append("答案包含违规内容：" + String.join("、", answerViolations));
            }
            errorRows.add(violationMsg.toString());
            return;
        }

        Knowledge knowledge = new Knowledge();
        knowledge.setTitle(title);
        knowledge.setContent(content);
        knowledge.setCategory(category);
        knowledge.setKeywords(isBlank(keywords) ? "" : keywords);
        knowledge.setViewCount(0);
        knowledge.setCreatedBy(1L);
        // V4: 重复检测（title 已在方法开头定义）
        if (knowledgeMapper.selectByTitle(title) != null) {
            errorRows.add("第" + (rowIndex + 1) + "行：标题「" + title + "」已存在，跳过");
            return;
        }
        if (validList.stream().anyMatch(k -> k.getTitle() != null && k.getTitle().equals(title))) {
            errorRows.add("第" + (rowIndex + 1) + "行：标题「" + title + "」在文件中重复，跳过");
            return;
        }

        validList.add(knowledge);

    } catch (Exception e) {
        errorRows.add("第" + (rowIndex + 1) + "行：解析错误 - " + e.getMessage());
    }
}    /**
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
     */
    private boolean containsGarbled(String str) {
        if (str == null) {
            return false;
        }
        if (str.contains("\uFFFD")) {
            return true;
        }
        if (str.matches(".*\\?{3,}.*")) {
            return true;
        }
        int controlCharCount = 0;
        for (char c : str.toCharArray()) {
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                controlCharCount++;
            }
        }
        if (str.length() > 0 && controlCharCount > str.length() / 2) {
            return true;
        }
          // 3. 检测连续控制字符（连续3个及以上）
    if (str.matches(".*[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]{3,}.*")) {
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

    @Override
    public List<Knowledge> listByCondition(String keyword, String category, int offset, int size) {
        return knowledgeMapper.listByCondition(keyword, category, offset, size);
    }

    @Override
    public int countByCondition(String keyword, String category) {
        return knowledgeMapper.countByCondition(keyword, category);
    }

    @Override
    public List<String> getAllCategories() {
        return knowledgeMapper.getAllCategories();
    }

    @Override
    @Transactional
    public void add(Knowledge knowledge) {
          // ========== 敏感词检测（使用 SensitiveWordService）==========
        List<String> questionViolations = sensitiveWordService.findMatchingWords(knowledge.getTitle());
        List<String> answerViolations = sensitiveWordService.findMatchingWords(knowledge.getContent());
        
        StringBuilder violationMsg = new StringBuilder();
        if (!questionViolations.isEmpty()) {
            violationMsg.append("问题包含违规内容：" + String.join("、", questionViolations));
        }
        if (!answerViolations.isEmpty()) {
            if (!questionViolations.isEmpty()) {
                violationMsg.append("；");
            }
            violationMsg.append("答案包含违规内容：" + String.join("、", answerViolations));
        }
        
        if (violationMsg.length() > 0) {
            throw new RuntimeException(violationMsg.toString());
        }
        knowledge.setViewCount(0);
        knowledgeMapper.insert(knowledge);
    }
}