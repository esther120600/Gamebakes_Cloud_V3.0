package com.example.servicioproductos.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_kinesis")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventoKinesis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String partitionKey;

    private String sequenceNumber;

    private String shardId;

    @Lob
    @Column(name = "payload")
    private String payload;

    private LocalDateTime receivedAt;
}
