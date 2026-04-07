package com.medicaps.icms.controller;

import com.medicaps.icms.dto.ComplaintResponse;
import com.medicaps.icms.entity.Worker;
import com.medicaps.icms.entity.WorkerType;
import com.medicaps.icms.entity.Complaint;
import com.medicaps.icms.repository.ComplaintRepository;
import com.medicaps.icms.service.WorkerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/workers")
@CrossOrigin(origins = "*", maxAge = 3600)
public class WorkerController {

    @Autowired
    private WorkerService workerService;

    @Autowired
    private ComplaintRepository complaintRepository;

    @GetMapping
    public ResponseEntity<Page<Worker>> getAllWorkers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "name") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir,
            @RequestParam(value = "active", required = false) Boolean active) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Worker> workers;
        if (active != null) {
            workers = active ? workerService.getActiveWorkers(pageable) : workerService.getAllWorkers(pageable);
        } else {
            workers = workerService.getAllWorkers(pageable);
        }

        return ResponseEntity.ok(workers);
    }

    @GetMapping("/active")
    public ResponseEntity<Page<Worker>> getActiveWorkers(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "sortBy", defaultValue = "name") String sortBy,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Worker> workers = workerService.getActiveWorkers(pageable);
        return ResponseEntity.ok(workers);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Worker> getWorkerById(@PathVariable("id") Long id) {
        Optional<Worker> worker = workerService.getWorkerById(id);
        return worker.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/complaints")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> getWorkerComplaints(@PathVariable("id") Long id) {
        Optional<Worker> workerOpt = workerService.getWorkerById(id);
        if (workerOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        List<Complaint> complaints = complaintRepository.findByAssignedWorkerIdOrderByCreatedAtDesc(id);
        List<ComplaintResponse> response = complaints.stream().map(c -> {
            ComplaintResponse dto = new ComplaintResponse();
            dto.setId(c.getId());
            dto.setTitle(c.getTitle());
            dto.setDescription(c.getDescription());
            dto.setStatus(c.getStatus());
            dto.setPriority(c.getPriority());
            dto.setAssetCategory(c.getAssetCategory());
            dto.setBlockNumber(c.getBlockNumber());
            dto.setBuildingNumber(c.getBuildingNumber());
            dto.setRoomNumber(c.getRoomNumber());
            dto.setCreatedAt(c.getCreatedAt());
            dto.setUpdatedAt(c.getUpdatedAt());
            dto.setResolvedAt(c.getResolvedAt());
            if (c.getUser() != null) {
                dto.setUserName(c.getUser().getName());
                dto.setUserEmail(c.getUser().getEmail());
            }
            dto.setWorkerId(workerOpt.get().getId());
            dto.setWorkerName(workerOpt.get().getName());
            return dto;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Worker> createWorker(@RequestBody Worker worker) {
        try {
            Worker newWorker = workerService.createWorker(worker);
            return ResponseEntity.ok(newWorker);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Worker> updateWorker(@PathVariable("id") Long id, @RequestBody Worker workerDetails) {
        try {
            Worker updatedWorker = workerService.updateWorker(id, workerDetails);
            return ResponseEntity.ok(updatedWorker);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> deleteWorker(@PathVariable("id") Long id) {
        try {
            workerService.deleteWorker(id);
            return ResponseEntity.ok(Map.of("message", "Worker deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> deactivateWorker(@PathVariable("id") Long id) {
        try {
            workerService.deactivateWorker(id);
            return ResponseEntity.ok(Map.of("message", "Worker deactivated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<?> activateWorker(@PathVariable("id") Long id) {
        try {
            workerService.activateWorker(id);
            return ResponseEntity.ok(Map.of("message", "Worker activated successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/type/{workerType}")
    public ResponseEntity<Page<Worker>> getWorkersByType(
            @PathVariable WorkerType workerType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Worker> workers = workerService.getWorkersByType(workerType, pageable);
        return ResponseEntity.ok(workers);
    }

    @GetMapping("/department/{department}")
    public ResponseEntity<Page<Worker>> getWorkersByDepartment(
            @PathVariable String department,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Worker> workers = workerService.getWorkersByDepartment(department, pageable);
        return ResponseEntity.ok(workers);
    }

    @GetMapping("/search")
    public ResponseEntity<List<Worker>> searchWorkers(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "false") boolean activeOnly) {

        List<Worker> workers = activeOnly ? workerService.searchActiveWorkers(keyword)
                : workerService.searchWorkers(keyword);
        return ResponseEntity.ok(workers);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'OWNER')")
    public ResponseEntity<Map<String, Object>> getWorkerStats() {
        Map<String, Object> stats = Map.of(
                "totalWorkers", workerService.getTotalWorkers(),
                "activeWorkers", workerService.getActiveWorkersCount(),
                "departments", workerService.getAllDepartments(),
                "statsByType", workerService.getWorkerStatsByType());
        return ResponseEntity.ok(stats);
    }
}
