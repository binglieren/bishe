package com.example.kaoyan.question;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.example.kaoyan.question")
@EntityScan("com.example.kaoyan.entity")
@EnableJpaRepositories("com.example.kaoyan.repository")
public class QuestionMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuestionMcpApplication.class, args);
    }
}
