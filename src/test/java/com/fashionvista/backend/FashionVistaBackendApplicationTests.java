package com.fashionvista.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FashionVistaBackendApplicationTests {

	static {
		try {
			io.github.cdimascio.dotenv.Dotenv dotenv = io.github.cdimascio.dotenv.Dotenv.configure()
					.ignoreIfMissing()
					.load();
			dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));
		} catch (Exception e) {
			// Ignore if .env file is not found (e.g., in CI environment)
			System.out.println("No .env file found, using environment variables or test profile");
		}
	}

	@Test
	void contextLoads() {
	}

}
