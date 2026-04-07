package com.medicaps.icms.repository;

import com.medicaps.icms.entity.Evidence;
import com.medicaps.icms.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvidenceRepository extends JpaRepository<Evidence, Long> {
    
    List<Evidence> findByComplaint(Complaint complaint);
    
    List<Evidence> findByComplaintId(Long complaintId);
    
    void deleteByComplaintId(Long complaintId);
}
