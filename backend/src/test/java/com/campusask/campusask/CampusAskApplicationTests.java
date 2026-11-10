package com.campusask.campusask;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.campusask.campusask.service.QuestionLogProcessor;
import com.campusask.campusask.config.QuestionLogSchemaInitializer;

@SpringBootTest
@ActiveProfiles("test")
class CampusAskApplicationTests {

    @MockitoBean QuestionLogProcessor processor;
    @MockitoBean QuestionLogSchemaInitializer legacySchemaInitializer;

    @Test
    void contextLoads() {
    }

}
