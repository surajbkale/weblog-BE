package com.weblogs.blog;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BlogApplication {

	public static void main(String[] args) {
		// Load .env before Spring starts so ${VAR} placeholders in application.yml resolve.
		// ignoreIfMissing() → no-ops in production where vars come from the OS environment.
		Dotenv.configure()
				.ignoreIfMissing()
				.systemProperties()   // copies every .env entry into System.setProperty()
				.load();

		SpringApplication.run(BlogApplication.class, args);
	}

}
