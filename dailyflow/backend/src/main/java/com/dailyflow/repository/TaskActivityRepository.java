package com.dailyflow.repository;

import com.dailyflow.domain.TaskActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskActivityRepository extends JpaRepository<TaskActivity, Long> {

    Page<TaskActivity> findByUserId(Long userId, Pageable pageable);

    Page<TaskActivity> findByUserIdAndTaskId(Long userId, Long taskId, Pageable pageable);
}
