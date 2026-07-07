package com.divzero;

import com.divzero.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DivZeroApplication {

    public static void main(String[] args) {
        SpringApplication.run(DivZeroApplication.class, args);
    }
}
