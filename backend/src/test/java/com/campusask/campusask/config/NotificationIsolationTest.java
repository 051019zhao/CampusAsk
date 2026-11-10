package com.campusask.campusask.config;

import com.campusask.campusask.context.BaseContext;
import com.campusask.campusask.controller.NotificationController;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class NotificationIsolationTest {
    @Test void markingReadCannotModifyAnotherUsersNotification() {
        JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa",""));
        db.execute("CREATE TABLE notification(id BIGINT PRIMARY KEY,user_id VARCHAR(50),is_read INT)");
        db.update("INSERT INTO notification VALUES(1,'alice',0),(2,'bob',0)");
        NotificationController controller=new NotificationController();ReflectionTestUtils.setField(controller,"jdbc",db);
        BaseContext.setCurrentUsername("alice");
        try {
            controller.read(2L);assertEquals(0,db.queryForObject("SELECT is_read FROM notification WHERE id=2",Integer.class));
            controller.read(1L);assertEquals(1,db.queryForObject("SELECT is_read FROM notification WHERE id=1",Integer.class));
        } finally {BaseContext.clear();}
    }
}
