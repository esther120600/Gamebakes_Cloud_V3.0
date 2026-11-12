package com.gamebakes.servicio_resenas.service;

import com.gamebakes.servicio_resenas.DTO.PedidoEstadoDTO;
import com.gamebakes.servicio_resenas.model.AutorizacionResena;
import com.gamebakes.servicio_resenas.repository.AutorizacionResenaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PedidoEstadoKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(PedidoEstadoKafkaListener.class);

    @Autowired
    private AutorizacionResenaRepository autorizacionRepository;

    @KafkaListener(topics = "pedidos-estado", groupId = "servicio-resenas-group")
    public void procesarEstadoPedido(PedidoEstadoDTO evento){
        log.info("Evento kafka recibidio: Pedido #{} cambio a estado {}", evento.getPedidoId(), evento.getNuevoEstado());

        if ("ENTREGADO".equals(evento.getNuevoEstado())) {

            boolean yaExiste = autorizacionRepository.existsByClienteIdAndProductoId(evento.getClienteId(), evento.getProductoId());

            if (!yaExiste) {
                AutorizacionResena auth = new AutorizacionResena(null, evento.getClienteId(), evento.getProductoId(), evento.getPedidoId());
                autorizacionRepository.save(auth);
                log.info("Autorizacion de reseña habilitada para Cliente {} en Producto {}", evento.getClienteId(), evento.getProductoId());
            }
        }
    }
}
