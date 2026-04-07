package com.medicaps.icms.service;

import com.medicaps.icms.entity.User;
import com.medicaps.icms.entity.Role;
import com.medicaps.icms.repository.UserRepository;
import com.medicaps.icms.repository.ComplaintRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(!user.isEnabled())
                .build();
    }

    public User registerUser(User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        // Set default role as USER for new registrations
        if (user.getRole() == null) {
            user.setRole(Role.USER);
        }

        // Encode password
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // User must verify email before being enabled (if not already set)
        if (!user.isEmailVerified()) {
            // Generate verification token
            String verificationToken = UUID.randomUUID().toString();
            user.setVerificationToken(verificationToken);
            user.setTokenExpiry(LocalDateTime.now().plusHours(24));
            
            user.setEnabled(false);
            user.setEmailVerified(false);

            User savedUser = userRepository.save(user);

            // Send verification email
            emailService.sendVerificationEmail(savedUser.getEmail(), verificationToken);
            return savedUser;
        }

        return userRepository.save(user);
    }

    public boolean verifyEmail(String token) {
        Optional<User> userOpt = userRepository.findByVerificationToken(token);
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            
            if (user.getTokenExpiry().isBefore(LocalDateTime.now())) {
                return false; // Token expired
            }

            user.setEmailVerified(true);
            user.setEnabled(true);
            user.setVerificationToken(null);
            user.setTokenExpiry(null);
            
            userRepository.save(user);
            return true;
        }
        
        return false;
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> findEnabledUsersByRole(Role role) {
        return userRepository.findEnabledUsersByRole(role);
    }

    public User createAdmin(User user) {
        user.setRole(Role.ADMIN);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return registerUser(user);
    }

    public User createWorker(User user) {
        user.setRole(Role.WORKER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return registerUser(user);
    }

    public User createOwner(User user) {
        user.setRole(Role.OWNER);
        user.setEnabled(true);
        user.setEmailVerified(true);
        return registerUser(user);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, User userDetails) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setName(userDetails.getName());
        user.setEmail(userDetails.getEmail());
        if (userDetails.getPassword() != null && !userDetails.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(userDetails.getPassword()));
        }
        user.setRole(userDetails.getRole());
        user.setEnabled(userDetails.isEnabled());

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        // Delete the user (Hibernate handles cascaded complaints created BY user)
        userRepository.deleteById(id);
    }

    public long countUsersByRole(Role role) {
        return userRepository.countEnabledUsersByRole(role);
    }

    public boolean hasRole(String email, String roleName) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return user.getRole() != null && user.getRole().name().equals(roleName);
        }
        return false;
    }
}
