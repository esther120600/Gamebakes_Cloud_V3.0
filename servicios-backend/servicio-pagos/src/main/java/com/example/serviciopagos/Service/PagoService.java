package com.example.serviciopagos.Service;

import com.example.serviciopagos.DTO.PagoExitosoDTO;
import com.example.serviciopagos.DTO.SolicitudPagoDTO;
import com.example.serviciopagos.Model.Pago;
import com.example.serviciopagos.Model.CarritoItem;
import com.example.serviciopagos.Model.ProductoStockCache;
import com.example.serviciopagos.Repository.PagoRepository;
import com.example.serviciopagos.Repository.ProductoStockCacheRepository;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.preference.*;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.resources.preference.Preference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PagoService {

    @Autowired
    private PagoRepository pagoRepository;

    @Autowired
    private CarritoService carritoService;

    @Autowired
    private ProductoStockCacheRepository stockCacheRepository;

    @Autowired
    private PagoEventPublisher pagoEventPublisher;

    private String accessToken = "APP_USR-6384651523153058-051023-18ce169c7c92f41fc1af6ae5d5ad9a39-3392426062";

    public Pago iniciarPagoMP(SolicitudPagoDTO solicitud) {

        ProductoStockCache stockCache = stockCacheRepository.findById(solicitud.getProductoId())
                .orElseThrow(() -> new RuntimeException("El producto no se encuentra disponible en el catálogo."));

        if (stockCache.getStockDisponible() <= 0) {
            throw new RuntimeException("El producto se encuentra agotado.");
        }
        if (stockCache.getStockDisponible() < solicitud.getCantidad()) {
            throw new RuntimeException("No hay suficiente stock. Solo quedan " + stockCache.getStockDisponible() + " unidades.");
        }

        Pago pago = new Pago();
        pago.setProductoId(solicitud.getProductoId());
        pago.setCantidad(solicitud.getCantidad());
        pago.setClienteId(solicitud.getClienteId());
        pago.setMonto(solicitud.getMonto());
        pago.setMetodoPago("MERCADO_PAGO");
        pago.setFechaPago(LocalDateTime.now());
        pago.setEstado("PENDIENTE");
        pago = pagoRepository.save(pago);

        try {
            MercadoPagoConfig.setAccessToken(accessToken);

            PreferenceItemRequest itemRequest = PreferenceItemRequest.builder()
                    .title("Compra Directa Gamebakes")
                    .quantity(1)
                    .unitPrice(BigDecimal.valueOf(solicitud.getMonto()))
                    .build();
            List<PreferenceItemRequest> items = new ArrayList<>();
            items.add(itemRequest);

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(items)
                    .externalReference(pago.getIdPago().toString())
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);
            pago.setTransaccionId(preference.getInitPoint());
            return pagoRepository.save(pago);

        } catch (MPApiException apiEx) {
            String jsonError = apiEx.getApiResponse() != null ? apiEx.getApiResponse().getContent() : "Sin detalles de MP";
            System.err.println("💥 ERROR EXACTO DE MERCADO PAGO: " + jsonError);
            throw new RuntimeException(jsonError);
        } catch (Exception e) {
            System.err.println("💥 ERROR INTERNO: " + e.getMessage());
            throw new RuntimeException("Error interno: " + e.getMessage());
        }
    }

    public Pago iniciarPagoCarrito(String clienteId) {
        List<CarritoItem> itemsCarrito = carritoService.listarPorCliente(clienteId);
        if (itemsCarrito.isEmpty()) throw new RuntimeException("El carrito está vacío");

        for (CarritoItem ci : itemsCarrito) {
            ProductoStockCache stockCache = stockCacheRepository.findById(ci.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Un producto de tu carrito ya no está disponible."));

            if (stockCache.getStockDisponible() < ci.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para uno de los productos de tu carrito. Revisa las cantidades.");
            }
        }

        Double total = itemsCarrito.stream()
                .mapToDouble(i -> i.getPrecioUnitario() * i.getCantidad())
                .sum();

        Pago pago = new Pago();
        pago.setClienteId(clienteId);
        pago.setMonto(total);
        pago.setMetodoPago("MERCADO_PAGO");
        pago.setFechaPago(LocalDateTime.now());
        pago.setEstado("PENDIENTE");
        pago = pagoRepository.save(pago);

        try {
            MercadoPagoConfig.setAccessToken(accessToken);
            List<PreferenceItemRequest> itemsPreference = new ArrayList<>();
            for (CarritoItem ci : itemsCarrito) {
                itemsPreference.add(PreferenceItemRequest.builder()
                        .title("Producto #" + ci.getProductoId())
                        .quantity(ci.getCantidad())
                        .unitPrice(BigDecimal.valueOf(ci.getPrecioUnitario()))
                        .build());
            }

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(itemsPreference)
                    .externalReference(pago.getIdPago().toString())
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);
            pago.setTransaccionId(preference.getInitPoint());
            return pagoRepository.save(pago);

        } catch (MPApiException apiEx) {
            String jsonError = apiEx.getApiResponse() != null ? apiEx.getApiResponse().getContent() : "Sin detalles de MP";
            System.err.println("💥 ERROR EXACTO DE MERCADO PAGO: " + jsonError);
            throw new RuntimeException(jsonError);
        } catch (Exception e) {
            System.err.println("💥 ERROR INTERNO: " + e.getMessage());
            throw new RuntimeException("Error interno: " + e.getMessage());
        }
    }

    @Transactional
    public Pago confirmarPago(Long idPago, String usuarioAutenticadoId, String token, String nombreUsuario) {
        Pago pago = pagoRepository.findById(idPago).orElseThrow();

        if (!pago.getClienteId().equals(usuarioAutenticadoId)) {
            throw new RuntimeException("No autorizado");
        }

        if ("APROBADO".equals(pago.getEstado())) {
            carritoService.limpiarCarrito(pago.getClienteId());
            return pago;
        }

        pago.setEstado("APROBADO");
        pago.setTransaccionId("MP-CONFIRM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pago = pagoRepository.save(pago);

        Map<String, Object> pagoInfo = new HashMap<>();
        pagoInfo.put("clienteId", pago.getClienteId());
        pagoInfo.put("clienteNombre", nombreUsuario);
        pagoInfo.put("monto", pago.getMonto());
        pagoInfo.put("transaccionId", pago.getTransaccionId());

        PagoExitosoDTO pagoDTO = new PagoExitosoDTO();
        pagoDTO.setClienteId(pago.getClienteId());
        pagoDTO.setClienteNombre(nombreUsuario);
        pagoDTO.setMonto(pago.getMonto());
        pagoDTO.setTransaccionId(pago.getTransaccionId());

        if (pago.getProductoId() != null) {
            pagoInfo.put("productoId", pago.getProductoId());
            pagoInfo.put("cantidad", pago.getCantidad());

            pagoDTO.setProductoId(pago.getProductoId());
            pagoDTO.setCantidad(pago.getCantidad());
        } else {
            List<CarritoItem> items = carritoService.listarPorCliente(pago.getClienteId());
            pagoInfo.put("items", items);

            pagoDTO.setItems(items);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonPago = mapper.writeValueAsString(pagoInfo);
            pagoEventPublisher.publicarPagoExitoso(jsonPago, pagoDTO);
        } catch (Exception e) {
            System.err.println("Error al publicar evento de pago: " + e.getMessage());
        }

        carritoService.limpiarCarrito(pago.getClienteId());
        return pago;
    }

    public List<Pago> obtenerHistorialPorCliente(String clienteId) {
        return pagoRepository.findByClienteId(clienteId);
    }
}