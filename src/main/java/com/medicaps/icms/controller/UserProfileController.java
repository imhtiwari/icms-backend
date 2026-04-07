package com.medicaps.icms.controller;

import com.medicaps.icms.entity.User;
import com.medicaps.icms.service.StrikeService;
import com.medicaps.icms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "http://localhost:3000")
public class UserProfileController {

    @Autowired
    private UserService userService;

    @Autowired
    private StrikeService strikeService;

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        String email = authentication.getName();
        boolean canFile = strikeService.canUserCreateComplaint(email);
        User user = userService.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Map<String, Object> body = new HashMap<>();
        body.put("id", user.getId());
        body.put("name", user.getName());
        body.put("email", user.getEmail());
        body.put("role", user.getRole().name());
        body.put("enabled", user.isEnabled());
        body.put("strikeCount", user.getStrikeCount() != null ? user.getStrikeCount() : 0);
        body.put("lastStrikeDate", user.getLastStrikeDate());
        body.put("isBanned", Boolean.TRUE.equals(user.getIsBanned()));
        body.put("banReason", user.getBanReason());
        body.put("complaintBannedUntil", user.getComplaintBannedUntil());
        body.put("canSubmitComplaints", canFile);
        return ResponseEntity.ok(body);
    }
}
