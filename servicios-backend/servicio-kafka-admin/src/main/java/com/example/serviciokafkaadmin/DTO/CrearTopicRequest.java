package com.example.serviciokafkaadmin.DTO;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record CrearTopicRequest(
        @NotBlank(message = "El nombre del topic es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre solo puede tener letras, numeros, punto, guion y guion bajo")
        String nombre,

        @Min(value = 1, message = "Debe haber al menos 1 particion")
        Integer particiones,

        @Min(value = 1, message = "El factor de replicacion debe ser al menos 1")
        Short factorReplicacion,

        // Ej: {"retention.ms": "604800000", "cleanup.policy": "delete"}
        Map<String, String> configuraciones
) {
}
