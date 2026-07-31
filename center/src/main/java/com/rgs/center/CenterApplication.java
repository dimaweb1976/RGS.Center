package com.rgs.center;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка запуска серверного приложения RGS.Center.
 * test gitea push email notification
 * test gitea push email notification. try #2
 * test gitea push email notification. try #3
 */
@SpringBootApplication
@EnableScheduling
public class CenterApplication {

    /**
     * @param args аргументы командной строки Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(CenterApplication.class, args);
    }
}
