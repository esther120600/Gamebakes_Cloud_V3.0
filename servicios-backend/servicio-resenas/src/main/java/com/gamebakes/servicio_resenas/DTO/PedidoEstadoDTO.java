package com.gamebakes.servicio_resenas.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PedidoEstadoDTO {
    private Long pedidoId;
    private String clienteId;
    private String vendedorId;
    private Long productoId;
    private String nuevoEstado;
    private LocalDateTime fechaActualizacion;
    private String mensaje;
}
