package com.ridehailing.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ridehailing.core.dto.AuthResponse;
import com.ridehailing.core.dto.LoginRequest;
import com.ridehailing.core.dto.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeEach
    void cleanup() {
        jdbcClient.sql("DELETE FROM wallets").update();
        jdbcClient.sql("DELETE FROM users").update();
    }

    @Test
    void registerCustomer_success() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "customer@test.com",
            "password123",
            "John Doe",
            "CUSTOMER"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void registerDriver_success() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "driver@test.com",
            "password123",
            "Jane Smith",
            "DRIVER"
        );

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void registerDuplicateEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest(
            "duplicate@test.com",
            "password123",
            "Test User",
            "CUSTOMER"
        );

        // First registration
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());

        // Duplicate registration
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
    }

    @Test
    void login_success() throws Exception {
        // Register first
        RegisterRequest registerRequest = new RegisterRequest(
            "login@test.com",
            "password123",
            "Login Test",
            "CUSTOMER"
        );
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));

        // Then login
        LoginRequest loginRequest = new LoginRequest("login@test.com", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void loginWrongPassword_returns401() throws Exception {
        // Register first
        RegisterRequest registerRequest = new RegisterRequest(
            "wrong@test.com",
            "password123",
            "Wrong Test",
            "CUSTOMER"
        );
        mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(registerRequest)));

        // Login with wrong password
        LoginRequest loginRequest = new LoginRequest("wrong@test.com", "wrongpassword");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginNonExistentEmail_returns401() throws Exception {
        LoginRequest loginRequest = new LoginRequest("nonexistent@test.com", "password123");
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void accessProtectedEndpoint_withValidToken_success() throws Exception {
        // Register customer
        RegisterRequest registerRequest = new RegisterRequest(
            "protected@test.com",
            "password123",
            "Protected Test",
            "CUSTOMER"
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String token = authResponse.accessToken();

        // Access protected endpoint (example: /api/v1/rides/**)
        mockMvc.perform(get("/api/v1/rides/test")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound()); // 404 because endpoint doesn't exist yet, but auth passed
    }

    @Test
    void accessProtectedEndpoint_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/rides/test"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accessProtectedEndpoint_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/rides/test")
                .header("Authorization", "Bearer invalid.token.here"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void accessRidesEndpoint_withDriverToken_returns403() throws Exception {
        // Register driver
        RegisterRequest registerRequest = new RegisterRequest(
            "driver403@test.com",
            "password123",
            "Driver Forbidden",
            "DRIVER"
        );
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        String token = authResponse.accessToken();

        // Driver tries to access customer-only endpoint
        mockMvc.perform(get("/api/v1/rides/test")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    void accessInternalEndpoint_withValidKey_success() throws Exception {
        mockMvc.perform(get("/internal/test")
                .header("X-Internal-Key", "test-internal-key"))
            .andExpect(status().isNotFound()); // 404 because endpoint doesn't exist, but key check passed
    }

    @Test
    void accessInternalEndpoint_withoutKey_returns403() throws Exception {
        mockMvc.perform(get("/internal/test"))
            .andExpect(status().isForbidden());
    }

    @Test
    void accessInternalEndpoint_withInvalidKey_returns403() throws Exception {
        mockMvc.perform(get("/internal/test")
                .header("X-Internal-Key", "wrong-key"))
            .andExpect(status().isForbidden());
    }
}
