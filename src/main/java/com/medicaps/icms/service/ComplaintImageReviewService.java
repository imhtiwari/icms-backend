package com.medicaps.icms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medicaps.icms.entity.Complaint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.net.ConnectException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ComplaintImageReviewService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    @Value("${complaint.image-review.enabled:false}")
    private boolean imageReviewEnabled;

    @Value("${complaint.image-review.fail-open:true}")
    private boolean failOpenOnReviewError;

    @Value("${gemini.api-key:}")
    private String geminiApiKey;

    @Value("${gemini.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String geminiBaseUrl;

    @Value("${gemini.image-review-model:gemini-2.5-flash}")
    private String geminiImageReviewModel;

    public ComplaintImageReviewService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ReviewResult reviewComplaintImages(Complaint complaint, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return ReviewResult.reject("At least one evidence image is required.");
        }

        if (!imageReviewEnabled || geminiApiKey == null || geminiApiKey.isBlank()) {
            return ReviewResult.approve("Automatic image review was skipped because it is not configured.");
        }

        try {
            String outputText = callImageReviewModel(complaint, files);
            return parseReviewResult(outputText);
        } catch (ConnectException e) {
            System.err.println("Gemini image review failed: " + e.getClass().getName());
            System.err.println("Gemini image review error message: " + e.getMessage());
            e.printStackTrace();
            if (failOpenOnReviewError) {
                return ReviewResult.approve("Automatic image review was skipped because Gemini could not be reached.");
            }
            throw new RuntimeException("Automatic image review failed: connection to Gemini could not be established.", e);
        } catch (Exception e) {
            System.err.println("Gemini image review failed: " + e.getClass().getName());
            System.err.println("Gemini image review error message: " + e.getMessage());
            e.printStackTrace();
            
            // Check for rate limiting (HTTP 429)
            if (e.getMessage() != null && e.getMessage().contains("429")) {
                System.out.println("Gemini API rate limit reached - allowing complaint submission");
                if (failOpenOnReviewError) {
                    return ReviewResult.approve("Automatic image review was skipped due to API rate limits. Complaint will be reviewed manually.");
                }
                // For rate limiting, we should allow the complaint through
                return ReviewResult.approve("Image review temporarily unavailable - complaint submitted successfully.");
            }
            
            if (failOpenOnReviewError) {
                return ReviewResult.approve("Automatic image review was skipped because Gemini is unavailable or rate-limited.");
            }
            throw new RuntimeException("Automatic image review failed: " + describeException(e), e);
        }
    }

    private String callImageReviewModel(Complaint complaint, List<MultipartFile> files) throws IOException, InterruptedException {
        List<Map<String, Object>> parts = new ArrayList<>();
        parts.add(Map.of("text", buildReviewPrompt(complaint)));

        for (MultipartFile file : files.stream().limit(3).toList()) {
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                continue;
            }

            String base64 = Base64.getEncoder().encodeToString(file.getBytes());
            Map<String, Object> inlineData = new LinkedHashMap<>();
            inlineData.put("mimeType", contentType);
            inlineData.put("data", base64);
            parts.add(Map.of("inlineData", inlineData));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", List.of(Map.of("parts", parts)));
        payload.put("generationConfig", Map.of(
                "temperature", 0.1,
                "maxOutputTokens", 120
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(geminiBaseUrl + "/models/" + geminiImageReviewModel + ":generateContent?key=" + geminiApiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        System.out.println("Sending Gemini image review request to model: " + geminiImageReviewModel);
        System.out.println("Gemini image review endpoint: " + geminiBaseUrl + "/models/" + geminiImageReviewModel + ":generateContent");
        System.out.println("Gemini image review image parts count: " + Math.max(0, parts.size() - 1));

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        System.out.println("Gemini image review response status: " + response.statusCode());
        System.out.println("Gemini image review response body: " + response.body());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("Gemini image review request failed with status " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray()) {
            for (JsonNode candidate : candidates) {
                JsonNode contentNode = candidate.path("content").path("parts");
                if (contentNode.isArray()) {
                    for (JsonNode part : contentNode) {
                        String text = part.path("text").asText("");
                        if (!text.isBlank()) {
                            return text;
                        }
                    }
                }
            }
        }

        throw new RuntimeException("Gemini image review returned no text output");
    }

    private String buildReviewPrompt(Complaint complaint) {
        return """
                You are validating whether complaint evidence images are relevant to a maintenance complaint.

                Complaint details:
                Title: %s
                Description: %s
                Category: %s
                Block: %s
                Room: %s

                Decide whether these images are relevant evidence for this complaint.

                Rules:
                - Return UNRELATED only when the images are clearly unrelated, misleading, random, or not evidence of the described issue.
                - Return RELATED if at least one image plausibly supports the complaint, even if quality is imperfect.
                - Be conservative about rejection only when the mismatch is clear.

                Return JSON only in this exact shape:
                {"decision":"RELATED|UNRELATED","reason":"short reason"}
                """.formatted(
                safe(complaint.getTitle()),
                safe(complaint.getDescription()),
                complaint.getAssetCategory() != null ? complaint.getAssetCategory().name() : "OTHER",
                safe(complaint.getBlockNumber()),
                safe(complaint.getRoomNumber())
        );
    }

    private ReviewResult parseReviewResult(String outputText) {
        try {
            int start = outputText.indexOf('{');
            int end = outputText.lastIndexOf('}');
            String json = (start >= 0 && end > start) ? outputText.substring(start, end + 1) : outputText;
            JsonNode parsed = objectMapper.readTree(json);
            String decision = parsed.path("decision").asText("RELATED").trim().toUpperCase();
            String reason = parsed.path("reason").asText("Image review completed.").trim();

            if ("UNRELATED".equals(decision)) {
                return ReviewResult.reject(reason.isBlank() ? "Submitted image is not related to the complaint." : reason);
            }
            return ReviewResult.approve(reason.isBlank() ? "Image appears related to the complaint." : reason);
        } catch (Exception e) {
            String normalized = outputText.toUpperCase();
            if (normalized.contains("UNRELATED")) {
                return ReviewResult.reject("Submitted image appears unrelated to the complaint.");
            }
            return ReviewResult.approve("Image appears related to the complaint.");
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "N/A" : value;
    }

    private String describeException(Exception e) {
        String message = e.getMessage();
        if (message != null && !message.isBlank()) {
            return message;
        }
        return e.getClass().getSimpleName();
    }

    public record ReviewResult(boolean related, String reason) {
        public static ReviewResult approve(String reason) {
            return new ReviewResult(true, reason);
        }

        public static ReviewResult reject(String reason) {
            return new ReviewResult(false, reason);
        }
    }
}
