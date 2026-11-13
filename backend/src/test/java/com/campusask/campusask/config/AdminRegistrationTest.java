package com.campusask.campusask.config;

import com.campusask.campusask.controller.UserController;
import com.campusask.campusask.dto.UserDTO;
import com.campusask.campusask.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AdminRegistrationTest {
    @Test void disabledRegistrationStopsBeforeUserService() {
        UserController controller=new UserController();
        UserService users=mock(UserService.class);
        ReflectionTestUtils.setField(controller,"userService",users);
        ReflectionTestUtils.setField(controller,"adminRegistrationKey","");
        UserDTO request=new UserDTO();
        request.setRole("1");
        request.setRegisterKey("example-key");
        assertEquals("管理员自助注册未启用，请联系管理员",controller.register(request).getMessage());
        verifyNoInteractions(users);
    }
}