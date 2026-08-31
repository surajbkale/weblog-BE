package com.weblogs.blog;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class BlogApplication {

	public static void main(String[] args) {
		// Load .env first (base config, committed to repo).
		// Then overlay .env.local if present — local-only overrides that are
		// NOT committed to source control (matches the Next.js convention).
		// ignoreIfMissing() → no-ops in production where vars come from the OS env.
		Dotenv.configure()
				.filename(".env")
				.ignoreIfMissing()
				.systemProperties()
				.load();

		Dotenv.configure()
				.filename(".env.local")
				.ignoreIfMissing()
				.systemProperties()   // .env.local overwrites any keys already set by .env
				.load();

		SpringApplication.run(BlogApplication.class, args);
	}

}

