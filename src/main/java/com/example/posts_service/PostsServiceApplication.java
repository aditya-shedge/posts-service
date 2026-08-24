package com.example.posts_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PostsServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PostsServiceApplication.class, args);
	}

}
