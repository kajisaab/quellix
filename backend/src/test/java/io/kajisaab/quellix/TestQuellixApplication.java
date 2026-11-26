package io.kajisaab.quellix;

import org.springframework.boot.SpringApplication;

public class TestQuellixApplication {

	public static void main(String[] args) {
		SpringApplication.from(QuellixApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
