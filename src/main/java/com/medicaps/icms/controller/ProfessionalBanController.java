package com.medicaps.icms.controller;

import com.medicaps.icms.service.ProfessionalBanService;
import com.medicaps.icms.service.ProfessionalBanService.BanEvaluation;
import com.medicaps.icms.service.ProfessionalBanService.BanAction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Professional Ban Management Controller
 * Handles intelligent banning based on user activity patterns
 */
@RestController
@RequestMapping("/api/admin/professional-ban")
@CrossOrigin(origins = "http://localhost:3000")
public class ProfessionalBanController {

    @Autowired
    private ProfessionalBanService professionalBanService;

    /**
     * Evaluate user for potential ban based on activity
     */
    @GetMapping("/evaluate/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> evaluateUser(@PathVariable Long userId) {
        try {
            BanEvaluation evaluation = professionalBanService.evaluateUserForBan(userId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("userId", userId);
            response.put("falseComplaintCount", evaluation.getFalseComplaintCount());
            response.put("spamScore", evaluation.getSpamScore());
            response.put("recommendedAction", evaluation.getRecommendedAction().name());
            response.put("reason", evaluation.getReason());
            response.put("banDurationDays", evaluation.getBanDurationDays());
            response.put("evaluation", evaluation);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Execute ban action with professional tracking
     */
    @PostMapping("/execute/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> executeBan(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            String actionStr = (String) request.get("action");
            String reason = (String) request.get("reason");
            Integer durationDays = (Integer) request.get("durationDays");
            
            if (actionStr == null || actionStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Action is required"));
            }
            
            BanAction action = BanAction.valueOf(actionStr.toUpperCase());
            String adminEmail = authentication.getName();
            
            professionalBanService.executeBan(userId, action, reason, durationDays, adminEmail);
            
            return ResponseEntity.ok(Map.of(
                "message", "Ban action executed successfully",
                "action", action.name(),
                "userId", userId,
                "adminEmail", adminEmail
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Quick ban for false complaints
     */
    @PostMapping("/false-complaint/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> banForFalseComplaints(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            String reason = (String) request.getOrDefault("reason", "Multiple false complaints detected");
            Integer durationDays = (Integer) request.getOrDefault("durationDays", 30);
            
            professionalBanService.executeBan(userId, BanAction.BAN_FOR_FALSE_COMPLAINTS, 
                                             reason, durationDays, authentication.getName());
            
            return ResponseEntity.ok(Map.of("message", "User banned for false complaints"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Quick ban for spam activity
     */
    @PostMapping("/spam/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> banForSpam(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            String reason = (String) request.getOrDefault("reason", "Spam activity detected");
            Integer durationDays = (Integer) request.getOrDefault("durationDays", 7);
            
            professionalBanService.executeBan(userId, BanAction.BAN_FOR_SPAM, 
                                             reason, durationDays, authentication.getName());
            
            return ResponseEntity.ok(Map.of("message", "User banned for spam activity"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Manual ban with custom reason
     */
    @PostMapping("/manual/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> manualBan(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            String reason = (String) request.get("reason");
            Integer durationDays = (Integer) request.getOrDefault("durationDays", 30);
            
            if (reason == null || reason.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Reason is required for manual ban"));
            }
            
            professionalBanService.executeBan(userId, BanAction.MANUAL_BAN, 
                                             reason, durationDays, authentication.getName());
            
            return ResponseEntity.ok(Map.of("message", "User manually banned"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Unban user with good behavior consideration
     */
    @PostMapping("/unban/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> unbanUser(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        
        try {
            String reason = (String) request.getOrDefault("reason", "Good behavior or ban period expired");
            
            professionalBanService.executeBan(userId, BanAction.UNBAN, 
                                             reason, null, authentication.getName());
            
            return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get ban statistics and analytics
     */
    @GetMapping("/analytics")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getBanAnalytics() {
        try {
            // This would typically query a ban_analytics table
            Map<String, Object> analytics = new HashMap<>();
            analytics.put("totalBans", 0);
            analytics.put("activeBans", 0);
            analytics.put("falseComplaintBans", 0);
            analytics.put("spamBans", 0);
            analytics.put("manualBans", 0);
            analytics.put("averageBanDuration", 0);
            
            return ResponseEntity.ok(analytics);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
