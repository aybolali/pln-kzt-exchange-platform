package pl.aybolali.plnkztexchangebot.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync  // Включаем поддержку @Async
@Slf4j
public class AsyncConfig {

    @Bean("customTaskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Сколько потоков создавать сразу
        executor.setCorePoolSize(5);

        // Максимальное количество потоков при нагрузке
        executor.setMaxPoolSize(10);

        // Размер очереди задач
        executor.setQueueCapacity(100);

        // Префикс имен потоков (для отладки)
        executor.setThreadNamePrefix("async-");

        // Что делать если очередь переполнена
        executor.setRejectedExecutionHandler(
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        executor.initialize();
        return executor;
    }
}