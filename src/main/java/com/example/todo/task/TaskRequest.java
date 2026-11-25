package com.example.todo.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record TaskRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 2000) String description,
        TaskStatus status,
        @NotNull TaskPriority priority,
        LocalDate dueDate,
        @NotNull @Positive Long categoryId
) {
}
