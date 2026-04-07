package com.medicaps.icms.config;

import com.medicaps.icms.entity.Role;
import com.medicaps.icms.entity.User;
import com.medicaps.icms.repository.UserRepository;
import com.medicaps.icms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Override
    public void run(String... args) throws Exception {
        // Create an OWNER/ADMIN account if none exists
        if (!userRepository.existsByEmail("admin@medicaps.ac.in")) {
            User admin = new User();
            admin.setName("System Admin");
            admin.setEmail("admin@medicaps.ac.in");
            admin.setPassword("admin123");
            admin.setRole(Role.ADMIN);
            admin.setEnabled(true);
            admin.setEmailVerified(true);
            
            userService.registerUser(admin);
            System.out.println("Default admin user created: admin@medicaps.ac.in / admin123");
        }
        
        // Also create an OWNER if none exists (Owner has more privileges)
        if (!userRepository.existsByEmail("owner@medicaps.ac.in")) {
            User owner = new User();
            owner.setName("System Owner");
            owner.setEmail("owner@medicaps.ac.in");
            owner.setPassword("owner123");
            owner.setRole(Role.OWNER);
            owner.setEnabled(true);
            owner.setEmailVerified(true);
            
            userService.registerUser(owner);
            System.out.println("Default owner user created: owner@medicaps.ac.in / owner123");
        }
    }
}
