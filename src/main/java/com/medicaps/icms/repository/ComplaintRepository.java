package com.medicaps.icms.repository;

import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.ComplaintStatus;
import com.medicaps.icms.entity.Priority;
import com.medicaps.icms.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {
    
    Page<Complaint> findByUser(User user, Pageable pageable);
    
    Page<Complaint> findByStatus(ComplaintStatus status, Pageable pageable);
    
    Page<Complaint> findByPriority(Priority priority, Pageable pageable);

    Page<Complaint> findByStatusAndPriority(ComplaintStatus status, Priority priority, Pageable pageable);

    Page<Complaint> findByUserAndStatus(User user, ComplaintStatus status, Pageable pageable);

    Page<Complaint> findByUserAndPriority(User user, Priority priority, Pageable pageable);

    Page<Complaint> findByUserAndStatusAndPriority(User user, ComplaintStatus status, Priority priority, Pageable pageable);

    List<Complaint> findByAssignedWorkerIdAndStatusIn(Long workerId, List<ComplaintStatus> statuses);

    List<Complaint> findByAssignedWorkerIdOrderByCreatedAtDesc(Long workerId);
    
    @Query("SELECT c FROM Complaint c WHERE c.user.id = :userId ORDER BY c.createdAt DESC")
    Page<Complaint> findByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);
    
    @Query("SELECT c FROM Complaint c WHERE c.isFalseComplaint = true ORDER BY c.createdAt DESC")
    Page<Complaint> findByIsFalseComplaintTrue(Pageable pageable);
    
    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.status = :status")
    long countByStatus(@Param("status") ComplaintStatus status);
    
    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.priority = :priority")
    long countByPriority(@Param("priority") Priority priority);
    
    @Query("SELECT c FROM Complaint c WHERE c.createdAt BETWEEN :startDate AND :endDate ORDER BY c.createdAt DESC")
    List<Complaint> findByDateRange(@Param("startDate") LocalDateTime startDate, 
                                   @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT c FROM Complaint c WHERE " +
           "LOWER(c.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.blockNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.buildingNumber) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.roomNumber) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Complaint> searchComplaints(@Param("keyword") String keyword);

    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<User> searchUsers(@Param("keyword") String keyword, Pageable pageable);
    
    @Query("SELECT c FROM Complaint c WHERE c.user = :user AND c.isFalseComplaint = true")
    List<Complaint> findByUserAndIsFalseComplaintTrue(@Param("user") User user);
    
    @Query("SELECT c FROM Complaint c WHERE c.user = :user AND c.createdAt > :since")
    List<Complaint> findByUserAndCreatedAtAfter(@Param("user") User user, @Param("since") LocalDateTime since);
}
