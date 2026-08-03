package com.rgs.center.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Настраивает независимое выполнение периодических опросов устройств.
 */
@Configuration
public class SchedulingConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(SchedulingConfiguration.class);

    /**
     * Создаёт отдельные потоки для сетевых устройств и COM-приборов GDRM/SHP.
     *
     * @return планировщик фоновых задач
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        // Долгий ping одного устройства не должен блокировать остальные.
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("device-poll-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(5);
        log.info(
                "Device polling scheduler configured: poolSize={}, shutdownWait={} seconds",
                8,
                5
        );
        return scheduler;
    }
}
