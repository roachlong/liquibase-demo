package com.cockroachdb.liquibase_demo.bootstrap;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.cockroachdb.liquibase_demo.domain.Book;
import com.cockroachdb.liquibase_demo.repositories.BookRepository;

@Profile({"local", "default"})
@Component
public class DataInitializer implements CommandLineRunner {

    private final BookRepository bookRepository;
    

    public DataInitializer(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }


    @Override
    public void run(String... args) throws Exception {
        String isbnDDD = "123";
        if (!bookRepository.existsByIsbnIgnoreCase(isbnDDD)) {
            Book bookDDD = new Book("Domain Driven Design", isbnDDD, "RandomHouse", null);
            bookRepository.save(bookDDD);
        }

        String isbnSIA = "234234";
        if (!bookRepository.existsByIsbnIgnoreCase(isbnSIA)) {
            Book bookSIA = new Book("Spting In Action", isbnSIA, "O'Riely", null);
            bookRepository.save(bookSIA);
        }

        bookRepository.findAll().forEach(book -> {
            System.out.println("Book Id: " + book.getId());
            System.out.println("Book Title: " + book.getTitle());
        });
    }

}
