package com.cockroachdb.liquibase_demo.repositories;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.cockroachdb.liquibase_demo.domain.Book;

public interface BookRepository extends JpaRepository<Book, UUID> {

    boolean existsByIsbnIgnoreCase(String isbn);
    
}
