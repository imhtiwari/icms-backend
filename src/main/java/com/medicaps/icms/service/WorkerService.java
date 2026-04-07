package com.medicaps.icms.service;

import com.medicaps.icms.entity.Worker;
import com.medicaps.icms.entity.WorkerType;
import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.entity.ComplaintStatus;
import com.medicaps.icms.repository.WorkerRepository;
import com.medicaps.icms.repository.ComplaintRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Service
public class WorkerService {

    @Autowired
    private WorkerRepository workerRepository;

    @Autowired
    private ComplaintRepository complaintRepository;

    public Page<Worker> getAllWorkers(Pageable pageable) {
        return workerRepository.findAll(pageable);
    }

    public Page<Worker> getActiveWorkers(Pageable pageable) {
        return workerRepository.findByActive(true, pageable);
    }

    public Optional<Worker> getWorkerById(Long id) {
        return workerRepository.findById(id);
    }

    public Optional<Worker> getWorkerByEmail(String email) {
        return workerRepository.findByEmail(email);
    }

    public Optional<Worker> getWorkerByPhoneNumber(String phoneNumber) {
        return workerRepository.findByPhoneNumber(phoneNumber);
    }

    public Worker createWorker(Worker worker) {
        // Check if email already exists
        if (workerRepository.findByEmail(worker.getEmail()).isPresent()) {
            throw new RuntimeException("Worker with email " + worker.getEmail() + " already exists");
        }
        // Check if phone number already exists
        if (workerRepository.findByPhoneNumber(worker.getPhoneNumber()).isPresent()) {
            throw new RuntimeException("Worker with phone number " + worker.getPhoneNumber() + " already exists");
        }
        return workerRepository.save(worker);
    }

    public Worker updateWorker(Long id, Worker workerDetails) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        // Check if email is being changed and if new email already exists
        if (!worker.getEmail().equals(workerDetails.getEmail())) {
            if (workerRepository.findByEmail(workerDetails.getEmail()).isPresent()) {
                throw new RuntimeException("Worker with email " + workerDetails.getEmail() + " already exists");
            }
        }

        // Check if phone number is being changed and if new phone number already exists
        if (!worker.getPhoneNumber().equals(workerDetails.getPhoneNumber())) {
            if (workerRepository.findByPhoneNumber(workerDetails.getPhoneNumber()).isPresent()) {
                throw new RuntimeException("Worker with phone number " + workerDetails.getPhoneNumber() + " already exists");
            }
        }

        worker.setName(workerDetails.getName());
        worker.setEmail(workerDetails.getEmail());
        worker.setPhoneNumber(workerDetails.getPhoneNumber());
        worker.setDepartment(workerDetails.getDepartment());
        worker.setSpecialization(workerDetails.getSpecialization());
        worker.setDesignation(workerDetails.getDesignation());
        worker.setOfficeLocation(workerDetails.getOfficeLocation());
        worker.setWorkHours(workerDetails.getWorkHours());
        worker.setActive(workerDetails.getActive());
        worker.setWorkerType(workerDetails.getWorkerType());

        return workerRepository.save(worker);
    }

    public void deleteWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        workerRepository.delete(worker);
    }

    public void deactivateWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        worker.setActive(false);
        workerRepository.save(worker);
        
        // Bonus automation feature: automatically unassign them from any IN_PROGRESS or PENDING complaints
        List<Complaint> activeAssignments = complaintRepository.findByAssignedWorkerIdAndStatusIn(
                id, Arrays.asList(ComplaintStatus.IN_PROGRESS, ComplaintStatus.PENDING));
                
        for (Complaint complaint : activeAssignments) {
            complaint.setAssignedWorker(null);
            // Revert back to pending so that it flags for attention
            complaint.setStatus(ComplaintStatus.PENDING);
        }
        
        if (!activeAssignments.isEmpty()) {
            complaintRepository.saveAll(activeAssignments);
        }
    }

    public void activateWorker(Long id) {
        Worker worker = workerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        worker.setActive(true);
        workerRepository.save(worker);
    }

    public Page<Worker> getWorkersByType(WorkerType workerType, Pageable pageable) {
        return workerRepository.findByWorkerType(workerType, pageable);
    }

    public Page<Worker> getWorkersByDepartment(String department, Pageable pageable) {
        return workerRepository.findByDepartment(department, pageable);
    }

    public List<Worker> searchWorkers(String keyword) {
        List<Worker> results = new java.util.ArrayList<>();
        results.addAll(workerRepository.findByNameContainingIgnoreCase(keyword));
        results.addAll(workerRepository.findByEmailContainingIgnoreCase(keyword));
        results.addAll(workerRepository.findByDepartmentContainingIgnoreCase(keyword));
        results.addAll(workerRepository.findBySpecializationContainingIgnoreCase(keyword));
        results.addAll(workerRepository.findByDesignationContainingIgnoreCase(keyword));
        
        // Remove duplicates and return
        return results.stream().distinct().toList();
    }

    public List<Worker> searchActiveWorkers(String keyword) {
        List<Worker> results = searchWorkers(keyword);
        return results.stream().filter(Worker::getActive).distinct().toList();
    }

    public long getTotalWorkers() {
        return workerRepository.count();
    }

    public long getActiveWorkersCount() {
        return workerRepository.countByActive(true);
    }

    public List<String> getAllDepartments() {
        return workerRepository.findAllDepartments();
    }

    public List<Object[]> getWorkerStatsByType() {
        return workerRepository.countByWorkerTypeGroupBy();
    }
}
