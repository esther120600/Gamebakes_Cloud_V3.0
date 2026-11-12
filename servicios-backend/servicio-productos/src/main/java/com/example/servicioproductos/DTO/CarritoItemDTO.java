package com.example.servicioproductos.DTO;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CarritoItemDTO {
    private Long idItem;
    private String clienteId;
    private Long productoId;
    private Integer cantidad;
    private Double precioUnitario;
}
