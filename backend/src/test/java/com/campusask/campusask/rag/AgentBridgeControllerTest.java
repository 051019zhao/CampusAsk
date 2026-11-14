package com.campusask.campusask.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentBridgeControllerTest {
    @Test void modelCallbackReturnsPlainJsonFields() throws Exception {
        RagModelClient model=mock(RagModelClient.class);
        AgentBridgeController bridge=new AgentBridgeController(mock(RagService.class),mock(RagStore.class),model);
        String key="x".repeat(32);
        ReflectionTestUtils.setField(bridge,"sharedKey",key);
        when(model.agentTurn(anyList(),anyList())).thenReturn(new ObjectMapper().readTree("""
                {"role":"assistant","content":null,"tool_calls":[{"id":"call-1","function":{"name":"current_date","arguments":"{}"}}]}
                """));
        Map<String,Object> result=bridge.model(key,new AgentBridgeController.ModelRequest(
                List.of(Map.of("role","user","content","今天几号")),List.of(Map.of("type","function"))));
        assertEquals("assistant",result.get("role"));
        assertTrue(result.containsKey("tool_calls"));
        assertFalse(result.containsKey("object"));
    }
    @Test void callbackRejectsMissingSharedKey() {
        AgentBridgeController bridge=new AgentBridgeController(mock(RagService.class),mock(RagStore.class),mock(RagModelClient.class));
        ReflectionTestUtils.setField(bridge,"sharedKey","x".repeat(32));
        assertThrows(ResponseStatusException.class,()->bridge.model(null,
                new AgentBridgeController.ModelRequest(List.of(),List.of())));
    }
}
