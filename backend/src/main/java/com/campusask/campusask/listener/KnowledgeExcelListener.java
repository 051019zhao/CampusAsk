package com.campusask.campusask.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.campusask.campusask.dto.KnowledgeExcelDTO;
import com.campusask.campusask.entity.Knowledge;
import com.campusask.campusask.mapper.KnowledgeMapper;
import java.util.ArrayList;
import java.util.List;

public class KnowledgeExcelListener extends AnalysisEventListener<KnowledgeExcelDTO> {

    private final KnowledgeMapper mapper;
    private List<Knowledge> tempList = new ArrayList<>();

    public KnowledgeExcelListener(KnowledgeMapper mapper){
        this.mapper = mapper;
    }

    @Override
    public void invoke(KnowledgeExcelDTO dto, AnalysisContext context) {
        Knowledge k = new Knowledge();
        k.setTitle(dto.getTitle());
        k.setContent(dto.getContent());
        k.setCategory(dto.getCategory());
        k.setKeywords(dto.getKeywords());
        tempList.add(k);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if(!tempList.isEmpty()){
            for(Knowledge item : tempList){
                mapper.insert(item);
            }
        }
    }
}