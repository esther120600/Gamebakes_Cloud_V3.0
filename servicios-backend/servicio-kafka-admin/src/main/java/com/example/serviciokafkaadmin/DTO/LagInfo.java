package com.example.serviciokafkaadmin.DTO;

public record LagInfo(
        String topic,
        int particion,
        long offsetActual,
        long offsetFinal,
        long lag
) {
}