package com.campusask.campusask.rag;

import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

@Component
public class DocumentLoader {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public String load(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new IllegalArgumentException("请选择不超过 5 MB 的文件");
        String name = Objects.toString(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
        String text;
        if (name.endsWith(".docx")) {
            StringBuilder body = new StringBuilder();
            try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                for (IBodyElement part : doc.getBodyElements()) {
                    if (part instanceof XWPFParagraph p) body.append(p.getText()).append('\n');
                    if (part instanceof XWPFTable table) body.append(table.getText()).append('\n');
                    if (body.length() > 200_000) throw new IllegalArgumentException("文档正文超过 20 万字符");
                }
            }
            text = body.toString();
        } else if (name.endsWith(".csv") || name.endsWith(".txt") || name.endsWith(".md")) {
            try {
                text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(file.getBytes())).toString().replace("\uFEFF", "");
            } catch (CharacterCodingException e) {
                throw new IllegalArgumentException("请将文本或 CSV 保存为 UTF-8 编码");
            }
            if (name.endsWith(".csv")) text = csv(text);
        } else throw new IllegalArgumentException("支持 CSV、DOCX、TXT、MD 文件");
        if (text.isBlank()) throw new IllegalArgumentException("文件没有可检索的正文");
        if (text.length() > 200_000) throw new IllegalArgumentException("文档正文超过 20 万字符");
        return text;
    }

    /** RFC 4180 quoting, escaped quotes and embedded newlines; preserve headers on each row. */
    static String csv(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < text.length() && text.charAt(i+1) == '"') { cell.append('"'); i++; }
                else quoted = !quoted;
            } else if (!quoted && (c == ',' || c == '\n' || c == '\r')) {
                row.add(cell.toString()); cell.setLength(0);
                if (c != ',') {
                    rows.add(row); row = new ArrayList<>();
                    if (c == '\r' && i+1 < text.length() && text.charAt(i+1) == '\n') i++;
                }
            } else cell.append(c);
        }
        if (quoted) throw new IllegalArgumentException("CSV 引号未闭合");
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        if (rows.isEmpty()) return "";
        List<String> headers = rows.get(0);
        StringBuilder result = new StringBuilder();
        for (int i = 1; i < rows.size(); i++) {
            result.append("第 ").append(i + 1).append(" 行：");
            for (int j = 0; j < rows.get(i).size(); j++) {
                result.append(j < headers.size() ? headers.get(j) : "列" + (j+1))
                        .append('=').append(rows.get(i).get(j)).append("；");
            }
            result.append('\n');
        }
        return result.toString();
    }
}
