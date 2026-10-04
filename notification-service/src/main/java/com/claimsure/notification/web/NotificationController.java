package com.claimsure.notification.web;

import com.claimsure.common.error.ResourceNotFoundException;
import com.claimsure.common.web.PageResponse;
import com.claimsure.notification.domain.Notification;
import com.claimsure.notification.domain.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
@SecurityRequirement(name = "bearer")
public class NotificationController {
    public record NotificationView(String id, String type, String title, String message, String reference,
                                   boolean read, Instant createdAt) {
        static NotificationView of(Notification n) {
            return new NotificationView(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getReference(),
                    n.isRead(), n.getCreatedAt());
        }
    }

    private final NotificationRepository repo;

    public NotificationController(NotificationRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @Operation(summary = "My notifications, newest first")
    public PageResponse<NotificationView> mine(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size,
                                               @AuthenticationPrincipal Jwt jwt) {
        Page<Notification> p = repo.findByUserId(jwt.getSubject(), PageRequest.of(Math.max(page, 0),
                Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), NotificationView::of);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unread", repo.countByUserIdAndReadFalse(jwt.getSubject()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationView markRead(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        Notification n = repo.findByIdAndUserId(id, jwt.getSubject())
                .orElseThrow(() -> new ResourceNotFoundException("Notification", id));
        n.setRead(true);
        return NotificationView.of(repo.save(n));
    }
}
