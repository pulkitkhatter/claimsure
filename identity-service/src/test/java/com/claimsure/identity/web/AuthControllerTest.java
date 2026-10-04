package com.claimsure.identity.web;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimsure.common.error.ConflictException;
import com.claimsure.common.error.ErrorAutoConfiguration;
import com.claimsure.common.security.SecurityAutoConfiguration;
import com.claimsure.identity.service.AuthService;
import com.claimsure.identity.web.Dtos.AuthResponse;
import com.claimsure.identity.web.Dtos.UserView;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ErrorAutoConfiguration.class})
@org.springframework.context.annotation.Import(AuthExceptionHandler.class)
@TestPropertySource(properties = {
        "claimsure.security.secret=unit-test-secret-0123456789-abcdefghij",
        "claimsure.security.public-paths=/api/v1/auth/register,/api/v1/auth/login"})
class AuthControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    AuthService auth;

    @Test
    void registerReturns201() throws Exception {
        when(auth.register(any())).thenReturn(new UserView("1", "a@b.co", "Ann", Set.of("CUSTOMER")));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"Str0ngPassword\",\"fullName\":\"Ann\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("a@b.co"));
    }

    @Test
    void weakPasswordIsRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"short\",\"fullName\":\"Ann\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void duplicateEmailIs409() throws Exception {
        when(auth.register(any())).thenThrow(new ConflictException("exists"));
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"Str0ngPassword\",\"fullName\":\"Ann\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void loginSuccess() throws Exception {
        when(auth.login(any())).thenReturn(new AuthResponse("tok", "Bearer", 3600,
                new UserView("1", "a@b.co", "Ann", Set.of("CUSTOMER"))));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("tok"));
    }

    @Test
    void badCredentialsAre401WithProblemJson() throws Exception {
        when(auth.login(any())).thenThrow(new BadCredentialsException("Invalid email or password"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.co\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsProfile() throws Exception {
        when(auth.me("u-9")).thenReturn(new UserView("u-9", "a@b.co", "Ann", Set.of("CUSTOMER")));
        mvc.perform(get("/api/v1/auth/me").with(jwt().jwt(j -> j.subject("u-9"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("u-9"));
    }
}
