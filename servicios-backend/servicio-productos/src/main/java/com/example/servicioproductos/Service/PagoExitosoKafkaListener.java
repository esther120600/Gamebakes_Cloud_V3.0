package com.example.servicioproductos.Service;

import com.example.servicioproductos.DTO.PagoExitosoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PagoExitosoKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(PagoExitosoKafkaListener.class);

    @Autowired
    private ProductoService productoService;

    @KafkaListener(topics = "eventos-pago", groupId = "grupo-productos")
    public void procesarPagoExitoso(PagoExitosoDTO pagoDTO) {
        try {
            log.info("Recibido evento pago.exitoso para cliente: {}", pagoDTO.getClienteId());

            if (pagoDTO.getItems() != null && !pagoDTO.getItems().isEmpty()) {
                pagoDTO.getItems().forEach(item -> {
                    productoService.restarStock(item.getProductoId(), item.getCantidad());
                    log.info("Stock restado: productoId={}, cantidad={}", item.getProductoId(), item.getCantidad());
                });
            } else if (pagoDTO.getProductoId() != null && pagoDTO.getCantidad() != null) {
                productoService.restarStock(pagoDTO.getProductoId(), pagoDTO.getCantidad());
                log.info("Stock restado (directo): productoId={}, cantidad={}", pagoDTO.getProductoId(), pagoDTO.getCantidad());
            } else {
                throw new IllegalArgumentException("El evento de pago no contiene información de productos válida.");
            }
        } catch (Exception e) {
            log.error("Error al procesar el stock para el pago {}: {}", pagoDTO.getTransaccionId(), e.getMessage());
            throw e;
        }
    }
}