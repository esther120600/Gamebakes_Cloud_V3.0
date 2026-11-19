package com.example.servicioproductos.Config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

@Configuration
public class KinesisConfig {

    @Value("${gamebakes.kinesis.region:us-east-1}")
    private String region;

    @Bean(destroyMethod = "close")
    public KinesisClient kinesisClient() {
        // Credenciales: default chain (instance profile en EC2, variables de entorno en local)
        return KinesisClient.builder()
                .region(Region.of(region))
                .build();
    }
}
