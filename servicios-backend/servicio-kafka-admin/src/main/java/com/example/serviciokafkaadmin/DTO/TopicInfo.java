package com.example.serviciokafkaadmin.DTO;

import java.util.List;
import java.util.Map;

public record TopicInfo(
        String nombre,
        boolean interno,
        int particiones,
        int factorReplicacion,
        List<ParticionInfo> detalleParticiones,
        Map<String, String> configuraciones
) {
}