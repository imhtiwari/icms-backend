package com.medicaps.icms.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/test-auth")
public class TestAuthController {

    @GetMapping
    public String getAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "No authentication found";
        }
        return "Name: " + auth.getName() + " | Authorities: " + 
               auth.getAuthorities().stream().map(Object::toString).collect(Collectors.joining(","));
    }
}
