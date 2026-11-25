package com.example.todo.category;

import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
class CategoryService {

    private final CategoryRepository categories;

    CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    List<CategoryResponse> list() {
        return categories.findAll(Sort.by("id")).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    CategoryResponse get(long id) {
        return CategoryResponse.from(find(id));
    }

    @Transactional
    CategoryResponse create(String name) {
        return save(new Category(name));
    }

    @Transactional
    CategoryResponse update(long id, String name) {
        Category category = find(id);
        category.rename(name);
        return save(category);
    }

    @Transactional
    void delete(long id) {
        categories.delete(find(id));
        try {
            categories.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Move or delete the tasks in this category first.", ex);
        }
    }

    private Category find(long id) {
        return categories.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category " + id + " not found."));
    }

    private CategoryResponse save(Category category) {
        try {
            return CategoryResponse.from(categories.saveAndFlush(category));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A category with this name already exists.", ex);
        }
    }
}
