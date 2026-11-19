package com.example.servicioproductos.Repository;

import com.example.servicioproductos.Model.EventoKinesis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoKinesisRepository extends JpaRepository<EventoKinesis, Long> {
}
