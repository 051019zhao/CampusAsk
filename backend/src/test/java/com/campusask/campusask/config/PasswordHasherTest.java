package com.campusask.campusask.config;

import com.campusask.campusask.utils.PasswordHasher;
import com.campusask.campusask.service.impl.UserServiceImpl;
import com.campusask.campusask.mapper.UserMapper;
import com.campusask.campusask.entity.User;
import com.campusask.campusask.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class PasswordHasherTest {
    @Test void saltedHashesFitExistingColumnAndRejectWrongPassword() {
        String first=PasswordHasher.hash("sample-password"),second=PasswordHasher.hash("sample-password");
        assertNotEquals(first,second);assertTrue(first.length()<=100);
        assertTrue(PasswordHasher.matches("sample-password",first));assertFalse(PasswordHasher.matches("wrong-password",first));
    }
    @Test void supportsLegacyMd5AndRejectsMalformedHashes() {
        String legacy=org.springframework.util.DigestUtils.md5DigestAsHex("old-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(PasswordHasher.matches("old-test",legacy));assertTrue(PasswordHasher.legacy(legacy));
        for(String bad:new String[]{"broken","pbkdf2$999999999$YQ==$YQ==","pbkdf2$600000$bad$bad"}) assertFalse(PasswordHasher.matches("old-test",bad));
    }
    @Test void validatesNewPasswordsWithoutChangingLegacyLoginPolicy() {
        assertThrows(IllegalArgumentException.class,()->PasswordHasher.validateNew("short"));
        assertThrows(IllegalArgumentException.class,()->PasswordHasher.validateNew("x".repeat(129)));
        assertDoesNotThrow(()->PasswordHasher.validateNew("valid-new-password"));
    }
    @Test void successfulLegacyLoginUsesCompareAndSetUpgrade() {
        UserMapper mapper=mock(UserMapper.class);UserServiceImpl service=new UserServiceImpl();ReflectionTestUtils.setField(service,"userMapper",mapper);
        String old=org.springframework.util.DigestUtils.md5DigestAsHex("old-test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        User user=new User();user.setUsername("202600000001");user.setRole("0");user.setPassword(old);
        when(mapper.getByUsername(user.getUsername())).thenReturn(user);when(mapper.upgradePassword(anyString(),anyString(),anyString())).thenReturn(1);
        UserDTO request=new UserDTO();request.setUsername(user.getUsername());request.setRole("0");request.setPassword("old-test");
        assertTrue(service.login(request).getPassword().startsWith("pbkdf2$"));
        verify(mapper).upgradePassword(eq(user.getUsername()),eq(old),startsWith("pbkdf2$"));
    }
}
