package com.example.serviciopagos.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

/**
 * Publica el evento pago.exitoso en AWS Kinesis Data Streams.
 * Es una salida paralela a RabbitMQ y Kafka: si falla, el flujo de negocio
 * no se ve afectado. Se activa con gamebakes.kinesis.enabled=true.
 */
@Component
public class KinesisEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KinesisEventPublisher.class);

    private final KinesisClient kinesisClient;

    @Value("${gamebakes.kinesis.enabled:false}")
    private boolean enabled;

    @Value("${gamebakes.kinesis.stream-name:gamebakes-eventos}")
    private String streamName;

    public KinesisEventPublisher(KinesisClient kinesisClient) {
        this.kinesisClient = kinesisClient;
    }

    public void publicarPagoExitoso(String jsonPago, String clienteId) {
        if (!enabled) {
            return;
        }
        try {
            kinesisClient.putRecord(PutRecordRequest.builder()
                    .streamName(streamName)
                    .partitionKey(clienteId)
                    .data(SdkBytes.fromUtf8String(jsonPago))
                    .build());
            log.info("[Kinesis] pago.exitoso publicado en stream {} -> {}", streamName, clienteId);
        } catch (Exception e) {
            log.error("[Kinesis] Error al publicar pago exitoso: {}", e.getMessage());
        }
    }
}
