package co.nexoeventos.controlador;

import java.time.LocalDate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import co.nexoeventos.servicio.ConsultaServicio;

@RestController
@RequestMapping("/api")
public class ConsultaControlador {
    private final SesionWeb web;
    private final ConsultaServicio consultas;

    public ConsultaControlador(SesionWeb web,ConsultaServicio consultas) {
        this.web=web; this.consultas=consultas;
    }

    @GetMapping("/panel")
    Map<String,Object> panel(HttpServletRequest p) { return consultas.panel(web.actor(p)); }
    @GetMapping("/busqueda")
    Map<String,Object> buscar(HttpServletRequest p,@RequestParam(defaultValue="") String q,
        @RequestParam(required=false) String tipo,@RequestParam(required=false) String categoria,
        @RequestParam(required=false) String lugar,@RequestParam(required=false) LocalDate desde,
        @RequestParam(required=false) LocalDate hasta) {
        return consultas.buscar(web.actor(p),q,tipo,categoria,lugar,desde,hasta);
    }
    @GetMapping("/busqueda/recientes")
    List<Map<String,String>> recientes(HttpServletRequest p) { return consultas.recientes(web.actor(p)); }
}
