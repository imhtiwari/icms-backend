package com.medicaps.icms.controller;

import com.medicaps.icms.dto.ComplaintResponse;
import com.medicaps.icms.dto.RecentComplaintDTO;
import com.medicaps.icms.entity.*;
import com.medicaps.icms.repository.ComplaintRepository;
import com.medicaps.icms.service.AIComplaintValidationService;
import com.medicaps.icms.service.ComplaintService;
import com.medicaps.icms.service.StrikeService;
import com.medicaps.icms.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/complaints")
@CrossOrigin(origins = "http://localhost:3000", methods = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
        RequestMethod.PATCH, RequestMethod.DELETE, RequestMethod.OPTIONS })
public class ComplaintController {

    @Autowired
    private ComplaintService complaintService;

    @Autowired
    private StrikeService strikeService;

    @Autowired
    private AIComplaintValidationService aiValidationService;

    @Autowired
    private UserService userService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public ResponseEntity<?> createComplaint(@Valid @RequestBody CreateComplaintRequest request,
            Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("Received CreateComplaintRequest: title=" + request.getTitle() + ", category="
                    + request.getAssetCategory());

            Complaint complaint = new Complaint();
            complaint.setTitle(request.getTitle());
            complaint.setDescription(request.getDescription());
            complaint.setBlockNumber(request.getBlockNumber());
            complaint.setBuildingNumber(request.getBuildingNumber());
            complaint.setRoomNumber(request.getRoomNumber());
            complaint.setPriority(request.getPriority());
            complaint.setAssetCategory(request.getAssetCategory());
            complaint.setUser(user);

            Complaint createdComplaint = complaintService.createComplaint(complaint, user.getEmail());

            // Convert to DTO to avoid serialization issues
            ComplaintResponse response = new ComplaintResponse();
            response.setId(createdComplaint.getId());
            response.setTitle(createdComplaint.getTitle());
            response.setDescription(createdComplaint.getDescription());
            response.setBlockNumber(createdComplaint.getBlockNumber());
            response.setBuildingNumber(createdComplaint.getBuildingNumber());
            response.setRoomNumber(createdComplaint.getRoomNumber());
            response.setPriority(createdComplaint.getPriority());
            response.setAssetCategory(createdComplaint.getAssetCategory());
            response.setStatus(createdComplaint.getStatus());
            response.setCreatedAt(createdComplaint.getCreatedAt());
            response.setUpdatedAt(createdComplaint.getUpdatedAt());
            response.setResolvedAt(createdComplaint.getResolvedAt());
            response.setUserName(createdComplaint.getUser().getName());
            response.setUserEmail(createdComplaint.getUser().getEmail());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping(value = "/submit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public ResponseEntity<?> submitComplaintWithEvidence(
            @RequestParam("title") String title,
            @RequestParam("description") String description,
            @RequestParam(value = "blockNumber", required = false) String blockNumber,
            @RequestParam(value = "buildingNumber", required = false) String buildingNumber,
            @RequestParam(value = "roomNumber", required = false) String roomNumber,
            @RequestParam("priority") String priority,
            @RequestParam("assetCategory") String assetCategory,
            @RequestParam("files") MultipartFile[] files,
            Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (files == null || files.length == 0) {
                return ResponseEntity.badRequest().body(Map.of("message", "At least one evidence image is required"));
            }

            Complaint complaint = new Complaint();
            complaint.setTitle(title);
            complaint.setDescription(description);
            complaint.setBlockNumber(blockNumber);
            complaint.setBuildingNumber(buildingNumber);
            complaint.setRoomNumber(roomNumber);
            complaint.setPriority(Priority.valueOf(priority.toUpperCase()));
            complaint.setAssetCategory(AssetCategory.valueOf(assetCategory.toUpperCase()));
            complaint.setUser(user);

            ComplaintService.ComplaintSubmissionResult result = complaintService.submitComplaintWithEvidence(
                    complaint,
                    List.of(files),
                    user.getEmail());

            Complaint savedComplaint = result.complaint();
            
            // Perform AI validation
            AIComplaintValidationService.ValidationResult aiResult = aiValidationService.validateComplaint(savedComplaint);
            aiValidationService.applyValidationResults(savedComplaint, aiResult);
            
            // Refresh the complaint to get updated status
            savedComplaint = complaintRepository.findById(savedComplaint.getId()).orElse(savedComplaint);
            
            Map<String, Object> response = new HashMap<>();
            response.put("id", savedComplaint.getId());
            response.put("status", savedComplaint.getStatus().toString());
            response.put("autoRejected", result.autoRejected() || !aiResult.isValid());
            response.put("reviewReason", result.reviewReason());
            response.put("aiValidationReason", aiResult.getReason());
            response.put("aiValidationScore", aiResult.getValidityScore());
            response.put("aiValidationPassed", aiResult.isValid());
            response.put("strikeCount", result.strikeCount());
            response.put("complaintBannedUntil", result.complaintBannedUntil());
            response.put("message", !aiResult.isValid()
                    ? "Complaint was rejected by AI validation: " + aiResult.getReason()
                    : result.autoRejected()
                    ? "Complaint was automatically rejected because the submitted image did not match the complaint."
                    : "Complaint submitted successfully");

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getComplaints(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "desc") String sortDir,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "priority", required = false) String priority,
            Authentication authentication) {

        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

            Page<Complaint> complaints = complaintService.getComplaintsForUser(user, pageable, status, priority);

            // Convert to DTOs to avoid serialization issues
            List<ComplaintResponse> complaintResponses = complaints.getContent().stream()
                    .map(complaint -> {
                        ComplaintResponse dto = new ComplaintResponse();
                        dto.setId(complaint.getId());
                        dto.setTitle(complaint.getTitle());
                        dto.setDescription(complaint.getDescription());
                        dto.setBlockNumber(complaint.getBlockNumber());
                        dto.setBuildingNumber(complaint.getBuildingNumber());
                        dto.setRoomNumber(complaint.getRoomNumber());
                        dto.setPriority(complaint.getPriority());
                        dto.setAssetCategory(complaint.getAssetCategory());
                        dto.setStatus(complaint.getStatus());
                        dto.setCreatedAt(complaint.getCreatedAt());
                        dto.setUpdatedAt(complaint.getUpdatedAt());
                        dto.setResolvedAt(complaint.getResolvedAt());
                        dto.setUserName(complaint.getUser().getName());
                        dto.setUserEmail(complaint.getUser().getEmail());
                        if (complaint.getAssignedWorker() != null) {
                            dto.setWorkerId(complaint.getAssignedWorker().getId());
                            dto.setWorkerName(complaint.getAssignedWorker().getName());
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());

            // Create a simple response object
            Map<String, Object> response = Map.of(
                    "content", complaintResponses,
                    "page", complaints.getNumber(),
                    "size", complaints.getSize(),
                    "totalElements", complaints.getTotalElements(),
                    "totalPages", complaints.getTotalPages(),
                    "first", complaints.isFirst(),
                    "last", complaints.isLast());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getMyComplaints(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<ComplaintResponse> complaintResponses = complaintService.getComplaintsByUser(user.getId()).stream()
                    .map(complaint -> {
                        ComplaintResponse dto = new ComplaintResponse();
                        dto.setId(complaint.getId());
                        dto.setTitle(complaint.getTitle());
                        dto.setDescription(complaint.getDescription());
                        dto.setBlockNumber(complaint.getBlockNumber());
                        dto.setBuildingNumber(complaint.getBuildingNumber());
                        dto.setRoomNumber(complaint.getRoomNumber());
                        dto.setPriority(complaint.getPriority());
                        dto.setAssetCategory(complaint.getAssetCategory());
                        dto.setStatus(complaint.getStatus());
                        dto.setCreatedAt(complaint.getCreatedAt());
                        dto.setUpdatedAt(complaint.getUpdatedAt());
                        dto.setResolvedAt(complaint.getResolvedAt());
                        dto.setUserName(complaint.getUser().getName());
                        dto.setUserEmail(complaint.getUser().getEmail());
                        if (complaint.getAssignedWorker() != null) {
                            dto.setWorkerId(complaint.getAssignedWorker().getId());
                            dto.setWorkerName(complaint.getAssignedWorker().getName());
                        }
                        return dto;
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of("content", complaintResponses));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getComplaint(@PathVariable("id") Long id, Authentication authentication) {
        try {
            System.out.println("GET /complaints/" + id + " - User: " + authentication.getName());

            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("User found: " + user.getEmail());
            Optional<Complaint> complaint = complaintService.getComplaint(id, user);

            if (complaint.isPresent()) {
                System.out.println("Complaint found: " + complaint.get().getTitle());
                Complaint c = complaint.get();

                // Create a simple response using HashMap to avoid Map.of() limit
                Map<String, Object> simpleResponse = new HashMap<>();
                simpleResponse.put("id", c.getId());
                simpleResponse.put("title", c.getTitle());
                simpleResponse.put("description", c.getDescription());
                simpleResponse.put("status", c.getStatus().toString());
                simpleResponse.put("priority", c.getPriority().toString());
                simpleResponse.put("assetCategory", c.getAssetCategory().toString());
                simpleResponse.put("blockNumber", c.getBlockNumber());
                simpleResponse.put("buildingNumber", c.getBuildingNumber());
                simpleResponse.put("roomNumber", c.getRoomNumber());
                simpleResponse.put("createdAt", c.getCreatedAt());
                simpleResponse.put("userName", c.getUser().getName());
                simpleResponse.put("userEmail", c.getUser().getEmail());
                simpleResponse.put("isFalseComplaint", Boolean.TRUE.equals(c.getIsFalseComplaint()));
                simpleResponse.put("falseComplaintReason", c.getFalseComplaintReason());
                if (c.getAssignedWorker() != null) {
                    simpleResponse.put("workerId", c.getAssignedWorker().getId());
                    simpleResponse.put("workerName", c.getAssignedWorker().getName());
                }

                System.out.println("Returning simple response");
                return ResponseEntity.ok(simpleResponse);
            } else {
                System.out.println("Complaint not found for ID: " + id);
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            System.out.println("Error in getComplaint: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER')")
    public ResponseEntity<?> updateComplaint(@PathVariable("id") Long id,
            @Valid @RequestBody UpdateComplaintRequest request,
            Authentication authentication) {
        try {
            System.out.println("PUT /complaints/" + id + " - User: " + authentication.getName());

            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            System.out.println("User found: " + user.getEmail() + " with role: " + user.getRole());

            Optional<Complaint> complaintOpt = complaintService.getComplaint(id, user);
            if (complaintOpt.isEmpty()) {
                System.out.println("Complaint not found for ID: " + id + " or user doesn't have permission");
                return ResponseEntity.notFound().build();
            }

            Complaint complaint = complaintOpt.get();
            System.out.println("Permission granted to edit complaint: " + complaint.getTitle());

            // Update fields
            complaint.setTitle(request.getTitle());
            complaint.setDescription(request.getDescription());
            complaint.setBlockNumber(request.getBlockNumber());
            complaint.setBuildingNumber(request.getBuildingNumber());
            complaint.setRoomNumber(request.getRoomNumber());
            complaint.setPriority(request.getPriority() != null ? request.getPriority() : complaint.getPriority());
            complaint.setAssetCategory(
                    request.getAssetCategory() != null ? request.getAssetCategory() : complaint.getAssetCategory());

            Complaint updatedComplaint = complaintService.updateComplaint(complaint);

            System.out.println("Complaint updated successfully: " + updatedComplaint.getTitle());

            // Return simple response to avoid serialization issues
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedComplaint.getId());
            response.put("title", updatedComplaint.getTitle());
            response.put("description", updatedComplaint.getDescription());
            response.put("status", updatedComplaint.getStatus().toString());
            response.put("priority", updatedComplaint.getPriority().toString());
            response.put("assetCategory", updatedComplaint.getAssetCategory().toString());
            response.put("blockNumber", updatedComplaint.getBlockNumber());
            response.put("buildingNumber", updatedComplaint.getBuildingNumber());
            response.put("roomNumber", updatedComplaint.getRoomNumber());
            response.put("createdAt", updatedComplaint.getCreatedAt());
            response.put("updatedAt", updatedComplaint.getUpdatedAt());
            response.put("userName", updatedComplaint.getUser().getName());
            response.put("userEmail", updatedComplaint.getUser().getEmail());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("Error updating complaint: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> updateComplaintStatus(@PathVariable("id") Long id,
            @RequestBody Map<String, String> request) {
        try {
            String statusStr = request.get("status");
            ComplaintStatus status = ComplaintStatus.valueOf(statusStr.toUpperCase());

            Complaint updatedComplaint = complaintService.updateComplaintStatus(id, status);

            // Return safe response
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedComplaint.getId());
            response.put("status", updatedComplaint.getStatus().toString());
            response.put("updatedAt", updatedComplaint.getUpdatedAt());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> assignWorker(@PathVariable("id") Long id, @RequestBody Map<String, Object> request) {
        try {
            System.out.println("PATCH /api/complaints/" + id + "/assign - Request: " + request);
            Object workerIdObj = request.get("workerId");
            Long workerId = null;
            if (workerIdObj instanceof Number) {
                workerId = ((Number) workerIdObj).longValue();
            } else if (workerIdObj instanceof String) {
                workerId = Long.parseLong((String) workerIdObj);
            }

            System.out.println("Parsed workerId: " + workerId);

            if (workerId == null) {
                System.out.println("Error: workerId is null");
                return ResponseEntity.badRequest().body(Map.of("message", "workerId is required"));
            }

            Complaint updatedComplaint = complaintService.assignWorker(id, workerId);

            System.out.println("Worker assigned successfully for complaint ID: " + id);

            // Return safe response
            Map<String, Object> response = new HashMap<>();
            response.put("id", updatedComplaint.getId());
            response.put("status", updatedComplaint.getStatus().toString());
            response.put("workerId", updatedComplaint.getAssignedWorker().getId());
            response.put("workerName", updatedComplaint.getAssignedWorker().getName());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            System.out.println("Error in assignWorker: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> deleteComplaint(@PathVariable("id") Long id) {
        try {
            complaintService.deleteComplaint(id);
            return ResponseEntity.ok(Map.of("message", "Complaint deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> searchComplaints(@RequestParam("keyword") String keyword, Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            List<Complaint> complaints = complaintService.searchComplaints(keyword, user);
            return ResponseEntity.ok(complaints);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/mark-false")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> markComplaintFalse(@PathVariable Long id, @RequestBody MarkFalseComplaintRequest request) {
        try {
            String trimmedReason = request != null && request.getReason() != null ? request.getReason().trim() : "";
            if (trimmedReason.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("message", "A reason is required when marking a false complaint"));
            }
            complaintService.markComplaintAsFalse(id, trimmedReason);
            Complaint complaint = complaintService.getComplaintForAdmin(id);
            User reportedUser = complaint.getUser();
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Complaint marked as false; a strike was applied to the reporting user.");
            response.put("strikeCount", reportedUser.getStrikeCount() != null ? reportedUser.getStrikeCount() : 0);
            response.put("complaintBannedUntil", reportedUser.getComplaintBannedUntil());
            response.put("banReason", reportedUser.getBanReason());
            response.put("reportedUserEmail", reportedUser.getEmail());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/{id}/mark-valid")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> markComplaintValid(@PathVariable Long id) {
        try {
            complaintService.markComplaintAsValid(id);
            return ResponseEntity.ok(Map.of("message", "False-complaint flag cleared; strike removed if it applied."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getComplaintStats() {
        try {
            Map<String, Object> stats = complaintService.getComplaintStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/recent")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN', 'OWNER', 'WORKER')")
    public ResponseEntity<?> getRecentComplaints(Authentication authentication) {
        try {
            User user = userService.findByEmail(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            List<RecentComplaintDTO> recentComplaints = complaintService.getRecentComplaints(user);
            return ResponseEntity.ok(recentComplaints);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyAuthority('ROLE_USER', 'ROLE_ADMIN', 'ROLE_OWNER')")
    public ResponseEntity<?> uploadFiles(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam("complaintId") Long complaintId,
            Authentication authentication) {
        try {
            // File upload logic can be implemented later
            return ResponseEntity.ok(Map.of("message", "Files uploaded successfully", "count", files.length));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    // DTO classes
    public static class CreateComplaintRequest {
        private String title;
        private String description;
        private String blockNumber;
        private String buildingNumber;
        private String roomNumber;
        private Priority priority;
        private AssetCategory assetCategory;

        // Getters and Setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBlockNumber() {
            return blockNumber;
        }

        public void setBlockNumber(String blockNumber) {
            this.blockNumber = blockNumber;
        }

        public String getBuildingNumber() {
            return buildingNumber;
        }

        public void setBuildingNumber(String buildingNumber) {
            this.buildingNumber = buildingNumber;
        }

        public String getRoomNumber() {
            return roomNumber;
        }

        public void setRoomNumber(String roomNumber) {
            this.roomNumber = roomNumber;
        }

        public Priority getPriority() {
            return priority;
        }

        public void setPriority(Priority priority) {
            this.priority = priority;
        }

        public AssetCategory getAssetCategory() {
            return assetCategory;
        }

        public void setAssetCategory(AssetCategory assetCategory) {
            this.assetCategory = assetCategory;
        }
    }

    public static class MarkFalseComplaintRequest {
        private String reason;

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }

    public static class UpdateComplaintRequest {
        private String title;
        private String description;
        private String blockNumber;
        private String buildingNumber;
        private String roomNumber;
        private Priority priority;
        private AssetCategory assetCategory;

        // Getters and Setters
        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getBlockNumber() {
            return blockNumber;
        }

        public void setBlockNumber(String blockNumber) {
            this.blockNumber = blockNumber;
        }

        public String getBuildingNumber() {
            return buildingNumber;
        }

        public void setBuildingNumber(String buildingNumber) {
            this.buildingNumber = buildingNumber;
        }

        public String getRoomNumber() {
            return roomNumber;
        }

        public void setRoomNumber(String roomNumber) {
            this.roomNumber = roomNumber;
        }

        public Priority getPriority() {
            return priority;
        }

        public void setPriority(Priority priority) {
            this.priority = priority;
        }

        public AssetCategory getAssetCategory() {
            return assetCategory;
        }

        public void setAssetCategory(AssetCategory assetCategory) {
            this.assetCategory = assetCategory;
        }
    }
}
