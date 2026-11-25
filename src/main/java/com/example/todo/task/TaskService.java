package com.example.todo.task;

import com.example.todo.category.Category;
import com.example.todo.category.CategoryRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class TaskService {

    private final TaskRepository tasks;
    private final CategoryRepository categories;

    TaskService(TaskRepository tasks, CategoryRepository categories) {
        this.tasks = tasks;
        this.categories = categories;
    }

    List<TaskResponse> list(TaskStatus status, TaskPriority priority, Long categoryId, LocalDate dueDate) {
        return tasks.findMatching(status, priority, categoryId, dueDate).stream()
                .map(TaskResponse::from)
                .toList();
    }

    TaskResponse get(long id) {
        return TaskResponse.from(find(id));
    }

    @Transactional
    TaskResponse create(TaskRequest request) {
        Task task = new Task();
        applyRequest(task, request);
        return TaskResponse.from(tasks.save(task));
    }

    @Transactional
    TaskResponse update(long id, TaskRequest request) {
        Task task = find(id);
        applyRequest(task, request);
        return TaskResponse.from(task);
    }

    @Transactional
    TaskResponse complete(long id) {
        Task task = find(id);
        task.complete();
        return TaskResponse.from(task);
    }

    @Transactional
    void delete(long id) {
        tasks.delete(find(id));
    }

    private Task find(long id) {
        return tasks.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task " + id + " not found."));
    }

    private void applyRequest(Task task, TaskRequest request) {
        Long categoryId = request.categoryId();
        Category category = categories.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Category " + categoryId + " not found."));
        task.update(request.title(), request.description(), request.status(), request.priority(),
                request.dueDate(), category);
    }
}
