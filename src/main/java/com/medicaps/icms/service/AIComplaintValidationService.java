package com.medicaps.icms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.ComplaintStatus;
import com.medicaps.icms.entity.User;
import com.medicaps.icms.repository.ComplaintRepository;
import com.medicaps.icms.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * AI-powered Complaint Validation Service
 * Uses AI to determine if complaints are valid, relevant, and actionable
 */
@Service
public class AIComplaintValidationService {

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StrikeService strikeService;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${ai.validation.enabled:true}")
    private boolean aiValidationEnabled;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${gemini.validation-model:gemini-2.5-flash}")
    private String geminiValidationModel;

    // Validation thresholds
    private static final double MIN_VALIDITY_SCORE = 0.6;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final int MIN_DESCRIPTION_LENGTH = 10;

    public AIComplaintValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /**
     * Validate complaint using AI
     */
    public CompletableFuture<ValidationResult> validateComplaintAsync(Complaint complaint) {
        return CompletableFuture.supplyAsync(() -> validateComplaint(complaint));
    }

    /**
     * Synchronous validation
     */
    public ValidationResult validateComplaint(Complaint complaint) {
        if (!aiValidationEnabled || geminiApiKey == null || geminiApiKey.isBlank()) {
            return ValidationResult.approve("AI validation disabled - complaint approved by default.");
        }

        try {
            // Pre-validation checks
            ValidationResult preValidation = performPreValidation(complaint);
            if (!preValidation.isValid()) {
                return preValidation;
            }

            // AI validation
            String aiResponse = callAIValidation(complaint);
            return parseAIValidationResult(aiResponse);

        } catch (Exception e) {
            System.err.println("AI validation failed: " + e.getMessage());
            e.printStackTrace();
            return ValidationResult.approve("AI validation temporarily unavailable - complaint approved by default.");
        }
    }

    /**
     * Perform basic pre-validation before AI
     */
    private ValidationResult performPreValidation(Complaint complaint) {
        // Check description length
        String description = complaint.getDescription();
        if (description == null || description.trim().isEmpty()) {
            return ValidationResult.reject("Complaint description is required.");
        }

        if (description.trim().length() < MIN_DESCRIPTION_LENGTH) {
            return ValidationResult.reject("Complaint description is too short. Please provide more details.");
        }

        if (description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            return ValidationResult.reject("Complaint description is too long. Please be more concise.");
        }

        // Check for spam patterns
        if (isLikelySpam(description)) {
            return ValidationResult.reject("Complaint appears to be spam or contains invalid content.");
        }

        // Check for inappropriate content
        if (containsInappropriateContent(description)) {
            return ValidationResult.reject("Complaint contains inappropriate or offensive content.");
        }

        return ValidationResult.valid();
    }

    /**
     * Call AI for complaint validation
     */
    private String callAIValidation(Complaint complaint) throws IOException, InterruptedException {
        String prompt = buildValidationPrompt(complaint);
        
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("contents", List.of(
            Map.of("parts", List.of(Map.of("text", prompt)))
        ));
        requestBody.put("generationConfig", Map.of(
            "temperature", 0.1,
            "maxOutputTokens", 500,
            "topP", 0.8,
            "topK", 10
        ));

        String jsonBody = objectMapper.writeValueAsString(requestBody);
        String url = String.format("%s/models/%s:generateContent?key=%s", 
                geminiBaseUrl, geminiValidationModel, geminiApiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            throw new RuntimeException("AI validation rate limit reached");
        }

        if (response.statusCode() != 200) {
            throw new RuntimeException("AI validation failed with status: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Build AI validation prompt
     */
    private String buildValidationPrompt(Complaint complaint) {
        return String.format("""
            You are an AI assistant validating infrastructure complaints for an educational institution. 
            Analyze this complaint and determine if it's valid, relevant, and actionable.

            Complaint Details:
            Title: %s
            Description: %s
            Category: %s
            Priority: %s
            Block: %s
            Building: %s
            Room: %s
            Submitted: %s

            Validation Criteria:
            1. CLARITY: Is the complaint clear and understandable?
            2. RELEVANCE: Is it related to infrastructure/facilities?
            3. ACTIONABILITY: Can maintenance staff address this issue?
            4. SPECIFICITY: Are location details sufficient?
            5. APPROPRIATENESS: Is the tone professional and respectful?

            Additional Checks:
            - Is this a duplicate of existing complaints?
            - Does this contain personal attacks or harassment?
            - Is this a legitimate maintenance request vs. general feedback?
            - Are there safety concerns that require immediate attention?

            Return JSON response in this exact format:
            {
              "isValid": true/false,
              "validityScore": 0.0-1.0,
              "category": "MAINTENANCE|SAFETY|CLEANING|EQUIPMENT|OTHER|INVALID",
              "urgency": "LOW|MEDIUM|HIGH|CRITICAL",
              "reason": "Detailed explanation of validation decision",
              "recommendations": ["List of specific actions or improvements needed"],
              "isDuplicate": false,
              "requiresImmediateAttention": false,
              "estimatedResolutionTime": "hours/days/weeks"
            }
            """,
            safe(complaint.getTitle()),
            safe(complaint.getDescription()),
            complaint.getAssetCategory() != null ? complaint.getAssetCategory().name() : "OTHER",
            complaint.getPriority() != null ? complaint.getPriority().name() : "MEDIUM",
            safe(complaint.getBlockNumber()),
            safe(complaint.getBuildingNumber()),
            safe(complaint.getRoomNumber()),
            complaint.getCreatedAt() != null ? complaint.getCreatedAt().toString() : "Unknown"
        );
    }

    /**
     * Parse AI validation result
     */
    private ValidationResult parseAIValidationResult(String aiResponse) {
        try {
            JsonNode root = objectMapper.readTree(aiResponse);
            JsonNode candidates = root.path("candidates");
            
            if (candidates.isEmpty()) {
                return ValidationResult.approve("AI validation returned no response - complaint approved by default.");
            }

            String content = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
            
            // Extract JSON from response
            int start = content.indexOf('{');
            int end = content.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return ValidationResult.approve("AI validation response format invalid - complaint approved by default.");
            }

            String jsonContent = content.substring(start, end + 1);
            JsonNode result = objectMapper.readTree(jsonContent);

            boolean isValid = result.path("isValid").asBoolean(true);
            double validityScore = result.path("validityScore").asDouble(1.0);
            String reason = result.path("reason").asText("AI validation completed.");
            String category = result.path("category").asText("OTHER");
            String urgency = result.path("urgency").asText("MEDIUM");
            boolean isDuplicate = result.path("isDuplicate").asBoolean(false);
            boolean requiresImmediateAttention = result.path("requiresImmediateAttention").asBoolean(false);
            String estimatedResolutionTime = result.path("estimatedResolutionTime").asText("days");

            // Parse recommendations
            List<String> recommendations = new ArrayList<>();
            JsonNode recsNode = result.path("recommendations");
            if (recsNode.isArray()) {
                for (JsonNode rec : recsNode) {
                    recommendations.add(rec.asText());
                }
            }

            // Final decision based on validity score
            if (validityScore < MIN_VALIDITY_SCORE) {
                return ValidationResult.reject(reason, validityScore, category, urgency, 
                    recommendations, isDuplicate, requiresImmediateAttention, estimatedResolutionTime);
            }

            if (!isValid) {
                return ValidationResult.reject(reason, validityScore, category, urgency,
                    recommendations, isDuplicate, requiresImmediateAttention, estimatedResolutionTime);
            }

            return ValidationResult.approve(reason, validityScore, category, urgency,
                recommendations, isDuplicate, requiresImmediateAttention, estimatedResolutionTime);

        } catch (Exception e) {
            System.err.println("Failed to parse AI validation result: " + e.getMessage());
            return ValidationResult.approve("AI validation response parsing failed - complaint approved by default.");
        }
    }

    /**
     * Check if content is likely spam
     */
    private boolean isLikelySpam(String content) {
        String normalized = content.toLowerCase().trim();
        
        // Check for repetitive characters
        if (Pattern.compile("(.)\\1{5,}").matcher(normalized).find()) {
            return true;
        }

        // Check for excessive capitalization
        long capitalCount = normalized.chars().filter(Character::isUpperCase).count();
        if (capitalCount > normalized.length() * 0.5) {
            return true;
        }

        // Check for common spam patterns
        String[] spamPatterns = {
            "test complaint", "just testing", "sample complaint", "asdf", "qwerty",
            "lorem ipsum", "dummy text", "test123", "hello world", "fake complaint"
        };

        for (String pattern : spamPatterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check for inappropriate content
     */
    private boolean containsInappropriateContent(String content) {
        String normalized = content.toLowerCase();
        
        String[] inappropriatePatterns = {
            "curse", "swear", "insult", "abuse", "threat", "violence",
            "illegal", "criminal", "harassment", "discrimination"
        };

        for (String pattern : inappropriatePatterns) {
            if (normalized.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Apply validation results to complaint
     */
    public void applyValidationResults(Complaint complaint, ValidationResult result) {
        if (result.isValid()) {
            // Update complaint status and metadata
            if (result.getUrgency() != null) {
                try {
                    complaint.setPriority(com.medicaps.icms.entity.Priority.valueOf(result.getUrgency()));
                } catch (IllegalArgumentException e) {
                    complaint.setPriority(com.medicaps.icms.entity.Priority.MEDIUM);
                }
            }

            // Set AI validation metadata
            complaint.setAiValidationScore(result.getValidityScore());
            complaint.setAiValidationReason(result.getReason());
            complaint.setAiValidatedAt(LocalDateTime.now());
            complaint.setAiValidationPassed(true);

        } else {
            // Reject complaint
            complaint.setStatus(ComplaintStatus.REJECTED);
            complaint.setRejectionReason(result.getReason());
            complaint.setAiValidationScore(result.getValidityScore());
            complaint.setAiValidationReason(result.getReason());
            complaint.setAiValidatedAt(LocalDateTime.now());
            complaint.setAiValidationPassed(false);

            // Apply strike if this appears to be spam/invalid
            if (result.getValidityScore() < 0.3) {
                User user = complaint.getUser();
                if (user != null) {
                    strikeService.addStrikes(user.getEmail(), 1);
                }
            }
        }

        complaintRepository.save(complaint);
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    // Result classes
    public static class ValidationResult {
        private boolean valid;
        private String reason;
        private double validityScore;
        private String category;
        private String urgency;
        private List<String> recommendations;
        private boolean isDuplicate;
        private boolean requiresImmediateAttention;
        private String estimatedResolutionTime;

        private ValidationResult(boolean valid, String reason) {
            this.valid = valid;
            this.reason = reason;
            this.validityScore = valid ? 1.0 : 0.0;
            this.recommendations = new ArrayList<>();
        }

        public static ValidationResult approve(String reason) {
            return new ValidationResult(true, reason);
        }

        public static ValidationResult approve(String reason, double score, String category, String urgency,
                                           List<String> recommendations, boolean isDuplicate, 
                                           boolean requiresImmediateAttention, String estimatedResolutionTime) {
            ValidationResult result = new ValidationResult(true, reason);
            result.validityScore = score;
            result.category = category;
            result.urgency = urgency;
            result.recommendations = recommendations;
            result.isDuplicate = isDuplicate;
            result.requiresImmediateAttention = requiresImmediateAttention;
            result.estimatedResolutionTime = estimatedResolutionTime;
            return result;
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason);
        }

        public static ValidationResult reject(String reason, double score, String category, String urgency,
                                           List<String> recommendations, boolean isDuplicate,
                                           boolean requiresImmediateAttention, String estimatedResolutionTime) {
            ValidationResult result = new ValidationResult(false, reason);
            result.validityScore = score;
            result.category = category;
            result.urgency = urgency;
            result.recommendations = recommendations;
            result.isDuplicate = isDuplicate;
            result.requiresImmediateAttention = requiresImmediateAttention;
            result.estimatedResolutionTime = estimatedResolutionTime;
            return result;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, "Pre-validation passed");
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getReason() { return reason; }
        public double getValidityScore() { return validityScore; }
        public String getCategory() { return category; }
        public String getUrgency() { return urgency; }
        public List<String> getRecommendations() { return recommendations; }
        public boolean isDuplicate() { return isDuplicate; }
        public boolean isRequiresImmediateAttention() { return requiresImmediateAttention; }
        public String getEstimatedResolutionTime() { return estimatedResolutionTime; }
    }
}
