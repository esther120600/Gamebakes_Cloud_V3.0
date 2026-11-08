package com.example.serviciokafkaadmin.Controller;

import com.example.serviciokafkaadmin.DTO.ActualizarConfigRequest;
import com.example.serviciokafkaadmin.DTO.AumentarParticionesRequest;
import com.example.serviciokafkaadmin.DTO.CrearTopicRequest;
import com.example.serviciokafkaadmin.DTO.GrupoInfo;
import com.example.serviciokafkaadmin.DTO.LagInfo;
import com.example.serviciokafkaadmin.DTO.TopicInfo;
import com.example.serviciokafkaadmin.Service.KafkaAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService servicio;

    public KafkaAdminController(KafkaAdminService servicio) {
        this.servicio = servicio;
    }

    // ---------------------------------------------------------------- TOPICS

    @PostMapping("/topics")
    public ResponseEntity<Map<String, String>> crearTopic(@Valid @RequestBody CrearTopicRequest request) {
        servicio.crearTopic(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("mensaje", "Topic creado", "nombre", request.nombre()));
    }

    @GetMapping("/topics")
    public List<String> listarTopics(@RequestParam(defaultValue = "false") boolean internos) {
        return servicio.listarTopics(internos);
    }

    @GetMapping("/topics/{nombre}")
    public TopicInfo describirTopic(@PathVariable String nombre) {
        return servicio.describirTopic(nombre);
    }

    @PutMapping("/topics/{nombre}/configuraciones")
    public Map<String, String> actualizarConfiguraciones(
            @PathVariable String nombre,
            @Valid @RequestBody ActualizarConfigRequest request) {
        servicio.actualizarConfiguraciones(nombre, request.configuraciones());
        return Map.of("mensaje", "Configuracion actualizada", "nombre", nombre);
    }

    @PutMapping("/topics/{nombre}/particiones")
    public Map<String, String> aumentarParticiones(
            @PathVariable String nombre,
            @Valid @RequestBody AumentarParticionesRequest request) {
        servicio.aumentarParticiones(nombre, request.totalParticiones());
        return Map.of("mensaje", "Particiones aumentadas", "nombre", nombre,
                "totalParticiones", String.valueOf(request.totalParticiones()));
    }

    @DeleteMapping("/topics/{nombre}")
    public ResponseEntity<Void> eliminarTopic(@PathVariable String nombre) {
        servicio.eliminarTopic(nombre);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------- CONSUMER GROUPS

    @GetMapping("/grupos")
    public List<GrupoInfo> listarGrupos() {
        return servicio.listarGrupos();
    }

    @GetMapping("/grupos/{grupoId}/lag")
    public List<LagInfo> lagDeGrupo(@PathVariable String grupoId) {
        return servicio.lagDeGrupo(grupoId);
    }
}