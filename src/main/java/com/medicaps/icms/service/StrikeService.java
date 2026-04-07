package com.medicaps.icms.service;

import com.medicaps.icms.entity.User;
import com.medicaps.icms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class StrikeService {

    public static final int COMPLAINT_BAN_DAYS = 30;
    public static final int STRIKES_FOR_COMPLAINT_BAN = 3;

    @Autowired
    private UserRepository userRepository;

 /**
     * Adds a strike to a user for creating a false complaint.
     * At 3 strikes, the user cannot file new complaints for {@value #COMPLAINT_BAN_DAYS} days (login remains active).
     */
    @Transactional
    public void addStrikeForFalseComplaint(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        clearComplaintBanIfExpired(user);

        int currentStrikeCount = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        user.setStrikeCount(currentStrikeCount + 1);
        user.setLastStrikeDate(LocalDateTime.now());

        if ((user.getStrikeCount() != null ? user.getStrikeCount() : 0) >= STRIKES_FOR_COMPLAINT_BAN) {
            user.setComplaintBannedUntil(LocalDateTime.now().plusDays(COMPLAINT_BAN_DAYS));
            user.setBanReason("You cannot submit new complaints for " + COMPLAINT_BAN_DAYS
                    + " days due to repeated false complaints.");
        }

        userRepository.save(user);
    }

    /**
     * Manual strike(s) from admin (same progression as false-complaint strikes).
     */
    @Transactional
    public void addStrikes(String userEmail, int count) {
        if (count < 1) {
            return;
        }
        for (int i = 0; i < count; i++) {
            addStrikeForFalseComplaint(userEmail);
        }
    }

    /**
     * True if the user may create a new complaint (ignores other roles/capabilities).
     */
    @Transactional
    public boolean canUserCreateComplaint(String userEmail) {
        User user = userRepository.findByEmail(userEmail).orElse(null);

        if (user == null) {
            return true;
        }

        if (!user.isEnabled()) {
            return false;
        }

        if (Boolean.TRUE.equals(user.getIsBanned())) {
            return false;
        }

        // Check strike count - allow complaints if less than 3 strikes
        int strikeCount = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        if (strikeCount >= 3) {
            return false;
        }

        // Check complaint ban period
        LocalDateTime until = user.getComplaintBannedUntil();
        if (until != null) {
            if (!until.isAfter(LocalDateTime.now())) {
                user.setComplaintBannedUntil(null);
                if (user.getBanReason() != null && user.getBanReason().contains("cannot submit new complaints")) {
                    user.setBanReason(null);
                }
                userRepository.save(user);
                return true;
            }
            return false;
        }

        return true;
    }

    private void clearComplaintBanIfExpired(User user) {
        if (user.getComplaintBannedUntil() != null
                && !user.getComplaintBannedUntil().isAfter(LocalDateTime.now())) {
            user.setComplaintBannedUntil(null);
            if (user.getBanReason() != null && user.getBanReason().contains("cannot submit new complaints")) {
                user.setBanReason(null);
            }
        }
    }

    @Transactional
    public void removeStrike(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        clearComplaintBanIfExpired(user);

        int currentStrikeCount = user.getStrikeCount() != null ? user.getStrikeCount() : 0;

        if (currentStrikeCount > 0) {
            user.setStrikeCount(currentStrikeCount - 1);
        } else {
            user.setStrikeCount(0);
        }
        if ((user.getStrikeCount() != null ? user.getStrikeCount() : 0) < STRIKES_FOR_COMPLAINT_BAN) {
            user.setComplaintBannedUntil(null);
            if (user.getBanReason() != null && user.getBanReason().contains("cannot submit new complaints")) {
                user.setBanReason(null);
            }
        }
        userRepository.save(user);
    }

    /**
     * Full unban (admin): clears strikes, ban flags, and complaint filing ban.
     */
    @Transactional
    public void unbanUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsBanned(false);
        user.setBanReason(null);
        user.setStrikeCount(0);
        user.setLastStrikeDate(null);
        user.setComplaintBannedUntil(null);
        user.setEnabled(true);
        userRepository.save(user);
    }

    /**
     * Manual ban: disable account (admin tool).
     */
    @Transactional
    public User banUserAccount(String userEmail, String reason) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsBanned(true);
        user.setBanReason(reason);
        user.setEnabled(false);
        return userRepository.save(user);
    }
}
