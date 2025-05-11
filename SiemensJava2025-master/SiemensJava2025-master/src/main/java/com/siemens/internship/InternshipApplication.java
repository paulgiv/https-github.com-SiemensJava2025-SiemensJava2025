package com.siemens.internship;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableAsync // Gotta turn on Spring's async magic so @Async works
public class InternshipApplication {

	public static void main(String[] args) {
		SpringApplication.run(InternshipApplication.class, args);
	}

	/**
	 * Setting up a specific thread pool for our background jobs.
	 * Don't want to use the default for everything, more control this way.
	 * This "taskExecutor" bean name can be used in @Async("taskExecutor").
	 */
	@Bean(name = "taskExecutor")
	public Executor taskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(5);    // Start with 5 threads
		executor.setMaxPoolSize(10);    // Can go up to 10 if needed
		executor.setQueueCapacity(25);  // How many tasks can wait if all threads are busy
		executor.setThreadNamePrefix("ItemProcessing-"); // So I know which threads are doing what
		executor.initialize();
		return executor;
	}
}