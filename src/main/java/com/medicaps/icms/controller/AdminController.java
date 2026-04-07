package com.medicaps.icms.controller;

import com.medicaps.icms.entity.Role;
import com.medicaps.icms.entity.User;
import com.medicaps.icms.service.StrikeService;
import com.medicaps.icms.service.UserService;
import com.medicaps.icms.service.UserResetService;
import com.medicaps.icms.service.ComplaintService;
import com.medicaps.icms.service.UserManagementService;
import com.medicaps.icms.dto.UserManagementDTO;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "http://localhost:3000")
public class AdminController {

    @Autowired
    private UserService userService;

    @Autowired
    private StrikeService strikeService;

    @Autowired
    private UserResetService userResetService;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private UserManagementService userManagementService;

    private Map<String, Object> userToResponseMap(User user) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole().name());
        userMap.put("enabled", user.isEnabled());
        userMap.put("emailVerified", user.isEmailVerified());
        userMap.put("strikeCount", user.getStrikeCount() != null ? user.getStrikeCount() : 0);
        userMap.put("isBanned", Boolean.TRUE.equals(user.getIsBanned()));
        userMap.put("banReason", user.getBanReason());
        userMap.put("lastStrikeDate", user.getLastStrikeDate());
        userMap.put("complaintBannedUntil", user.getComplaintBannedUntil());
        return userMap;
    }

    @PostMapping("/create-admin")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> createAdmin(@Valid @RequestBody CreateUserRequest request) {
        try {
            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());

            User createdAdmin = userService.createAdmin(user);
            return ResponseEntity.ok(Map.of(
                    "message", "Admin created successfully",
                    "user", Map.of(
                            "id", createdAdmin.getId(),
                            "name", createdAdmin.getName(),
                            "email", createdAdmin.getEmail(),
                            "role", createdAdmin.getRole().name())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/create-worker")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> createWorker(@Valid @RequestBody CreateUserRequest request) {
        try {
            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());

            User createdWorker = userService.createWorker(user);
            return ResponseEntity.ok(Map.of(
                    "message", "Worker created successfully",
                    "user", Map.of(
                            "id", createdWorker.getId(),
                            "name", createdWorker.getName(),
                            "email", createdWorker.getEmail(),
                            "role", createdWorker.getRole().name())));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/create-admin-temp")
    public ResponseEntity<?> createAdminTemp(@RequestBody Map<String, String> request) {
        try {
            String email = request.get("email");
            if (email == null || email.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Email is required"));
            }

            System.out.println("Creating admin for email: " + email);

            // Find user by email
            Optional<User> userOpt = userService.findByEmail(email);
            if (userOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "User not found with email: " + email));
            }

            User user = userOpt.get();
            user.setRole(Role.ADMIN);
            userService.updateUser(user.getId(), user);

            System.out.println("Successfully upgraded user to admin: " + email);

            return ResponseEntity.ok(Map.of(
                    "message", "User upgraded to admin successfully",
                    "email", email,
                    "newRole", "ADMIN"));
        } catch (Exception e) {
            System.out.println("Error creating admin: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/public-check")
    public ResponseEntity<?> publicCheck() {
        try {
            System.out.println("Public check endpoint accessed");
            return ResponseEntity.ok(Map.of(
                    "message", "Backend is running",
                    "timestamp", java.time.LocalDateTime.now().toString()));
        } catch (Exception e) {
            System.out.println("Error in public check: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/check-role")
    public ResponseEntity<?> checkRole(Authentication authentication) {
        try {
            if (authentication == null) {
                System.out.println("No authentication found");
                return ResponseEntity.ok(Map.of(
                        "message", "No authentication found",
                        "authenticated", false));
            }

            System.out.println("Checking role for user: " + authentication.getName());
            System.out.println("Authorities: " + authentication.getAuthorities());

            return ResponseEntity.ok(Map.of(
                    "username", authentication.getName(),
                    "authorities", authentication.getAuthorities().toString(),
                    "authenticated", true));
        } catch (Exception e) {
            System.out.println("Error checking role: " + e.getMessage());
            return ResponseEntity.ok(Map.of(
                    "message", "Error: " + e.getMessage(),
                    "authenticated", false));
        }
    }

    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getAllUsers(Authentication authentication) {
        try {
            System.out.println("GET /admin/users - User: " + authentication.getName() + " with authorities: "
                    + authentication.getAuthorities());
            List<User> users = userService.getAllUsers();
            System.out.println("Found " + users.size() + " users");

            // Convert to DTO to avoid serialization issues
            List<Map<String, Object>> userDtos = users.stream()
                    .map(user -> {
                        System.out.println("Processing user: " + user.getEmail() + " with role: " + user.getRole());
                        return userToResponseMap(user);
                    })
                    .toList();
            System.out.println("Successfully created DTOs for " + userDtos.size() + " users");
            return ResponseEntity.ok(userDtos);
        } catch (Exception e) {
            System.out.println("Error fetching users: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/workers")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getWorkers() {
        try {
            List<User> workers = userService.findEnabledUsersByRole(Role.WORKER);
            // Convert to DTO to avoid serialization issues
            List<Map<String, Object>> workerDtos = workers.stream()
                    .map(worker -> {
                        Map<String, Object> workerMap = new HashMap<>();
                        workerMap.put("id", worker.getId());
                        workerMap.put("name", worker.getName());
                        workerMap.put("email", worker.getEmail());
                        workerMap.put("role", worker.getRole().name());
                        return workerMap;
                    })
                    .toList();
            return ResponseEntity.ok(workerDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/admins")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<?> getAdmins() {
        try {
            List<User> admins = userService.findEnabledUsersByRole(Role.ADMIN);
            // Convert to DTO to avoid serialization issues
            List<Map<String, Object>> adminDtos = admins.stream()
                    .map(admin -> {
                        Map<String, Object> adminMap = new HashMap<>();
                        adminMap.put("id", admin.getId());
                        adminMap.put("name", admin.getName());
                        adminMap.put("email", admin.getEmail());
                        adminMap.put("role", admin.getRole().name());
                        return adminMap;
                    })
                    .toList();
            return ResponseEntity.ok(adminDtos);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getUserById(@PathVariable Long id, Authentication authentication) {
        try {
            System.out.println("GET /admin/users/" + id + " - User: " + authentication.getName());

            Optional<User> userOpt = userService.findById(id);
            if (userOpt.isEmpty()) {
                System.out.println("User not found with ID: " + id);
                return ResponseEntity.notFound().build();
            }

            User user = userOpt.get();
            System.out.println("Found user: " + user.getEmail());

            return ResponseEntity.ok(userToResponseMap(user));
        } catch (Exception e) {
            System.out.println("Error fetching user: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        try {
            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());
            user.setRole(Role.valueOf(request.getRole().toUpperCase()));
            user.setEnabled(request.isEnabled());

            User updatedUser = userService.updateUser(id, user);
            return ResponseEntity.ok(userToResponseMap(updatedUser));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/users/{id}")
    // @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            userService.deleteUser(id);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{email:.+}/strike")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> addStrikeToUser(@PathVariable String email,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            int count = 1;
            if (body != null && body.get("strikeCount") instanceof Number n) {
                count = Math.max(1, Math.min(10, n.intValue()));
            }
            strikeService.addStrikes(email, count);
            User updated = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(userToResponseMap(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{email:.+}/remove-strike")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> removeStrikeFromUser(@PathVariable String email) {
        try {
            strikeService.removeStrike(email);
            User updated = userService.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            return ResponseEntity.ok(userToResponseMap(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{email:.+}/ban")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> banUser(@PathVariable String email, @RequestBody Map<String, String> body) {
        try {
            String reason = body != null ? body.get("reason") : null;
            if (reason == null || reason.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Ban reason is required"));
            }
            User updated = strikeService.banUserAccount(email, reason.trim());
            return ResponseEntity.ok(userToResponseMap(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getUserStats() {
        try {
            Map<String, Object> stats = Map.of(
                    "totalUsers", userService.getAllUsers().size(),
                    "totalOwners", userService.countUsersByRole(Role.OWNER),
                    "totalAdmins", userService.countUsersByRole(Role.ADMIN),
                    "totalWorkers", userService.countUsersByRole(Role.WORKER),
                    "totalRegularUsers", userService.countUsersByRole(Role.USER));
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DTO classes
    public static class CreateUserRequest {
        private String name;
        private String email;
        private String password;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class UpdateUserRequest {
        private String name;
        private String email;
        private String password;
        private String role;
        private boolean enabled;

        // Getters and Setters
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @PostMapping("/users/{userId}/reset-strikes")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> resetUserStrikes(@PathVariable Long userId) {
        try {
            userResetService.resetUserStrikeStatusById(userId);
            return ResponseEntity.ok(Map.of("message", "User strikes reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/users/{userId}/remove-strike")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> removeOneStrike(@PathVariable Long userId) {
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            userResetService.removeOneStrike(user.getEmail());
            return ResponseEntity.ok(Map.of("message", "One strike removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
