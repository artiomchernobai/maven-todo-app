package com.example.todo.category;

record CategoryResponse(Long id, String name, String color) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getColor());
    }
}
