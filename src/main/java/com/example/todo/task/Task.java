package com.example.todo.task;

import com.example.todo.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Entity
@Table(name = "tasks")
class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    protected Task() {
    }

    void update(String title, String description, TaskStatus status, TaskPriority priority,
                LocalDate dueDate, Category category) {
        this.title = title.strip();
        this.description = description;
        if (status != null) {
            this.status = status;
        }
        this.priority = priority;
        this.dueDate = dueDate;
        this.category = category;
    }

    void complete() {
        status = TaskStatus.DONE;
    }

    Long getId() {
        return id;
    }

    String getTitle() {
        return title;
    }

    String getDescription() {
        return description;
    }

    TaskStatus getStatus() {
        return status;
    }

    TaskPriority getPriority() {
        return priority;
    }

    LocalDate getDueDate() {
        return dueDate;
    }

    Category getCategory() {
        return category;
    }
}
