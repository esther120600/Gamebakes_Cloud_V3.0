package com.example.serviciopagos.DTO;

import com.example.serviciopagos.Model.CarritoItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagoExitosoDTO {
    private String clienteId;
    private String clienteNombre;
    private Double monto;
    private String transaccionId;
    private Long productoId;
    private Integer cantidad;
    private List<CarritoItem> items;
}
