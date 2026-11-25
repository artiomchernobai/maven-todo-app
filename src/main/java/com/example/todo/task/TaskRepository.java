package com.example.todo.task;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
            select task from Task task join fetch task.category category
            where (:status is null or task.status = :status)
              and (:priority is null or task.priority = :priority)
              and (:categoryId is null or category.id = :categoryId)
              and (:dueDate is null or task.dueDate = :dueDate)
            order by task.id
            """)
    List<Task> findMatching(@Param("status") TaskStatus status,
                            @Param("priority") TaskPriority priority,
                            @Param("categoryId") Long categoryId,
                            @Param("dueDate") LocalDate dueDate);
}
