package com.campusask.campusask.rag;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.mapper.KnowledgeMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RagCoreTest {
    private RagStore store;
    private RagModelClient model;
    private RagService service;
    @BeforeEach void setup() {
        store=mock(RagStore.class); model=mock(RagModelClient.class);
        KnowledgeMapper knowledge=mock(KnowledgeMapper.class);
        when(knowledge.listAll()).thenReturn(List.of());
        when(model.embeddingsEnabled()).thenReturn(true);
        when(model.embeddingVersion()).thenReturn("test-v1");
        when(model.embed(anyString())).thenReturn(new double[]{1,0});
        when(model.answer(anyString(),anyString())).thenReturn("请参考 [S1]。");
        service=new RagService(store,model,new DocumentLoader(),knowledge);
        BaseContext.setCurrentUsername("alice");
    }
    @AfterEach void clear() { BaseContext.clear(); }
    @Test void chunksCoverSourceWithOverlap() {
        String text="校园资料".repeat(500);
        List<String> chunks=TextChunks.split(text);
        assertEquals(3,chunks.size());
        assertEquals(chunks.get(0).substring(680),chunks.get(1).substring(0,120));
        assertTrue(chunks.stream().allMatch(c -> c.length()<=800));
        assertTrue(text.endsWith(chunks.get(2)));
    }
    @Test void cosineHandlesIncompatibleModelsAndZeroVectors() {
        assertEquals(0,TextChunks.cosine(new double[]{1},new double[]{1,2}));
        assertEquals(0,TextChunks.cosine(new double[]{0,0},new double[]{1,2}));
        assertEquals(1,TextChunks.cosine(new double[]{1,0},new double[]{2,0}),1e-9);
    }
    @Test void csvPreservesQuotedCellsNewlinesAndHeaders() {
        String result=DocumentLoader.csv("地点,说明\r\n图书馆,\"周一,周二\n开放\"\r\n食堂,\"称为\"\"一饭\"\"\"");
        assertTrue(result.contains("地点=图书馆；说明=周一,周二\n开放"));
        assertTrue(result.contains("说明=称为\"一饭\""));
        assertThrows(IllegalArgumentException.class,()->DocumentLoader.csv("a,b\nx,\"bad"));
    }
    @Test void docxLoadsParagraphsAndTables() throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(XWPFDocument doc=new XWPFDocument()) {
            doc.createParagraph().createRun().setText("校园通知");
            doc.createTable(1,1).getRow(0).getCell(0).setText("图书馆开放");
            doc.write(bytes);
        }
        String text=new DocumentLoader().load(new MockMultipartFile("file","test.docx","application/octet-stream",bytes.toByteArray()));
        assertTrue(text.contains("校园通知")); assertTrue(text.contains("图书馆开放"));
    }
    @Test void loaderRejectsUnsupportedEmptyOversizeAndBadEncoding() {
        DocumentLoader loader=new DocumentLoader();
        assertThrows(IllegalArgumentException.class,()->loader.load(new MockMultipartFile("file","x.exe","",new byte[]{1})));
        assertThrows(IllegalArgumentException.class,()->loader.load(new MockMultipartFile("file","x.txt","",new byte[0])));
        assertThrows(IllegalArgumentException.class,()->loader.load(new MockMultipartFile("file","x.txt","",new byte[DocumentLoader.MAX_BYTES+1])));
        assertThrows(IllegalArgumentException.class,()->loader.load(new MockMultipartFile("file","x.csv","",new byte[]{(byte)0xff})));
    }
    @Test void privateOperationsRequireAuthenticatedOwner() {
        BaseContext.clear();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.documents("session"));
        verifyNoInteractions(store);
    }
    @Test void rejectsInvalidSessionBeforeReadingDocuments() {
        assertThrows(IllegalArgumentException.class,()->service.documents("../other"));
        verifyNoInteractions(store);
    }
    @Test void uploadedChunksAreEmbeddedBeforeAtomicPersistence() throws Exception {
        service.upload("session",new MockMultipartFile("file","test.csv","text/csv","地点,说明\n图书馆,开放".getBytes(StandardCharsets.UTF_8)));
        verify(model).embed(contains("地点=图书馆"));
        verify(store).replace(anyString(),eq("USER:alice"),eq("session"),eq("test.csv"),anyString(),argThat(chunks -> chunks.size()==1 && chunks.get(0).vector().equals("[1.0,0.0]")));
    }
    @Test void failedEmbeddingDoesNotCreatePartialDocument() {
        when(model.embed(anyString())).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,()->service.upload("session",new MockMultipartFile("file","test.txt","text/plain",new byte[]{65})));
        verify(store,never()).replace(anyString(),anyString(),anyString(),anyString(),anyString(),anyList());
    }
    @Test void memoryDisabledNeverReadsOrWritesHistory() {
        Map<String,Object> response=service.ask("图书馆在哪","session",false);
        assertEquals(false,response.get("memorySaved"));
        verify(store,never()).recent(anyString(),anyString());
        verify(store,never()).summary(anyString(),anyString());
        verify(store,never()).memories(anyString());
        verify(store,never()).remember(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void retrievesSemanticMatchAndReturnsTraceableCitation() {
        when(store.chunks("USER:alice","session")).thenReturn(List.of(new RagStore.Chunk("c1","d1","校园资料",2,"自习室位于二楼","[1,0]","test-v1")));
        Map<String,Object> response=service.ask("在哪里学习","session",false);
        assertEquals("hybrid",response.get("retrievalMode"));
        assertEquals(1,((List<?>)response.get("citations")).size());
        verify(model).answer(eq("在哪里学习"),contains("[S1] 校园资料 / 片段 2"));
    }
    @Test void remembersAndRecallsPersistedUserHistoryAcrossSessions() {
        when(store.memories("USER:alice")).thenReturn(List.of(new RagStore.Chunk("M1","old-session","历史对话",0,"我住五山校区","[1,0]","test-v1")));
        service.ask("我住哪个校区","new-session",true);
        verify(model).answer(eq("我住哪个校区"),contains("我住五山校区"));
        verify(store).remember(eq("USER:alice"),eq("new-session"),eq("我住哪个校区"),anyString(),anyString(),eq("test-v1"));
    }
    @Test void fallbackDoesNotContaminateMemory() {
        when(model.answer(anyString(),anyString())).thenThrow(new IllegalStateException());
        assertEquals("fallback",service.ask("校园问题","session",true).get("source"));
        verify(store,never()).remember(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
    }
    @Test void memorySaveFailureStillReturnsGeneratedAnswer() {
        doThrow(new IllegalStateException()).when(store).remember(anyString(),anyString(),anyString(),anyString(),anyString(),anyString());
        Map<String,Object> response=service.ask("校园问题","session",true);
        assertEquals("请参考 [S1]。",response.get("answer"));
        assertEquals(false,response.get("memorySaved"));
    }
    @Test void userSwitchAndDeletionUseCurrentIdentity() {
        service.documents("same-session"); BaseContext.setCurrentUsername("bob");
        service.documents("same-session"); service.delete("same-session","d1"); service.forget();
        verify(store).documents("USER:alice","same-session");
        verify(store).documents("USER:bob","same-session");
        verify(store).deleteDocument("USER:bob","same-session","d1");
        verify(store).forget("USER:bob");
    }
    @Test void embeddingOutageUsesLexicalDocumentsAndKeepsMemoryUsable() {
        when(model.embed(anyString())).thenThrow(new IllegalStateException("offline"));
        when(store.chunks("USER:alice","session")).thenReturn(List.of(new RagStore.Chunk("c","d","图书馆借书",1,"图书馆借书规则","[1,0]","test-v1")));
        var response=service.ask("图书馆借书","session",true);
        assertEquals("lexical",response.get("retrievalMode"));
        assertFalse(((List<?>)response.get("citations")).isEmpty());
        verify(store).remember(eq("USER:alice"),eq("session"),eq("图书馆借书"),anyString(),eq("[]"),eq("test-v1"));
    }
    @Test void missingEmbeddingConfigurationDoesNotDisablePublicQa() {
        when(model.embeddingsEnabled()).thenReturn(false);
        assertEquals("lexical",service.ask("校园问题","session",false).get("retrievalMode"));
        verify(model,never()).embed(anyString());
    }
}
