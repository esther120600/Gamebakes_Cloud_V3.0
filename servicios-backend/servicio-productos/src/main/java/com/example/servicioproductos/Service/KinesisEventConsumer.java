package com.example.servicioproductos.Service;

import com.example.servicioproductos.Model.EventoKinesis;
import com.example.servicioproductos.Repository.EventoKinesisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.GetRecordsResponse;
import software.amazon.awssdk.services.kinesis.model.GetShardIteratorResponse;
import software.amazon.awssdk.services.kinesis.model.Record;
import software.amazon.awssdk.services.kinesis.model.Shard;
import software.amazon.awssdk.services.kinesis.model.ShardIteratorType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consume el stream de Kinesis con polling simple (GetShardIterator + GetRecords).
 * Los iteradores viven en memoria: si el servicio se reinicia, vuelve a leer desde
 * TRIM_HORIZON (puede reprocesar eventos retenidos). Para producción se usaría
 * KCL con checkpoint en DynamoDB.
 * Se activa con gamebakes.kinesis.enabled=true.
 */
@Component
public class KinesisEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(KinesisEventConsumer.class);

    private final KinesisClient kinesisClient;
    private final EventoKinesisRepository eventoKinesisRepository;

    private final Map<String, String> iteradoresPorShard = new ConcurrentHashMap<>();

    @Value("${gamebakes.kinesis.enabled:false}")
    private boolean enabled;

    @Value("${gamebakes.kinesis.stream-name:gamebakes-eventos}")
    private String streamName;

    public KinesisEventConsumer(KinesisClient kinesisClient, EventoKinesisRepository eventoKinesisRepository) {
        this.kinesisClient = kinesisClient;
        this.eventoKinesisRepository = eventoKinesisRepository;
    }

    @Scheduled(fixedDelayString = "${gamebakes.kinesis.poll-interval-ms:2000}")
    public void consumir() {
        if (!enabled) {
            return;
        }
        try {
            if (iteradoresPorShard.isEmpty()) {
                inicializarIteradores();
            }

            for (Map.Entry<String, String> entrada : iteradoresPorShard.entrySet()) {
                String shardId = entrada.getKey();
                GetRecordsResponse respuesta = kinesisClient.getRecords(b -> b
                        .shardIterator(entrada.getValue())
                        .limit(100));

                for (Record registro : respuesta.records()) {
                    EventoKinesis evento = new EventoKinesis();
                    evento.setShardId(shardId);
                    evento.setPartitionKey(registro.partitionKey());
                    evento.setSequenceNumber(registro.sequenceNumber());
                    evento.setPayload(registro.data().asUtf8String());
                    evento.setReceivedAt(LocalDateTime.now());
                    eventoKinesisRepository.save(evento);
                }

                if (!respuesta.records().isEmpty()) {
                    log.info("[Kinesis] {} evento(s) persistidos desde shard {}", respuesta.records().size(), shardId);
                }

                String siguiente = respuesta.nextShardIterator();
                if (siguiente != null) {
                    entrada.setValue(siguiente);
                } else {
                    iteradoresPorShard.remove(shardId);
                }
            }
        } catch (Exception e) {
            log.warn("[Kinesis] Error consumiendo stream {}: {} - se reinician los iteradores", streamName, e.getMessage());
            iteradoresPorShard.clear();
        }
    }

    private void inicializarIteradores() {
        Map<String, String> nuevosIteradores = new HashMap<>();
        kinesisClient.listShards(b -> b.streamName(streamName))
                .shards()
                .forEach(shard -> nuevosIteradores.put(shard.shardId(), crearIterador(shard.shardId())));
        iteradoresPorShard.putAll(nuevosIteradores);
        log.info("[Kinesis] Iteradores inicializados para {} shard(s) del stream {}", nuevosIteradores.size(), streamName);
    }

    private String crearIterador(String shardId) {
        GetShardIteratorResponse iterador = kinesisClient.getShardIterator(b -> b
                .streamName(streamName)
                .shardId(shardId)
                .shardIteratorType(ShardIteratorType.TRIM_HORIZON));
        return iterador.shardIterator();
    }
}
