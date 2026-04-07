package com.medicaps.icms.service;

import com.medicaps.icms.entity.User;
import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.ComplaintStatus;
import com.medicaps.icms.repository.UserRepository;
import com.medicaps.icms.repository.ComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Professional Ban Management Service
 * Handles banning/unbanning based on user activity patterns
 */
@Service
public class ProfessionalBanService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    // Ban thresholds
    private static final int FALSE_COMPLAINT_THRESHOLD = 3;
    private static final int SPAM_THRESHOLD = 5; // 5 complaints in 1 hour
    private static final int INACTIVE_DAYS_THRESHOLD = 30; // Auto-unban after 30 days inactivity

    /**
     * Evaluate user for potential ban based on activity
     */
    @Transactional
    public BanEvaluation evaluateUserForBan(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        BanEvaluation evaluation = new BanEvaluation();

        // Check false complaints
        evaluation.setFalseComplaintCount(countFalseComplaints(user));
        if (evaluation.getFalseComplaintCount() >= FALSE_COMPLAINT_THRESHOLD) {
            evaluation.setRecommendedAction(BanAction.BAN_FOR_FALSE_COMPLAINTS);
            evaluation.setReason("Auto-banned for " + FALSE_COMPLAINT_THRESHOLD + "+ false complaints");
            evaluation.setBanDurationDays(30);
            return evaluation;
        }

        // Check spam activity
        evaluation.setSpamScore(calculateSpamScore(user));
        if (evaluation.getSpamScore() >= 100) {
            evaluation.setRecommendedAction(BanAction.BAN_FOR_SPAM);
            evaluation.setReason("Auto-banned for spam activity");
            evaluation.setBanDurationDays(7);
            return evaluation;
        }

        // Check if user should be unbanned (good behavior)
        if (shouldConsiderUnban(user)) {
            evaluation.setRecommendedAction(BanAction.CONSIDER_UNBAN);
            evaluation.setReason("User shows improved behavior");
            return evaluation;
        }

        evaluation.setRecommendedAction(BanAction.NO_ACTION);
        return evaluation;
    }

    /**
     * Execute ban with professional tracking
     */
    @Transactional
    public void executeBan(Long userId, BanAction action, String reason, Integer durationDays, String adminEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime banUntil = LocalDateTime.now().plusDays(durationDays != null ? durationDays : 30);

        switch (action) {
            case BAN_FOR_FALSE_COMPLAINTS:
                user.setIsBanned(true);
                user.setBanReason("Banned for false complaints: " + reason);
                user.setComplaintBannedUntil(banUntil);
                user.setEnabled(false);
                break;

            case BAN_FOR_SPAM:
                user.setIsBanned(true);
                user.setBanReason("Banned for spam: " + reason);
                user.setComplaintBannedUntil(banUntil);
                user.setEnabled(false);
                break;

            case MANUAL_BAN:
                user.setIsBanned(true);
                user.setBanReason("Manual ban by admin: " + reason);
                user.setComplaintBannedUntil(banUntil);
                user.setEnabled(false);
                break;

            case UNBAN:
                user.setIsBanned(false);
                user.setBanReason(null);
                user.setComplaintBannedUntil(null);
                user.setEnabled(true);
                // Reduce strikes for good behavior
                if (user.getStrikeCount() != null && user.getStrikeCount() > 0) {
                    user.setStrikeCount(user.getStrikeCount() - 1);
                }
                break;

            default:
                throw new IllegalArgumentException("Invalid ban action: " + action);
        }

        user.setLastStrikeDate(LocalDateTime.now());
        userRepository.save(user);

        // Log the action for audit trail
        logBanAction(userId, action, reason, adminEmail);
    }

    /**
     * Check if user should be considered for unban
     */
    private boolean shouldConsiderUnban(User user) {
        if (!Boolean.TRUE.equals(user.getIsBanned())) {
            return false;
        }

        // Check if ban period has expired
        if (user.getComplaintBannedUntil() != null && 
            user.getComplaintBannedUntil().isBefore(LocalDateTime.now())) {
            return true;
        }

        // Check if user has been inactive for a long time (using updatedAt as fallback)
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(INACTIVE_DAYS_THRESHOLD);
        if (user.getUpdatedAt() != null && user.getUpdatedAt().isBefore(thirtyDaysAgo)) {
            return true;
        }

        return false;
    }

    /**
     * Count false complaints for user
     */
    private int countFalseComplaints(User user) {
        return complaintRepository.findByUserAndIsFalseComplaintTrue(user).size();
    }

    /**
     * Calculate spam score based on complaint patterns
     */
    private int calculateSpamScore(User user) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<Complaint> recentComplaints = complaintRepository.findByUserAndCreatedAtAfter(user, oneHourAgo);
        
        int spamScore = 0;
        
        // High frequency complaints
        if (recentComplaints.size() >= SPAM_THRESHOLD) {
            spamScore += 50;
        }

        // Similar content complaints
        spamScore += calculateSimilarityScore(recentComplaints);

        // Short description complaints (potential spam)
        long shortDescCount = recentComplaints.stream()
                .filter(c -> c.getDescription().length() < 20)
                .count();
        if (shortDescCount >= 3) {
            spamScore += 30;
        }

        return spamScore;
    }

    /**
     * Calculate content similarity score
     */
    private int calculateSimilarityScore(List<Complaint> complaints) {
        // Simple implementation - can be enhanced with actual text similarity algorithms
        if (complaints.size() < 2) return 0;
        
        String firstDesc = complaints.get(0).getDescription();
        long similarCount = complaints.stream()
                .filter(c -> c.getDescription().equalsIgnoreCase(firstDesc))
                .count();
        
        return similarCount >= 2 ? 20 : 0;
    }

    /**
     * Log ban actions for audit trail
     */
    private void logBanAction(Long userId, BanAction action, String reason, String adminEmail) {
        // This would typically save to a ban_audit_log table
        System.out.println("BAN AUDIT: User " + userId + " - Action: " + action + 
                          " - Reason: " + reason + " - Admin: " + adminEmail +
                          " - Timestamp: " + LocalDateTime.now());
    }

    // Enums and DTOs
    public enum BanAction {
        BAN_FOR_FALSE_COMPLAINTS,
        BAN_FOR_SPAM,
        MANUAL_BAN,
        UNBAN,
        NO_ACTION,
        CONSIDER_UNBAN
    }

    public static class BanEvaluation {
        private int falseComplaintCount;
        private int spamScore;
        private BanAction recommendedAction;
        private String reason;
        private Integer banDurationDays;

        // Getters and setters
        public int getFalseComplaintCount() { return falseComplaintCount; }
        public void setFalseComplaintCount(int falseComplaintCount) { this.falseComplaintCount = falseComplaintCount; }

        public int getSpamScore() { return spamScore; }
        public void setSpamScore(int spamScore) { this.spamScore = spamScore; }

        public BanAction getRecommendedAction() { return recommendedAction; }
        public void setRecommendedAction(BanAction recommendedAction) { this.recommendedAction = recommendedAction; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Integer getBanDurationDays() { return banDurationDays; }
        public void setBanDurationDays(Integer banDurationDays) { this.banDurationDays = banDurationDays; }
    }
}
