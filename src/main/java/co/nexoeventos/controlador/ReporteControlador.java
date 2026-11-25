package co.nexoeventos.controlador;

import java.time.LocalDate;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.Dominio.*;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Actividad;
import co.nexoeventos.dominio.Aviso;
import co.nexoeventos.dominio.CambioServicio;
import co.nexoeventos.dominio.Compra;
import co.nexoeventos.dominio.Correo;
import co.nexoeventos.dominio.Entrada;
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Oferta;
import co.nexoeventos.dominio.Pago;
import co.nexoeventos.dominio.Reembolso;
import co.nexoeventos.dominio.Registro;
import co.nexoeventos.dominio.Solicitud;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.servicio.*;
import co.nexoeventos.servicio.ArchivoServicio.Tabla;

@RestController
@RequestMapping("/api")
public class ReporteControlador {
    private final SesionWeb web;
    private final ReporteServicio reportes;
    private final AuditoriaServicio auditoria;
    private final ArchivoServicio archivos;

    public ReporteControlador(SesionWeb web,ReporteServicio reportes,AuditoriaServicio auditoria,ArchivoServicio archivos) {
        this.web=web; this.reportes=reportes; this.auditoria=auditoria; this.archivos=archivos;
    }

    @GetMapping("/eventos/{id}/ventas")
    Map<String,Object> ventas(HttpServletRequest p,@PathVariable String id,@RequestParam(required=false) LocalDate desde,
        @RequestParam(required=false) LocalDate hasta,@RequestParam(required=false) String periodo) {
        return reportes.ventas(web.actor(p),id,desde,hasta,periodo);
    }
    @GetMapping("/reportes")
    ResponseEntity<byte[]> descargar(HttpServletRequest p,@RequestParam String tipo,@RequestParam String formato,
        @RequestParam(required=false) LocalDate desde,@RequestParam(required=false) LocalDate hasta,
        @RequestParam(required=false) String eventoId,@RequestParam(required=false) String proveedor,
        @RequestParam(required=false) String categoria,@RequestParam(required=false) String periodo) {
        Tabla tabla=reportes.construir(web.actor(p),tipo,desde,hasta,eventoId,proveedor,categoria,periodo);
        return switch (formato) {
            case "pdf" -> Descargas.archivo(archivos.pdf(tabla),"reporte-"+tipo+".pdf","application/pdf",false);
            case "xlsx" -> Descargas.archivo(archivos.excel(tabla),"reporte-"+tipo+".xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",false);
            default -> throw Regla.invalida("Formato de reporte inválido");
        };
    }
    @GetMapping("/auditoria")
    List<Registro> auditoria(HttpServletRequest p,@RequestParam(required=false) String usuario,
        @RequestParam(required=false) String accion,@RequestParam(required=false) LocalDate desde,
        @RequestParam(required=false) LocalDate hasta) {
        return auditoria.consultar(web.actor(p),usuario,accion,desde,hasta);
    }
    @GetMapping("/auditoria/archivo")
    ResponseEntity<byte[]> exportar(HttpServletRequest p,@RequestParam String formato,@RequestParam(required=false) String usuario,
        @RequestParam(required=false) String accion,@RequestParam(required=false) LocalDate desde,
        @RequestParam(required=false) LocalDate hasta) {
        List<List<?>> filas=new ArrayList<>();
        for (Registro r : auditoria.consultar(web.actor(p),usuario,accion,desde,hasta)) {
            filas.add(Arrays.asList(r.usuario(),r.accion(),r.fecha(),r.ip(),r.elemento(),r.resultado(),r.detalle()));
        }
        Tabla tabla=new Tabla("Registro de auditoría",List.of("Registros: "+filas.size()),
            List.of("Usuario","Acción","Fecha y hora","IP","Elemento","Resultado","Detalle"),filas,Map.of());
        return switch (formato) {
            case "pdf" -> Descargas.archivo(archivos.pdf(tabla),"auditoria.pdf","application/pdf",false);
            case "csv" -> Descargas.archivo(archivos.csv(tabla),"auditoria.csv","text/csv;charset=UTF-8",false);
            default -> throw Regla.invalida("Formato de auditoría inválido");
        };
    }
}
