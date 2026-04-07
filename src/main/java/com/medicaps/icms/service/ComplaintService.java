package com.medicaps.icms.service;

import com.medicaps.icms.dto.ComplaintResponse;
import com.medicaps.icms.dto.RecentComplaintDTO;
import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.ComplaintStatus;
import com.medicaps.icms.entity.Priority;
import com.medicaps.icms.entity.User;
import com.medicaps.icms.entity.Worker;
import com.medicaps.icms.entity.Evidence;
import com.medicaps.icms.repository.ComplaintRepository;
import com.medicaps.icms.repository.WorkerRepository;
import com.medicaps.icms.service.StrikeService;
import com.medicaps.icms.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ComplaintService {

    @Autowired
    private ComplaintRepository complaintRepository;

    @Autowired
    private UserService userService;

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private StrikeService strikeService;

    @Autowired
    private EvidenceService evidenceService;

    @Autowired
    private ComplaintImageReviewService complaintImageReviewService;

    public Complaint createComplaint(Complaint complaint, String userEmail) {
        // Check if user can create complaint
        if (!strikeService.canUserCreateComplaint(userEmail)) {
            throw new RuntimeException(
                    "Cannot create complaint: your account is disabled, or you cannot file complaints until your strike ban ends.");
        }
        
        return complaintRepository.save(complaint);
    }

    public Optional<Complaint> getComplaint(Long id, User user) {
        Optional<Complaint> complaint = complaintRepository.findById(id);
        
        if (complaint.isPresent()) {
            Complaint c = complaint.get();
            // Check if user has permission to view this complaint
            if (hasPermissionToView(c, user)) {
                return complaint;
            }
        }
        
        return Optional.empty();
    }

    public Page<Complaint> getComplaintsForUser(User user, Pageable pageable, String status, String priority) {
        ComplaintStatus complaintStatus = null;
        Priority complaintPriority = null;

        if (status != null && !status.isEmpty()) {
            complaintStatus = ComplaintStatus.valueOf(status.toUpperCase());
        }
        if (priority != null && !priority.isEmpty()) {
            complaintPriority = Priority.valueOf(priority.toUpperCase());
        }

        switch (user.getRole()) {
            case USER:
                if (complaintStatus != null && complaintPriority != null) {
                    return complaintRepository.findByUserAndStatusAndPriority(user, complaintStatus, complaintPriority, pageable);
                } else if (complaintStatus != null) {
                    return complaintRepository.findByUserAndStatus(user, complaintStatus, pageable);
                } else if (complaintPriority != null) {
                    return complaintRepository.findByUserAndPriority(user, complaintPriority, pageable);
                }
                return complaintRepository.findByUser(user, pageable);
            case ADMIN:
            case OWNER:
                if (complaintStatus != null && complaintPriority != null) {
                    return complaintRepository.findByStatusAndPriority(complaintStatus, complaintPriority, pageable);
                } else if (complaintStatus != null) {
                    return complaintRepository.findByStatus(complaintStatus, pageable);
                } else if (complaintPriority != null) {
                    return complaintRepository.findByPriority(complaintPriority, pageable);
                }
                return complaintRepository.findAll(pageable);
            default:
                return Page.empty();
        }
    }

    public Complaint updateComplaint(Complaint complaint) {
        return complaintRepository.save(complaint);
    }

    @Transactional
    public ComplaintSubmissionResult submitComplaintWithEvidence(Complaint complaint, List<MultipartFile> files, String userEmail) {
        if (files == null || files.isEmpty()) {
            throw new RuntimeException("At least one evidence image is required.");
        }

        Complaint createdComplaint = createComplaint(complaint, userEmail);
        List<Evidence> savedEvidence = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                savedEvidence.add(evidenceService.saveEvidence(file, createdComplaint));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to save complaint evidence: " + e.getMessage(), e);
        }

        ComplaintImageReviewService.ReviewResult reviewResult = complaintImageReviewService.reviewComplaintImages(createdComplaint, files);

        if (!reviewResult.related()) {
            createdComplaint.setStatus(ComplaintStatus.REJECTED);
            createdComplaint.setIsFalseComplaint(true);
            createdComplaint.setFalseComplaintReason(reviewResult.reason());
            complaintRepository.save(createdComplaint);

            strikeService.addStrikeForFalseComplaint(createdComplaint.getUser().getEmail());
            User updatedUser = userService.findByEmail(createdComplaint.getUser().getEmail())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return new ComplaintSubmissionResult(
                    createdComplaint,
                    true,
                    reviewResult.reason(),
                    updatedUser.getStrikeCount() != null ? updatedUser.getStrikeCount() : 0,
                    updatedUser.getComplaintBannedUntil());
        }

        return new ComplaintSubmissionResult(
                createdComplaint,
                false,
                reviewResult.reason(),
                createdComplaint.getUser().getStrikeCount() != null ? createdComplaint.getUser().getStrikeCount() : 0,
                createdComplaint.getUser().getComplaintBannedUntil());
    }

    public Complaint updateComplaintStatus(Long id, ComplaintStatus status) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        complaint.setStatus(status);
        return complaintRepository.save(complaint);
    }

    public Complaint assignWorker(Long complaintId, Long workerId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        
        Worker worker = workerRepository.findById(workerId)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        
        complaint.setAssignedWorker(worker);
        complaint.setStatus(ComplaintStatus.IN_PROGRESS);
        return complaintRepository.save(complaint);
    }

    public Complaint getComplaintForAdmin(Long complaintId) {
        return complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
    }

    /**
     * Marks a complaint as false and adds a strike to the user
     */
    @Transactional
    public void markComplaintAsFalse(Long complaintId, String reason) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        if (Boolean.TRUE.equals(complaint.getIsFalseComplaint())) {
            return;
        }

        complaint.setIsFalseComplaint(true);
        complaint.setFalseComplaintReason(reason);
        complaintRepository.save(complaint);
        
        // Add strike to the user who created the false complaint
        strikeService.addStrikeForFalseComplaint(complaint.getUser().getEmail());
    }

    /**
     * Marks a complaint as valid (removes false flag) and removes a strike from the user
     */
    @Transactional
    public void markComplaintAsValid(Long complaintId) {
        Complaint complaint = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        if (!Boolean.TRUE.equals(complaint.getIsFalseComplaint())) {
            return;
        }

        complaint.setIsFalseComplaint(false);
        complaint.setFalseComplaintReason(null);
        complaintRepository.save(complaint);
        
        // Remove strike from the user who created the false complaint
        strikeService.removeStrike(complaint.getUser().getEmail());
    }

    /**
     * Gets complaints that need admin review (false complaints)
     */
    public List<Complaint> getFalseComplaints() {
        try {
            System.out.println("Attempting to fetch false complaints...");
            Page<Complaint> falseComplaintsPage = complaintRepository.findByIsFalseComplaintTrue(
                org.springframework.data.domain.PageRequest.of(0, 100));
            List<Complaint> falseComplaints = falseComplaintsPage.getContent();
            System.out.println("Found " + falseComplaints.size() + " false complaints");
            return falseComplaints;
        } catch (Exception e) {
            System.out.println("Error fetching false complaints: " + e.getMessage());
            throw new RuntimeException("Failed to fetch false complaints: " + e.getMessage());
        }
    }

    /**
     * Gets recent complaints for a user
     */
    public List<RecentComplaintDTO> getRecentComplaints(User user) {
        List<Complaint> recentComplaints = complaintRepository.findByUser(user, 
            org.springframework.data.domain.PageRequest.of(0, 5))
            .getContent();
        
        // Convert to DTOs to avoid serialization issues
        return recentComplaints.stream()
                .map(this::convertToRecentComplaintDTO)
                .toList();
    }

    /**
     * Convert Complaint entity to RecentComplaintDTO
     */
    private RecentComplaintDTO convertToRecentComplaintDTO(Complaint complaint) {
        RecentComplaintDTO dto = new RecentComplaintDTO();
        dto.setId(complaint.getId());
        dto.setTitle(complaint.getTitle());
        dto.setDescription(complaint.getDescription());
        dto.setBlockNumber(complaint.getBlockNumber());
        dto.setBuildingNumber(complaint.getBuildingNumber());
        dto.setRoomNumber(complaint.getRoomNumber());
        dto.setAssetCategory(complaint.getAssetCategory());
        dto.setStatus(complaint.getStatus());
        dto.setPriority(complaint.getPriority());
        dto.setCreatedAt(complaint.getCreatedAt());
        dto.setUpdatedAt(complaint.getUpdatedAt());
        dto.setResolvedAt(complaint.getResolvedAt());
        return dto;
    }

    public void deleteComplaint(Long id) {
        Complaint complaint = complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));
        
        complaintRepository.delete(complaint);
    }

    public List<Complaint> searchComplaints(String keyword, User user) {
        List<Complaint> allComplaints = complaintRepository.searchComplaints(keyword);
        
        return allComplaints.stream()
                .filter(complaint -> hasPermissionToView(complaint, user))
                .collect(Collectors.toList());
    }

    public Map<String, Object> getComplaintStats() {
        Map<String, Object> stats = Map.of(
            "totalComplaints", complaintRepository.count(),
            "pendingComplaints", complaintRepository.countByStatus(ComplaintStatus.PENDING),
            "inProgressComplaints", complaintRepository.countByStatus(ComplaintStatus.IN_PROGRESS),
            "resolvedComplaints", complaintRepository.countByStatus(ComplaintStatus.RESOLVED),
            "rejectedComplaints", complaintRepository.countByStatus(ComplaintStatus.REJECTED),
            "highPriorityComplaints", complaintRepository.countByPriority(Priority.HIGH),
            "urgentComplaints", complaintRepository.countByPriority(Priority.URGENT),
            "totalUsers", userService.getAllUsers().size(),
            "totalAdmins", userService.countUsersByRole(com.medicaps.icms.entity.Role.ADMIN)
        );
        
        return stats;
    }

    public List<Complaint> getComplaintsByUser(Long userId) {
        return complaintRepository.findByUserIdOrderByCreatedAtDesc(userId, Pageable.unpaged()).getContent();
    }

    private boolean hasPermissionToView(Complaint complaint, User user) {
        switch (user.getRole()) {
            case OWNER:
            case ADMIN:
                return true;
            case USER:
                return complaint.getUser().getId().equals(user.getId());
            default:
                return false;
        }
    }

    public record ComplaintSubmissionResult(
            Complaint complaint,
            boolean autoRejected,
            String reviewReason,
            int strikeCount,
            LocalDateTime complaintBannedUntil) {
    }
}
