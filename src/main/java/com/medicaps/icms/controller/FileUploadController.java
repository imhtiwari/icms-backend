package com.medicaps.icms.controller;

import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.Evidence;
import com.medicaps.icms.service.ComplaintService;
import com.medicaps.icms.service.EvidenceService;
import com.medicaps.icms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:3000")
public class FileUploadController {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private UserService userService;

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER')")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file,
            @RequestParam("complaintId") Long complaintId,
            Authentication authentication) {
        try {
            // Validate file
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("message", "Please select a file to upload"));
            }

            // Check file type (allow only images)
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return ResponseEntity.badRequest().body(Map.of("message", "Only image files are allowed"));
            }

            // Check file size (max 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body(Map.of("message", "File size must be less than 10MB"));
            }

            // Create upload directory if it doesn't exist
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String fileExtension = originalFilename != null
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

            // Save file
            Path filePath = uploadPath.resolve(uniqueFilename);
            Files.copy(file.getInputStream(), filePath);

            // Get complaint and verify ownership
            Complaint complaint = complaintService.getComplaint(complaintId,
                    userService.findByEmail(authentication.getName()).orElseThrow())
                    .orElseThrow(() -> new RuntimeException("Complaint not found or access denied"));

            // Save evidence using EvidenceService
            Evidence evidence = evidenceService.saveEvidence(file, complaint);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "File uploaded successfully");
            response.put("evidenceId", evidence.getId());
            response.put("filename", evidence.getFileName());
            response.put("size", evidence.getFileSize());
            response.put("contentType", evidence.getContentType());

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Failed to upload file: " + e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/download/{filename}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        try {
            byte[] fileContent = evidenceService.getEvidenceFile(filename);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileContent.length)
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .build();
        }
    }

    @GetMapping("/complaint/{complaintId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getEvidencesByComplaint(@PathVariable Long complaintId,
                                                   Authentication authentication) {
        try {
            // For admins, allow access to all complaint files
            // For other roles, verify ownership
            if (!userService.hasRole(authentication.getName(), "ADMIN")) {
                Complaint complaint = complaintService.getComplaint(complaintId,
                        userService.findByEmail(authentication.getName()).orElseThrow(() -> new RuntimeException("Complaint not found or access denied"))
                ).orElseThrow(() -> new RuntimeException("Complaint not found or access denied"));
            }
            
            List<Evidence> evidences = evidenceService.getEvidencesByComplaint(complaintId);

            List<Map<String, Object>> response = evidences.stream().map(e -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", e.getId());
                map.put("fileName", e.getFileName());
                map.put("fileSize", e.getFileSize());
                map.put("contentType", e.getContentType());
                map.put("uploadedAt", e.getUploadedAt());
                // Extract just the filename from the full path for the view URL
                String storedFilename = Paths.get(e.getFilePath()).getFileName().toString();
                map.put("viewUrl", "/api/files/view/" + storedFilename);
                map.put("downloadUrl", "/api/files/download/" + storedFilename);
                return map;
            }).collect(Collectors.toList());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/view/{filename}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<Resource> viewFile(@PathVariable String filename) {
        try {
            byte[] fileContent = evidenceService.getEvidenceFile(filename);
            ByteArrayResource resource = new ByteArrayResource(fileContent);

            // Determine content type from extension
            String contentType = "image/jpeg";
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) contentType = "image/png";
            else if (lower.endsWith(".gif")) contentType = "image/gif";
            else if (lower.endsWith(".webp")) contentType = "image/webp";
            else if (lower.endsWith(".bmp")) contentType = "image/bmp";
            else if (lower.endsWith(".svg")) contentType = "image/svg+xml";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileContent.length)
                    .body(resource);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{evidenceId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER')")
    public ResponseEntity<?> deleteEvidence(@PathVariable Long evidenceId,
            Authentication authentication) {
        try {
            evidenceService.deleteEvidence(evidenceId);
            return ResponseEntity.ok(Map.of("message", "Evidence deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
