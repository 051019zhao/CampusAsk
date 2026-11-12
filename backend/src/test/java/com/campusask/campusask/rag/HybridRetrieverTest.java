package com.campusask.campusask.rag;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class HybridRetrieverTest {
    private RagStore.Chunk chunk(String text,String vector,String model) {return new RagStore.Chunk(text,text,"资料",1,text,vector,model);}
    @Test void lexicalFallbackWorksWithoutModelOrOnModelVersionMismatch() {
        HybridRetriever retriever=new HybridRetriever();
        var chunks=List.of(chunk("图书馆借书","[1,0]","old"),chunk("食堂餐饮","[0,1]","old"));
        assertEquals("图书馆借书",retriever.retrieve("图书馆借书",new double[0],chunks,"new",6).get(0).chunk().text());
        assertEquals("图书馆借书",retriever.retrieve("图书馆借书",new double[]{1,0},chunks,"new",6).get(0).chunk().text());
    }
    @Test void invalidVectorsDoNotBreakLexicalSearch() {
        assertEquals(1,new HybridRetriever().retrieve("选课",new double[]{1,0},List.of(chunk("选课","not-json","v1")),"v1",6).size());
    }
    @Test void exactChineseMatchSurvivesDisagreeingEmbedding() {
        var hits=new HybridRetriever().retrieve("图书馆借书",new double[]{1,0},
                List.of(chunk("图书馆借书","[0,1]","v1"),chunk("食堂餐饮","[1,0]","v1")),"v1",6);
        assertEquals(2,hits.size());
        assertEquals("图书馆借书",hits.get(0).chunk().text());
        assertEquals(1.0,hits.get(0).score(),1e-9);
    }
    @Test void semanticRetrievalCanFindDifferentWordingAndRespectsTopK() {
        var hits=new HybridRetriever().retrieve("去哪学习",new double[]{1,0},List.of(chunk("自习室二楼","[1,0]","v1"),chunk("球场","[0,1]","v1")),"v1",1);
        assertEquals(1,hits.size());assertEquals("自习室二楼",hits.get(0).chunk().text());
    }
}
