package com.matriz;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Matriz backend: a modular monolith (ADR-0001). Each direct sub-package of
 * {@code com.matriz} is an application module verified by Spring Modulith.
 */
// The default in-memory user (with a generated password printed to the log) is
// disabled; users live in the database and authentication arrives in Increment 3.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class MatrizApplication {

	public static void main(String[] args) {
		SpringApplication.run(MatrizApplication.class, args);
	}

}
