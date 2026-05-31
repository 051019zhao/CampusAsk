package com.campusask.campusask.controller;

import com.campusask.campusask.dto.Result;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeMapper knowledgeMapper;

    @GetMapping("/list")
    public Result<List<Knowledge>> list() {
        return Result.success(knowledgeMapper.listAll());
    }

    @PostMapping("/add")
    public Result<String> add(@RequestBody Knowledge knowledge) {
        knowledge.setViewCount(0);
        knowledgeMapper.insert(knowledge);
        return Result.success("添加成功");
    }

    @PostMapping("/import")
    public Result<String> importExcel(@RequestParam("file") MultipartFile file) {
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Knowledge> list = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Knowledge k = new Knowledge();
                k.setTitle(getCellValue(row, 0));
                k.setContent(getCellValue(row, 1));
                k.setCategory(getCellValue(row, 2));
                k.setKeywords(getCellValue(row, 3));
                k.setViewCount(0);
                k.setCreatedBy("admin");
                list.add(k);
            }
            list.forEach(knowledgeMapper::insert);
            return Result.success("成功导入 " + list.size() + " 条");
        } catch (Exception e) {
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    private String getCellValue(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }
}
