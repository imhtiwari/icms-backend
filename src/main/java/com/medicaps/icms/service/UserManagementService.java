package com.medicaps.icms.service;

import com.medicaps.icms.dto.UserManagementDTO;
import com.medicaps.icms.entity.User;
import com.medicaps.icms.entity.Role;
import com.medicaps.icms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserManagementService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Get all users for admin management
     */
    public Page<UserManagementDTO> getAllUsersForManagement(Pageable pageable) {
        Page<User> usersPage = userRepository.findAll(pageable);
        return usersPage.map(UserManagementDTO::new);
    }

    /**
     * Ban a user with custom reason and duration
     */
    @Transactional
    public void banUser(Long userId, String reason, Integer banDays) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsBanned(true);
        user.setBanReason(reason);
        user.setComplaintBannedUntil(LocalDateTime.now().plusDays(banDays != null ? banDays : 30));
        user.setEnabled(false);

        userRepository.save(user);
    }

    /**
     * Unban a user
     */
    @Transactional
    public void unbanUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setIsBanned(false);
        user.setBanReason(null);
        user.setComplaintBannedUntil(null);
        user.setEnabled(true);

        userRepository.save(user);
    }

    /**
     * Add strikes to a user
     */
    @Transactional
    public void addStrikes(Long userId, int strikeCount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int currentStrikes = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        user.setStrikeCount(currentStrikes + strikeCount);
        user.setLastStrikeDate(LocalDateTime.now());

        // Auto-ban if 3+ strikes
        if (user.getStrikeCount() >= 3) {
            user.setIsBanned(true);
            user.setBanReason("Auto-banned for 30 days due to 3+ strikes");
            user.setComplaintBannedUntil(LocalDateTime.now().plusDays(30));
            user.setEnabled(false);
        }

        userRepository.save(user);
    }

    /**
     * Remove strikes from a user
     */
    @Transactional
    public void removeStrikes(Long userId, int strikeCount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        int currentStrikes = user.getStrikeCount() != null ? user.getStrikeCount() : 0;
        int newStrikeCount = Math.max(0, currentStrikes - strikeCount);
        user.setStrikeCount(newStrikeCount);

        // Unban if less than 3 strikes
        if (newStrikeCount < 3) {
            user.setIsBanned(false);
            user.setBanReason(null);
            user.setComplaintBannedUntil(null);
            user.setEnabled(true);
        }

        userRepository.save(user);
    }

    /**
     * Enable/disable user account
     */
    @Transactional
    public void toggleUserStatus(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setEnabled(enabled);
        if (!enabled) {
            user.setBanReason("Account disabled by administrator");
        } else {
            user.setBanReason(null);
        }

        userRepository.save(user);
    }

    /**
     * Change user role
     */
    @Transactional
    public void changeUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setRole(newRole);
        userRepository.save(user);
    }

    /**
     * Search users by keyword
     */
    public Page<UserManagementDTO> searchUsers(String keyword, Pageable pageable) {
        Page<User> usersPage = userRepository.searchUsers(keyword, pageable);
        return usersPage.map(UserManagementDTO::new);
    }
}
