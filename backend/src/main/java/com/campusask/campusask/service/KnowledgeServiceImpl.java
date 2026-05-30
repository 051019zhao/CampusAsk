package com.campusask.campusask.service;

import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @Override
    public void importFromExcel(MultipartFile file) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            // 跳过表头，从第二行开始读
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                String title = row.getCell(0).getStringCellValue();
                String content = row.getCell(1).getStringCellValue();
                String category = row.getCell(2).getStringCellValue();
                String keywords = row.getCell(3).getStringCellValue();

                Knowledge knowledge = new Knowledge();
                knowledge.setTitle(title);
                knowledge.setContent(content);
                knowledge.setCategory(category);
                knowledge.setKeywords(keywords);
                knowledge.setViewCount(0);
                knowledge.setCreatedBy("admin");
                knowledgeMapper.insert(knowledge);
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel导入失败：" + e.getMessage());
        }
    }

    @Override
    public List<Knowledge> listAll() {
        return knowledgeMapper.listAll();
    }
}
