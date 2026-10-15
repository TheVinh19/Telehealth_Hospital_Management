package com.telehealth.identity;

import com.telehealth.contracts.enums.Role;
import com.telehealth.identity.dto.LoginRequest;
import com.telehealth.identity.dto.LoginResponse;
import com.telehealth.identity.dto.RegisterRequest;
import com.telehealth.identity.dto.UserResponse;
import com.telehealth.identity.dto.CreateDoctorRequest;
import com.telehealth.identity.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class IdentityServiceApplicationTests {

    @Autowired
    private AuthService authService;

    @Test
    void testDemoUsersSeeded() {
        LoginResponse adminLogin = authService.login(LoginRequest.builder()
                .email("admin@demo.com")
                .password("Admin123!")
                .build());
        assertNotNull(adminLogin.getAccessToken());
        assertEquals(Role.ADMIN, adminLogin.getUser().getRole());

        LoginResponse doctorLogin = authService.login(LoginRequest.builder()
                .email("doctor.anh@demo.com")
                .password("Doctor123!")
                .build());
        assertNotNull(doctorLogin.getAccessToken());
        assertEquals(Role.DOCTOR, doctorLogin.getUser().getRole());

        LoginResponse patientLogin = authService.login(LoginRequest.builder()
                .email("patient1@demo.com")
                .password("Patient123!")
                .build());
        assertNotNull(patientLogin.getAccessToken());
        assertEquals(Role.PATIENT, patientLogin.getUser().getRole());
    }

    @Test
    void testRegisterAndLogin() {
        String testEmail = "testnewpatient@demo.com";
        UserResponse registered = authService.register(RegisterRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .displayName("Người Dùng Mới")
                .build());

        assertNotNull(registered.getId());
        assertEquals(testEmail, registered.getEmail());
        assertEquals(Role.PATIENT, registered.getRole());

        // Duplicate registration should fail
        assertThrows(IllegalArgumentException.class, () ->
                authService.register(RegisterRequest.builder()
                        .email(testEmail)
                        .password("AnotherPass123!")
                        .displayName("Duplicate")
                        .build())
        );

        // Login with wrong password should fail
        assertThrows(IllegalArgumentException.class, () ->
                authService.login(LoginRequest.builder()
                        .email(testEmail)
                        .password("WrongPassword")
                        .build())
        );

        // Login with correct password
        LoginResponse login = authService.login(LoginRequest.builder()
                .email(testEmail)
                .password("SecurePass123!")
                .build());
        assertNotNull(login.getAccessToken());
        assertNotNull(login.getRefreshToken());
    }

    @Test
    void adminCanDeactivateDoctorAccount() {
        UserResponse doctor = authService.createDoctor(CreateDoctorRequest.builder()
                .email("doctor.delete.test@hospital.vn")
                .password("Doctor123!")
                .displayName("BS. Kiểm Thử Xóa")
                .build());

        UserResponse inactive = authService.deactivateDoctor(doctor.getId());
        assertEquals("INACTIVE", inactive.getStatus());
        assertThrows(IllegalStateException.class, () -> authService.login(LoginRequest.builder()
                .email("doctor.delete.test@hospital.vn")
                .password("Doctor123!")
                .build()));
    }
}
