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

    protected Category() {
    }

    Category(String name) {
        rename(name);
    }

    void rename(String name) {
        this.name = name.strip();
    }

    public Long getId() {
        return id;
    }

    String getName() {
        return name;
    }
}
