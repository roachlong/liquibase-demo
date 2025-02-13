package com.cockroachdb.liquibase_demo;

import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.cockroachdb.liquibase_demo.repositories.BookRepository;

@SpringBootTest
class LiquibaseDemoApplicationTests {

	@Autowired
	BookRepository bookRepository;

	@Test
	void testBookRepository() {
		Long count = bookRepository.count();

		MatcherAssert.assertThat("confirm records exist in db", count > 0);
	}

	@Test
	void contextLoads() {
	}

}
