package com.medicaps.icms.service;

import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.Evidence;
import com.medicaps.icms.repository.EvidenceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Service
public class EvidenceService {

    @Autowired
    private EvidenceRepository evidenceRepository;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public Evidence saveEvidence(MultipartFile file, Complaint complaint) throws IOException {
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        // Check file type (allow only images)
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        // Check file size (max 10MB)
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("File size must be less than 10MB");
        }

        // Create upload directory if it doesn't exist
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalFilename = file.getOriginalFilename();
        String fileExtension = originalFilename != null ? 
            originalFilename.substring(originalFilename.lastIndexOf(".")) : "";
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        // Save file
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath);

        // Create evidence record
        Evidence evidence = new Evidence();
        evidence.setFileName(originalFilename);
        evidence.setFilePath(filePath.toString());
        evidence.setFileSize(file.getSize());
        evidence.setContentType(contentType);
        evidence.setComplaint(complaint);

        return evidenceRepository.save(evidence);
    }

    public List<Evidence> getEvidencesByComplaint(Long complaintId) {
        return evidenceRepository.findByComplaintId(complaintId);
    }

    public Evidence getEvidenceById(Long id) {
        return evidenceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evidence not found"));
    }

    public void deleteEvidence(Long id) throws IOException {
        Evidence evidence = getEvidenceById(id);
        
        // Delete file from filesystem
        Path filePath = Paths.get(evidence.getFilePath());
        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
        
        // Delete record from database
        evidenceRepository.deleteById(id);
    }

    public void deleteEvidencesByComplaint(Long complaintId) throws IOException {
        List<Evidence> evidences = getEvidencesByComplaint(complaintId);
        
        for (Evidence evidence : evidences) {
            deleteEvidence(evidence.getId());
        }
    }

    public byte[] getEvidenceFile(String filename) throws IOException {
        Path filePath = Paths.get(uploadDir).resolve(filename);
        
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File not found");
        }
        
        return Files.readAllBytes(filePath);
    }
}
