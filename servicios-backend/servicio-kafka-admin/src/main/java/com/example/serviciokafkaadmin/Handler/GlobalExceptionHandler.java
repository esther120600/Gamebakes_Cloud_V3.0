package com.example.serviciokafkaadmin.Handler;

import org.apache.kafka.common.errors.InvalidConfigurationException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.InvalidRequestException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respuesta(HttpStatus.BAD_REQUEST, "Datos invalidos", detalle);
    }

    // JSON mal formado, cuerpo vacio o tipos de dato incorrectos
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> jsonInvalido(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de la peticion ilegible: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, "JSON invalido",
                "El cuerpo de la peticion no es un JSON valido o tiene campos con un tipo incorrecto");
    }

    @ExceptionHandler(TopicExistsException.class)
    public ResponseEntity<Map<String, Object>> topicExiste(TopicExistsException ex) {
        return respuesta(HttpStatus.CONFLICT, "El topic ya existe", ex.getMessage());
    }

    @ExceptionHandler(UnknownTopicOrPartitionException.class)
    public ResponseEntity<Map<String, Object>> topicNoExiste(UnknownTopicOrPartitionException ex) {
        return respuesta(HttpStatus.NOT_FOUND, "El topic no existe", ex.getMessage());
    }

    @ExceptionHandler({
            InvalidTopicException.class,
            InvalidConfigurationException.class,
            InvalidPartitionsException.class,
            InvalidReplicationFactorException.class,
            InvalidRequestException.class
    })
    public ResponseEntity<Map<String, Object>> solicitudInvalida(RuntimeException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud rechazada por Kafka", ex.getMessage());
    }

    @ExceptionHandler(org.apache.kafka.common.errors.TimeoutException.class)
    public ResponseEntity<Map<String, Object>> timeoutKafka(org.apache.kafka.common.errors.TimeoutException ex) {
        log.error("Kafka no respondio: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Kafka no esta disponible", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generico(Exception ex) {
        log.error("Error inesperado", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> respuesta(HttpStatus estado, String error, String detalle) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("estado", estado.value());
        cuerpo.put("error", error);
        cuerpo.put("detalle", detalle);
        return ResponseEntity.status(estado).body(cuerpo);
    }
}