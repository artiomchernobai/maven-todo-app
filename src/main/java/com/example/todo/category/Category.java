package com.example.todo.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 7)
    private String color;

    protected Category() {
    }

    Category(String name, String color) {
        update(name, color);
    }

    void update(String name, String color) {
        this.name = name.strip();
        if (color != null) {
            this.color = color;
        }
    }

    public Long getId() {
        return id;
    }

    String getName() {
        return name;
    }

    String getColor() {
        return color;
    }
}
