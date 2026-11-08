package com.example.serviciokafkaadmin.Service;

import com.example.serviciokafkaadmin.DTO.CrearTopicRequest;
import com.example.serviciokafkaadmin.DTO.GrupoInfo;
import com.example.serviciokafkaadmin.DTO.LagInfo;
import com.example.serviciokafkaadmin.DTO.ParticionInfo;
import com.example.serviciokafkaadmin.DTO.TopicInfo;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Administracion de Kafka: topics, configuraciones y consumer groups.
 * Usa el AdminClient. Los errores de Kafka se dejan subir como excepciones
 * para que el GlobalExceptionHandler las traduzca a codigos HTTP.
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    // Configuraciones de topic que se muestran en las respuestas
    private static final Set<String> CONFIGS_VISIBLES = Set.of(
            "cleanup.policy",
            "retention.ms",
            "retention.bytes",
            "min.insync.replicas",
            "segment.bytes",
            "max.message.bytes",
            "compression.type",
            "delete.retention.ms"
    );

    private final AdminClient adminClient;
    private final int particionesPorDefecto;
    private final short replicacionPorDefecto;
    private final long timeoutMs;

    public KafkaAdminService(
            AdminClient adminClient,
            @Value("${gamebakes.kafka.default-partitions}") int particionesPorDefecto,
            @Value("${gamebakes.kafka.default-replication-factor}") short replicacionPorDefecto,
            @Value("${gamebakes.kafka.request-timeout-ms}") long timeoutMs) {
        this.adminClient = adminClient;
        this.particionesPorDefecto = particionesPorDefecto;
        this.replicacionPorDefecto = replicacionPorDefecto;
        this.timeoutMs = timeoutMs;
    }

    // ---------------------------------------------------------------- TOPICS

    public void crearTopic(CrearTopicRequest req) {
        int particiones = req.particiones() != null ? req.particiones() : particionesPorDefecto;
        short replicacion = req.factorReplicacion() != null ? req.factorReplicacion() : replicacionPorDefecto;

        NewTopic nuevo = new NewTopic(req.nombre(), particiones, replicacion);
        if (req.configuraciones() != null && !req.configuraciones().isEmpty()) {
            nuevo.configs(req.configuraciones());
        }

        esperar(adminClient.createTopics(List.of(nuevo)).all());
        log.info("Topic creado: {} (particiones={}, replicacion={}, configs={})",
                req.nombre(), particiones, replicacion, req.configuraciones());
    }

    public List<String> listarTopics(boolean incluirInternos) {
        ListTopicsOptions opciones = new ListTopicsOptions().listInternal(incluirInternos);
        return esperar(adminClient.listTopics(opciones).names())
                .stream()
                .sorted()
                .toList();
    }

    public TopicInfo describirTopic(String nombre) {
        Map<String, TopicDescription> descripciones =
                esperar(adminClient.describeTopics(List.of(nombre)).allTopicNames());
        TopicDescription topic = descripciones.get(nombre);

        List<ParticionInfo> particiones = topic.partitions().stream()
                .map(p -> new ParticionInfo(
                        p.partition(),
                        p.leader() != null ? p.leader().id() : -1,
                        p.replicas().stream().map(Node::id).toList(),
                        p.isr().stream().map(Node::id).toList()))
                .toList();

        int factorReplicacion = topic.partitions().isEmpty()
                ? 0
                : topic.partitions().get(0).replicas().size();

        return new TopicInfo(
                nombre,
                topic.isInternal(),
                topic.partitions().size(),
                factorReplicacion,
                particiones,
                obtenerConfiguraciones(nombre));
    }

    public void actualizarConfiguraciones(String nombre, Map<String, String> configuraciones) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);

        List<AlterConfigOp> operaciones = configuraciones.entrySet().stream()
                .map(e -> new AlterConfigOp(new ConfigEntry(e.getKey(), e.getValue()), AlterConfigOp.OpType.SET))
                .toList();

        Map<ConfigResource, Collection<AlterConfigOp>> cambios = Map.of(recurso, operaciones);
        esperar(adminClient.incrementalAlterConfigs(cambios).all());
        log.info("Configuracion actualizada en topic {}: {}", nombre, configuraciones);
    }

    public void aumentarParticiones(String nombre, int totalParticiones) {
        esperar(adminClient.createPartitions(
                Map.of(nombre, NewPartitions.increaseTo(totalParticiones))).all());
        log.info("Particiones de {} aumentadas a {}", nombre, totalParticiones);
    }

    public void eliminarTopic(String nombre) {
        esperar(adminClient.deleteTopics(List.of(nombre)).all());
        log.info("Topic eliminado: {}", nombre);
    }

    // ------------------------------------------------------- CONSUMER GROUPS

    public List<GrupoInfo> listarGrupos() {
        Collection<ConsumerGroupListing> grupos = esperar(adminClient.listConsumerGroups().all());
        List<String> ids = grupos.stream().map(ConsumerGroupListing::groupId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }

        Map<String, ConsumerGroupDescription> descripciones =
                esperar(adminClient.describeConsumerGroups(ids).all());

        return descripciones.values().stream()
                .map(d -> new GrupoInfo(d.groupId(), String.valueOf(d.state()), d.members().size()))
                .sorted(Comparator.comparing(GrupoInfo::grupoId))
                .toList();
    }

    /** Lag por particion = ultimo offset del topic - ultimo offset confirmado por el grupo. */
    public List<LagInfo> lagDeGrupo(String grupoId) {
        Map<TopicPartition, OffsetAndMetadata> actuales =
                esperar(adminClient.listConsumerGroupOffsets(grupoId).partitionsToOffsetAndMetadata());
        if (actuales.isEmpty()) {
            return List.of();
        }

        Map<TopicPartition, OffsetSpec> pedido = new HashMap<>();
        actuales.keySet().forEach(tp -> pedido.put(tp, OffsetSpec.latest()));

        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> finales =
                esperar(adminClient.listOffsets(pedido).all());

        return actuales.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> {
                    long actual = e.getValue().offset();
                    long fin = finales.get(e.getKey()).offset();
                    return new LagInfo(
                            e.getKey().topic(),
                            e.getKey().partition(),
                            actual,
                            fin,
                            Math.max(0, fin - actual));
                })
                .sorted(Comparator.comparing(LagInfo::topic).thenComparingInt(LagInfo::particion))
                .toList();
    }

    // --------------------------------------------------------------- PRIVADOS

    private Map<String, String> obtenerConfiguraciones(String nombreTopic) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombreTopic);
        Map<ConfigResource, Config> resultado =
                esperar(adminClient.describeConfigs(List.of(recurso)).all());

        Map<String, String> configuraciones = new TreeMap<>();
        for (ConfigEntry entrada : resultado.get(recurso).entries()) {
            if (entrada.value() != null && CONFIGS_VISIBLES.contains(entrada.name())) {
                configuraciones.put(entrada.name(), entrada.value());
            }
        }
        return configuraciones;
    }

    /** Espera la respuesta de Kafka y deja subir los errores como RuntimeException. */
    private <T> T esperar(KafkaFuture<T> futuro) {
        try {
            return futuro.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            Throwable causa = e.getCause();
            if (causa instanceof RuntimeException re) {
                throw re;
            }
            throw new IllegalStateException(causa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Operacion interrumpida", e);
        } catch (java.util.concurrent.TimeoutException e) {
            throw new org.apache.kafka.common.errors.TimeoutException(
                    "Kafka no respondio en " + timeoutMs + " ms", e);
        }
    }
}