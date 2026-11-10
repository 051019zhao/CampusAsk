package com.campusask.campusask.config;

import com.campusask.campusask.controller.QaController;
import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.rag.*;
import com.campusask.campusask.service.QaService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;

class ApiContractTest {
    private MockMvc mvc;
    private RagService rag;
    private QaService legacy;
    @BeforeEach void setup() {
        QaController qa=new QaController();rag=mock(RagService.class);legacy=mock(QaService.class);
        ReflectionTestUtils.setField(qa,"ragService",rag);ReflectionTestUtils.setField(qa,"qaService",legacy);
        when(rag.ask(anyString(),anyString(),anyBoolean())).thenReturn(Map.of("answer","测试回答","source","ai_knowledge"));
        mvc=MockMvcBuilders.standaloneSetup(qa).setControllerAdvice(new ApiExceptionHandler(),new RagExceptionHandler()).addFilters(new RequestTraceFilter()).build();
    }
    @AfterEach void clear() {BaseContext.clear();}
    @Test void rejectsEmptyOversizeAndInvalidSessions() throws Exception {
        for(String body:List.of("{}","{\"keyword\":\" \"}","{\"keyword\":\"问\",\"sessionId\":\"../private\"}","{\"keyword\":\""+"问".repeat(2001)+"\"}"))
            mvc.perform(post("/api/qa/ask").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(rag);
    }
    @Test void successfulRequestHasTraceIdAndNoMdcLeak() throws Exception {
        mvc.perform(post("/api/qa/ask").header("X-Request-ID","attacker\ninput").contentType("application/json").content("{\"keyword\":\"校园问题\",\"sessionId\":\"s1\"}"))
                .andExpect(status().isOk()).andExpect(header().exists("X-Request-ID")).andExpect(jsonPath("$.code").value(200));
        org.junit.jupiter.api.Assertions.assertNull(org.slf4j.MDC.get("requestId"));
        verify(rag).ask("校园问题","s1",true);
    }
    @Test void historyUsesAuthenticatedIdentityInsteadOfQueryParameter() throws Exception {
        BaseContext.setCurrentUsername("alice");when(legacy.getRecentQuestions("alice",6)).thenReturn(List.of());
        mvc.perform(get("/api/qa/history?userId=bob")).andExpect(status().isOk());
        verify(legacy).getRecentQuestions("alice",6);verify(legacy,never()).getRecentQuestions("bob",6);
    }
    @Test void malformedJsonReturnsConsistentBadRequest() throws Exception {
        mvc.perform(post("/api/qa/ask").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
    }
    @Test void internalErrorsDoNotExposeImplementationDetails() throws Exception {
        when(rag.ask(anyString(),anyString(),anyBoolean())).thenThrow(new RuntimeException("private database connection detail"));
        mvc.perform(post("/api/qa/ask").contentType("application/json").content("{\"keyword\":\"问题\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(500))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database"))));
    }
}
