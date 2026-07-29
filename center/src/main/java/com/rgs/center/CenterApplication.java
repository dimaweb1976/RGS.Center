package com.rgs.center;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Точка запуска серверного приложения RGS.Center.
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
