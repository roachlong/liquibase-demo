package com.cockroachdb.liquibase_demo;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.test.context.ActiveProfiles;

import com.cockroachdb.liquibase_demo.repositories.BookRepository;

@ActiveProfiles("local")
@DataJpaTest
@ComponentScan(basePackages = {"com.cockroachdb.crdb_demo.bootstrap"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class CRDBIntegrationTest {

    @Autowired
    BookRepository bookRepository;

    @Test
    void testCRDB() {
        long countBefore = bookRepository.count();
        MatcherAssert.assertThat("confirm init", countBefore == 2);
    }
    
}
