package com.medicaps.icms.repository;

import com.medicaps.icms.entity.Worker;
import com.medicaps.icms.entity.WorkerType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkerRepository extends JpaRepository<Worker, Long> {
    
    List<Worker> findByNameContainingIgnoreCase(String name);
    
    List<Worker> findByEmailContainingIgnoreCase(String email);
    
    List<Worker> findByDepartmentContainingIgnoreCase(String department);
    
    List<Worker> findBySpecializationContainingIgnoreCase(String specialization);
    
    List<Worker> findByDesignationContainingIgnoreCase(String designation);
    
    Optional<Worker> findByEmail(String email);
    
    Optional<Worker> findByPhoneNumber(String phoneNumber);
    
    Page<Worker> findByActive(Boolean active, Pageable pageable);
    
    Page<Worker> findByWorkerType(WorkerType workerType, Pageable pageable);
    
    Page<Worker> findByActiveAndWorkerType(Boolean active, WorkerType workerType, Pageable pageable);
    
    Page<Worker> findByDepartment(String department, Pageable pageable);
    
    long countByActive(Boolean active);
    
    long countByWorkerType(WorkerType workerType);
    
    @Query("SELECT DISTINCT w.department FROM Worker w WHERE w.department IS NOT NULL ORDER BY w.department")
    List<String> findAllDepartments();
    
    @Query("SELECT w.workerType, COUNT(w) FROM Worker w GROUP BY w.workerType")
    List<Object[]> countByWorkerTypeGroupBy();
}
