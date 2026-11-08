package com.example.serviciopagos.Service;

import com.example.serviciopagos.Config.RabbitMQConfig;
import com.example.serviciopagos.DTO.PagoExitosoDTO;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


@Component
public class PagoEventPublisher {
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private KafkaTemplate<String, PagoExitosoDTO> kafkaTemplate;

    @Value("${gamebakes.sqs.queue-pago-exitoso}")
    private String sqsQueueUrl;

    private static final String KAFKA_TOPIC_PAGOS = "eventos-pago";

    public void publicarPagoExitoso(String jsonPago, PagoExitosoDTO pagoDTO) {
        // Publicar a RabbitMQ (mantener sin cambios)
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PAGO_EXITOSO, jsonPago);
        System.out.println("[RabbitMQ] pago.exitoso publicado -> " + jsonPago);

        try{
            kafkaTemplate.send(KAFKA_TOPIC_PAGOS, pagoDTO.getClienteId(), pagoDTO);
            System.out.println("[Kafka] pago.exitoso publicado en topic " + KAFKA_TOPIC_PAGOS + " -> " + pagoDTO.getClienteId());
        } catch (Exception e) {
            System.out.println("[Kafka] Error al enviar pago exitoso: ]" + e.getMessage());
        }
    }
}
