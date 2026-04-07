package com.medicaps.icms.service;

import com.medicaps.icms.entity.User;
import com.medicaps.icms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserResetService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Completely resets a user's strike status and ban information
     */
    @Transactional
    public void resetUserStrikeStatus(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Reset all strike and ban related fields
        user.setStrikeCount(0);
        user.setLastStrikeDate(null);
        user.setIsBanned(false);
        user.setBanReason(null);
        user.setComplaintBannedUntil(null);
        user.setEnabled(true);

        userRepository.save(user);
    }

    /**
     * Completely resets a user's strike status by ID
     */
    @Transactional
    public void resetUserStrikeStatusById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Reset all strike and ban related fields
        user.setStrikeCount(0);
        user.setLastStrikeDate(null);
        user.setIsBanned(false);
        user.setBanReason(null);
        user.setComplaintBannedUntil(null);
        user.setEnabled(true);

        userRepository.save(user);
    }

    /**
     * Removes one strike from a user
     */
    @Transactional
    public void removeOneStrike(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (user.getStrikeCount() != null && user.getStrikeCount() > 0) {
            user.setStrikeCount(user.getStrikeCount() - 1);
            
            // If user now has less than 3 strikes, clear any complaint ban
            if (user.getStrikeCount() < 3) {
                user.setComplaintBannedUntil(null);
                if (user.getBanReason() != null && user.getBanReason().contains("cannot submit new complaints")) {
                    user.setBanReason(null);
                }
            }
            
            userRepository.save(user);
        }
    }
}
