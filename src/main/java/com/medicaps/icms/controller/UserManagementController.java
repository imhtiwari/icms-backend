package com.medicaps.icms.controller;

import com.medicaps.icms.dto.UserManagementDTO;
import com.medicaps.icms.entity.Role;
import com.medicaps.icms.service.UserManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/user-management")
@CrossOrigin(origins = "http://localhost:3000")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    /**
     * Get all users for admin management
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getAllUsers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "search", required = false) String search) {
        
        try {
            Pageable pageable = PageRequest.of(page, size);
            Page<UserManagementDTO> users;
            
            if (search != null && !search.trim().isEmpty()) {
                users = userManagementService.searchUsers(search, pageable);
            } else {
                users = userManagementService.getAllUsersForManagement(pageable);
            }
            
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get user by ID for management
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getUserById(@PathVariable Long userId) {
        try {
            var user = userManagementService.getAllUsersForManagement(
                PageRequest.of(0, 1))
                .getContent()
                .stream()
                .filter(u -> u.getId().equals(userId))
                .findFirst();
            
            if (user.isPresent()) {
                return ResponseEntity.ok(user.get());
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Ban a user
     */
    @PostMapping("/{userId}/ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> banUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String reason = (String) request.get("reason");
            Integer banDays = (Integer) request.getOrDefault("banDays", 30);
            
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Ban reason is required"));
            }
            
            userManagementService.banUser(userId, reason.trim(), banDays);
            return ResponseEntity.ok(Map.of("message", "User banned successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Unban a user
     */
    @PostMapping("/{userId}/unban")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> unbanUser(@PathVariable Long userId) {
        try {
            userManagementService.unbanUser(userId);
            return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Add strikes to a user
     */
    @PostMapping("/{userId}/add-strikes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> addStrikes(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        
        try {
            Integer strikeCount = (Integer) request.get("strikeCount");
            if (strikeCount == null || strikeCount < 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "Strike count must be at least 1"));
            }
            
            userManagementService.addStrikes(userId, strikeCount);
            return ResponseEntity.ok(Map.of("message", "Strikes added successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Remove strikes from a user
     */
    @PostMapping("/{userId}/remove-strikes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> removeStrikes(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        
        try {
            Integer strikeCount = (Integer) request.get("strikeCount");
            if (strikeCount == null || strikeCount < 1) {
                return ResponseEntity.badRequest().body(Map.of("message", "Strike count must be at least 1"));
            }
            
            userManagementService.removeStrikes(userId, strikeCount);
            return ResponseEntity.ok(Map.of("message", "Strikes removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Enable/disable user account
     */
    @PostMapping("/{userId}/toggle-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> toggleUserStatus(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        
        try {
            Boolean enabled = (Boolean) request.get("enabled");
            userManagementService.toggleUserStatus(userId, enabled);
            return ResponseEntity.ok(Map.of("message", 
                enabled ? "User enabled successfully" : "User disabled successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Change user role
     */
    @PostMapping("/{userId}/change-role")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> changeUserRole(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request) {
        
        try {
            String roleName = (String) request.get("role");
            if (roleName == null || roleName.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Role is required"));
            }
            
            Role newRole;
            try {
                newRole = Role.valueOf(roleName.toUpperCase());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", "Invalid role: " + roleName));
            }
            
            userManagementService.changeUserRole(userId, newRole);
            return ResponseEntity.ok(Map.of("message", "User role changed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
