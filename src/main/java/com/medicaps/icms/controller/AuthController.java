package com.medicaps.icms.controller;

import com.medicaps.icms.entity.User;
import com.medicaps.icms.service.JwtService;
import com.medicaps.icms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:3000")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        try {
            // reCAPTCHA verification removed for testing
            // if (!recaptchaService.verifyRecaptcha(request.getRecaptchaToken())) {
            // return ResponseEntity.badRequest().body(Map.of("message", "reCAPTCHA
            // verification failed"));
            // }

            User user = new User();
            user.setName(request.getName());
            user.setEmail(request.getEmail());
            user.setPassword(request.getPassword());

            User registeredUser = userService.registerUser(user);

            return ResponseEntity.ok(Map.of(
                    "message", "Registration successful. Please check your email for verification.",
                    "userId", registeredUser.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            // reCAPTCHA verification removed for testing
            // if (!recaptchaService.verifyRecaptcha(request.getRecaptchaToken())) {
            // return ResponseEntity.badRequest().body(Map.of("message", "reCAPTCHA
            // verification failed"));
            // }

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            Optional<User> userOpt = userService.findByEmail(request.getEmail());

            if (userOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "User not found"));
            }

            User user = userOpt.get();

            if (!user.isEmailVerified()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message",
                        "Please verify your email before logging in. Check your inbox for the verification link."));
            }

            if (!user.isEnabled()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Account is disabled"));
            }

            String jwtToken = jwtService.generateToken(userDetails);

            Map<String, Object> response = new HashMap<>();
            response.put("token", jwtToken);
            response.put("user", Map.of(
                    "id", user.getId(),
                    "name", user.getName(),
                    "email", user.getEmail(),
                    "role", user.getRole().name()));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Invalid credentials"));
        }
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam String token) {
        boolean verified = userService.verifyEmail(token);

        if (verified) {
            // Return HTML redirect to frontend verification page
            String htmlRedirect = "<script>window.location.href='http://localhost:3000/verify-email?status=success';</script>";
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html")
                    .body(htmlRedirect);
        } else {
            // Return HTML redirect to frontend verification page with error
            String htmlRedirect = "<script>window.location.href='http://localhost:3000/verify-email?status=error';</script>";
            return ResponseEntity.ok()
                    .header("Content-Type", "text/html")
                    .body(htmlRedirect);
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        // TODO: Implement forgot password functionality
        return ResponseEntity.ok(Map.of("message", "Password reset email sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        // TODO: Implement reset password functionality
        return ResponseEntity.ok(Map.of("message", "Password reset successful"));
    }

    // DTO classes
    public static class RegisterRequest {
        private String name;
        private String email;
        private String password;
        private String recaptchaToken;

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

        public String getRecaptchaToken() {
            return recaptchaToken;
        }

        public void setRecaptchaToken(String recaptchaToken) {
            this.recaptchaToken = recaptchaToken;
        }
    }

    public static class LoginRequest {
        private String email;
        private String password;
        private String recaptchaToken;

        // Getters and Setters
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

        public String getRecaptchaToken() {
            return recaptchaToken;
        }

        public void setRecaptchaToken(String recaptchaToken) {
            this.recaptchaToken = recaptchaToken;
        }
    }

    public static class ForgotPasswordRequest {
        private String email;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static class ResetPasswordRequest {
        private String token;
        private String newPassword;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getNewPassword() {
            return newPassword;
        }

        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }
}
