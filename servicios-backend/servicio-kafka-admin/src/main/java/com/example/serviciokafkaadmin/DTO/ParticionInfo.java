package com.example.serviciokafkaadmin.DTO;

import java.util.List;

public record ParticionInfo(
        int particion,
        int lider,
        List<Integer> replicas,
        List<Integer> isr
) {
}