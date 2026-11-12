package com.gamebakes.servicio_resenas.service;

import com.gamebakes.servicio_resenas.client.PedidoClient;
import com.gamebakes.servicio_resenas.model.Resena;
import com.gamebakes.servicio_resenas.repository.AutorizacionResenaRepository;
import com.gamebakes.servicio_resenas.repository.ResenaRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("null")
@Service
public class ResenaService {

    @Autowired
    private ResenaRepository resenaRepository;

    @Autowired
    private PedidoClient pedidoClient;

    @Autowired
    private AutorizacionResenaRepository autorizacionRepository;

    //CLIENTE: Obtener reseñas por producto con Circuit Breaker
    @CircuitBreaker(name = "resenaCB", fallbackMethod = "fallbackResenas")
    public List<Resena> obtenerPorProducto(Long productoId) {
        return resenaRepository.findByProductoId(productoId);
    }

    //VENDEDOR: Obtener sus reseñas con Circuit Breaker
    @CircuitBreaker(name = "resenaCB", fallbackMethod = "fallbackResenasVendedor")
    public List<Resena> obtenerPorVendedor(String vendedorId) {
        return resenaRepository.findByVendedorId(vendedorId);
    }

    //CLIENTE: Obtener sus reseñas con Circuit Breaker
    @CircuitBreaker(name = "resenaCB", fallbackMethod = "fallbackResenasCliente")
    public List<Resena> obtenerPorCliente(String clienteId) {
        return resenaRepository.findByClienteId(clienteId);
    }

    //Lógica para guardar reseña con validación de compra y estado entregado
    public Resena guardarResena(Resena resena) {
        System.out.println("--- CREANDO RESEÑA ---");
        System.out.println("Cliente ID: " + resena.getClienteId());
        System.out.println("Producto ID: " + resena.getProductoId());

        boolean autorizado = autorizacionRepository.existsByClienteIdAndProductoId(resena.getClienteId(), resena.getProductoId());

        if (!autorizado) {
            throw new RuntimeException("No autorizado. El cliente no ha comprado este producto o el pedido aun no ha sido entregado");
        }

        Resena nueva = resenaRepository.save(resena);
        System.out.println("Reseña guardada con ID: " + nueva.getId());

        return nueva;
    }

    //VENDEDOR: Responder a reseña
    public Resena responderResena(Long resenaId, String respuesta) {
        Resena resena = resenaRepository.findById(resenaId)
                .orElseThrow(() -> new RuntimeException("Reseña no encontrada"));
        resena.setRespuestaVendedor(respuesta);
        return resenaRepository.save(resena);
    }

    //MÉTODOS FALLBACK (Resiliencia)
    public List<Resena> fallbackResenas(Long id, Throwable t) {
        System.out.println("Circuit Breaker: Error obteniendo reseñas del producto. Motivo: " + t.getMessage());
        return new ArrayList<>();
    }

    public List<Resena> fallbackResenasVendedor(String id, Throwable t) {
        System.out.println("Circuit Breaker: Error obteniendo reseñas del vendedor. Motivo: " + t.getMessage());
        return new ArrayList<>();
    }

    public List<Resena> fallbackResenasCliente(String id, Throwable t) {
        System.out.println("Circuit Breaker: Error obteniendo reseñas del cliente. Motivo: " + t.getMessage());
        return new ArrayList<>();
    }
}