package com.claimsure.notification.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.claimsure.common.error.ErrorAutoConfiguration;
import com.claimsure.common.security.SecurityAutoConfiguration;
import com.claimsure.notification.domain.Notification;
import com.claimsure.notification.domain.NotificationRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@ImportAutoConfiguration({SecurityAutoConfiguration.class, ErrorAutoConfiguration.class})
@TestPropertySource(properties = "claimsure.security.secret=unit-test-secret-0123456789-abcdefghij")
class NotificationControllerTest {
    @Autowired
    MockMvc mvc;
    @MockBean
    NotificationRepository repo;

    private static Notification n() {
        Notification n = new Notification();
        n.setTitle("Claim paid");
        n.setUserId("u1");
        return n;
    }

    @Test
    void listsOnlyCallersNotifications() throws Exception {
        when(repo.findByUserId(org.mockito.ArgumentMatchers.eq("u1"), any()))
                .thenReturn(new PageImpl<>(List.of(n())));
        mvc.perform(get("/api/v1/notifications").with(jwt().jwt(j -> j.subject("u1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Claim paid"));
    }

    @Test
    void cannotMarkSomeoneElsesNotificationAsRead() throws Exception {
        when(repo.findByIdAndUserId("n1", "u2")).thenReturn(Optional.empty());
        mvc.perform(post("/api/v1/notifications/n1/read").with(jwt().jwt(j -> j.subject("u2"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
