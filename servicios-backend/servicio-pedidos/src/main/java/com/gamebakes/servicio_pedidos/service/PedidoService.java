package com.gamebakes.servicio_pedidos.service;

import com.gamebakes.servicio_pedidos.DTO.PedidoEstadoDTO;
import com.gamebakes.servicio_pedidos.model.Pedido;
import com.gamebakes.servicio_pedidos.repository.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import com.gamebakes.servicio_pedidos.Config.RabbitMQConfig;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PedidoService {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private KafkaTemplate<String, PedidoEstadoDTO> kafkaTemplate;

    private static final String KAFKA_TOPIC_PEDIDOS = "pedidos-estado";

    //VISTA CLIENTE: Obtener sus pedidos personales
    public List<Pedido> obtenerPedidosPorCliente(String clienteId) {
        return pedidoRepository.findByClienteId(clienteId);
    }

    //VISTA VENDEDOR: Obtener pedidos dirigidos a sus productos
    public List<Pedido> obtenerPedidosPorVendedor(String vendedorId) {
        return pedidoRepository.findByVendedorId(vendedorId);
    }

    public Pedido crearPedido(Pedido pedido) {
        pedido.setEstado("PENDIENTE");
        Pedido nuevoPedido = pedidoRepository.save(pedido);

        String mensaje = "NUEVO_PEDIDO: El cliente " + pedido.getClienteNombre() + " compró " + pedido.getProductoNombre();

        //RabbitMQ: Notificar creación (se mantiene)
        notificarSeguimientoRabbitMQ(mensaje);

        //Kafka: Publicar evento de estado
        notificarEstadoKafka(nuevoPedido, mensaje);

        return nuevoPedido;
    }

    public Pedido actualizarEstado(Long id, String nuevoEstado) {
        Pedido pedido = pedidoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        pedido.setEstado(nuevoEstado);
        Pedido actualizado = pedidoRepository.save(pedido);

        String mensaje = "ESTADO_ACTUALIZADO: Pedido #" + id + " ahora está en " + nuevoEstado;

        notificarSeguimientoRabbitMQ(mensaje);

        notificarEstadoKafka(actualizado, mensaje);

        return actualizado;
    }

    public boolean validarCompra(String clienteId, Long productoId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteIdAndProductoId(clienteId, productoId);
        System.out.println("=== VALIDAR COMPRA ===");
        System.out.println("Cliente: " + clienteId + ", Producto: " + productoId);
        System.out.println("Pedidos encontrados: " + pedidos.size());
        return !pedidos.isEmpty();
    }

    public boolean validarEntregado(String clienteId, Long productoId) {
        List<Pedido> pedidos = pedidoRepository.findByClienteIdAndProductoId(clienteId, productoId);
        System.out.println("=== VALIDAR ENTREGADO ===");
        System.out.println("Cliente: " + clienteId + ", Producto: " + productoId);
        System.out.println("Pedidos encontrados: " + pedidos.size());
        for (Pedido pedido : pedidos) {
            System.out.println("  - Pedido ID: " + pedido.getId() + ", Estado: " + pedido.getEstado());
        }
        boolean entregado = pedidos.stream()
                .anyMatch(pedido -> "ENTREGADO".equals(pedido.getEstado()));
        System.out.println("Resultado: " + entregado);
        return entregado;
    }

    private void notificarSeguimientoRabbitMQ(String mensaje) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.RK_PEDIDO_SEGUIMIENTO, mensaje);
        } catch (Exception e) {
            System.err.println("No se pudo publicar seguimiento en RabbitMQ: " + e.getMessage());
        }
    }

    private void notificarEstadoKafka(Pedido pedido, String mensaje) {
        try {
            PedidoEstadoDTO dto = new PedidoEstadoDTO(
                    pedido.getId(),
                    pedido.getClienteId(),
                    pedido.getVendedorId(),
                    pedido.getProductoId(),
                    pedido.getEstado(),
                    LocalDateTime.now(),
                    mensaje
            );
            kafkaTemplate.send(KAFKA_TOPIC_PEDIDOS, String.valueOf(pedido.getId()), dto);
            System.out.println("[Kafka] Evento publicado en " + KAFKA_TOPIC_PEDIDOS + ": Pedido #" + pedido.getId());
        } catch (Exception e) {
            System.err.println("[Kafka] No se pudo publicar el estado del pedido: " + e.getMessage());
        }
    }
}