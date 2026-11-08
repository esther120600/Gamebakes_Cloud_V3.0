package com.example.serviciokafkaadmin.DTO;

import jakarta.validation.constraints.Min;

public record AumentarParticionesRequest(
        @Min(value = 1, message = "El total de particiones debe ser al menos 1")
        int totalParticiones
) {
}