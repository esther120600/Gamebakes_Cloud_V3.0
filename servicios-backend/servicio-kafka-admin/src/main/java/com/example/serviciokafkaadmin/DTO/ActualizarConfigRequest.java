package com.example.serviciokafkaadmin.DTO;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

public record ActualizarConfigRequest(
        @NotEmpty(message = "Debes enviar al menos una configuracion")
        Map<String, String> configuraciones
) {
}