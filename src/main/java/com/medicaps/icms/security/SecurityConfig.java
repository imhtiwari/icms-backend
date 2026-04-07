package com.medicaps.icms.security;

import com.medicaps.icms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/recaptcha/verify").permitAll()
                        .requestMatchers("/uploads/**").permitAll()
                        // Admin only endpoints
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/users/**").hasAnyRole("ADMIN", "OWNER")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "OWNER")
                        // Owner only endpoints
                        .requestMatchers("/api/owner/**").hasRole("OWNER")
                        // Worker only endpoints
                        .requestMatchers("/api/worker/**").hasRole("WORKER")
                        // Workers endpoints - accessible by all authenticated users
                        .requestMatchers(HttpMethod.GET, "/api/workers/active")
                        .hasAnyRole("USER", "ADMIN", "OWNER", "WORKER")
                        .requestMatchers(HttpMethod.GET, "/api/workers/search")
                        .hasAnyRole("USER", "ADMIN", "OWNER", "WORKER")
                        .requestMatchers("/api/workers/**").hasAnyRole("USER", "ADMIN", "OWNER", "WORKER")
                        // Complaints endpoints - accessible by all relevant roles
                        .requestMatchers("/api/complaints/**").hasAnyRole("USER", "ADMIN", "OWNER", "WORKER")
                        // All authenticated users
                        .requestMatchers("/api/user/**").hasAnyRole("USER", "ADMIN", "OWNER", "WORKER")
                        .requestMatchers(HttpMethod.GET, "/api/users/profile")
                        .hasAnyRole("USER", "ADMIN", "OWNER", "WORKER")
                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
