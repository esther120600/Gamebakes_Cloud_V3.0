package com.gamebakes.servicio_resenas.repository;

import com.gamebakes.servicio_resenas.model.AutorizacionResena;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutorizacionResenaRepository extends JpaRepository<AutorizacionResena, Long> {
    boolean existsByClienteIdAndProductoId(String clienteId, Long productoId);
}
