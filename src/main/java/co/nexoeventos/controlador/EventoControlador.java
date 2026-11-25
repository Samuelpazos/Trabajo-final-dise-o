package co.nexoeventos.controlador;

import java.time.LocalDate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import co.nexoeventos.dominio.EntradasApi.*;
import co.nexoeventos.servicio.*;

@RestController
@RequestMapping("/api/eventos")
public class EventoControlador {
    private final SesionWeb web;
    private final EventoServicio eventos;
    private final AsistenciaServicio asistencia;

    public EventoControlador(SesionWeb web,EventoServicio eventos,AsistenciaServicio asistencia) {
        this.web=web; this.eventos=eventos; this.asistencia=asistencia;
    }

    @GetMapping
    List<Map<String,Object>> catalogo(HttpServletRequest p,@RequestParam(required=false) String nombre,
        @RequestParam(required=false) String categoria,@RequestParam(required=false) String lugar,
        @RequestParam(required=false) LocalDate desde,@RequestParam(required=false) LocalDate hasta,
        @RequestParam(defaultValue="false") boolean favoritos) {
        return eventos.catalogo(web.actor(p),nombre,categoria,lugar,desde,hasta,favoritos);
    }
    @GetMapping("/gestion")
    List<Map<String,Object>> gestion(HttpServletRequest p,@RequestParam(required=false) String estado,
        @RequestParam(required=false) String filtro,@RequestParam(required=false) String orden) {
        return eventos.gestionados(web.actor(p),estado,filtro,orden);
    }
    @GetMapping("/{id}")
    Map<String,Object> detalle(HttpServletRequest p,@PathVariable String id) { return eventos.detalle(web.actor(p),id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> crear(HttpServletRequest p,@Valid @RequestBody DatosEvento datos) {
        return eventos.guardar(web.actor(p),null,datos);
    }
    @PutMapping("/{id}")
    Map<String,Object> editar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody DatosEvento datos) {
        return eventos.guardar(web.actor(p),id,datos);
    }
    @PatchMapping("/{id}/estado")
    Map<String,Object> estado(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody Estado datos) {
        return eventos.estado(web.actor(p),id,datos);
    }
    @PostMapping("/{id}/cancelacion")
    Map<String,Object> cancelar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody CancelarEvento datos) {
        return eventos.cancelar(web.actor(p),id,datos);
    }
    @PostMapping("/{id}/actividades")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> actividad(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody DatosActividad datos) {
        return eventos.actividad(web.actor(p),id,datos);
    }
    @PutMapping("/{id}/favorito")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void favorito(HttpServletRequest p,@PathVariable String id) { eventos.favorito(web.actor(p),id,true); }
    @DeleteMapping("/{id}/favorito")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void quitarFavorito(HttpServletRequest p,@PathVariable String id) { eventos.favorito(web.actor(p),id,false); }
    @PostMapping("/{id}/asistencia")
    ResponseEntity<Map<String,Object>> registrar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody Codigo datos) {
        Map<String,Object> resultado=asistencia.validar(web.actor(p),id,datos.codigo());
        return ResponseEntity.status((Integer)resultado.get("estado")).body(resultado);
    }
    @GetMapping("/{id}/asistencia")
    Map<String,Object> estadisticas(HttpServletRequest p,@PathVariable String id) {
        return asistencia.estadisticas(web.actor(p),id);
    }
}
