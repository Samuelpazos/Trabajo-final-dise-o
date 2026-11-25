package co.nexoeventos.controlador;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.io.IOException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.EntradasApi.*;
import co.nexoeventos.servicio.*;
import co.nexoeventos.servicio.ArchivoServicio.Tabla;
import static co.nexoeventos.dominio.Valores.centavos;

@RestController
@RequestMapping("/api")
public class ProveedorControlador {
    private final SesionWeb web;
    private final ProveedorServicio proveedores;
    private final ArchivoServicio archivos;

    public ProveedorControlador(SesionWeb web,ProveedorServicio proveedores,ArchivoServicio archivos) {
        this.web=web; this.proveedores=proveedores; this.archivos=archivos;
    }

    @GetMapping("/proveedores")
    List<Map<String,Object>> catalogo(HttpServletRequest p,@RequestParam(required=false) String filtro,
        @RequestParam(required=false) String tipo,@RequestParam(required=false) BigDecimal minimo,
        @RequestParam(required=false) LocalDate fecha) {
        Long precio=null;
        if (minimo!=null) {
            try { precio=centavos(minimo); } catch (ArithmeticException error) { throw Regla.invalida("Importe inválido"); }
            if (precio<0) throw Regla.invalida("El importe debe ser positivo");
        }
        return proveedores.catalogo(web.actor(p),filtro,tipo,precio,fecha);
    }
    @GetMapping("/ofertas")
    List<Map<String,Object>> propias(HttpServletRequest p) { return proveedores.propias(web.actor(p)); }
    @PostMapping("/ofertas")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> crear(HttpServletRequest p,@Valid @RequestBody DatosOferta datos) {
        return proveedores.guardar(web.actor(p),null,datos);
    }
    @PutMapping("/ofertas/{id}")
    Map<String,Object> editar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody DatosOferta datos) {
        return proveedores.guardar(web.actor(p),id,datos);
    }
    @DeleteMapping("/ofertas/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eliminar(HttpServletRequest p,@PathVariable String id) { proveedores.eliminar(web.actor(p),id); }
    @GetMapping("/solicitudes")
    List<Map<String,Object>> solicitudes(HttpServletRequest p,@RequestParam(required=false) String estado,
        @RequestParam(required=false) String eventoId) { return proveedores.listar(web.actor(p),estado,eventoId); }
    @PostMapping("/solicitudes")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> solicitar(HttpServletRequest p,@Valid @RequestBody PedirServicio datos) {
        return proveedores.solicitar(web.actor(p),datos);
    }
    @GetMapping("/solicitudes/{id}")
    Map<String,Object> detalle(HttpServletRequest p,@PathVariable String id) { return proveedores.detalle(web.actor(p),id); }
    @PostMapping(value="/solicitudes/{id}/respuestas",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    Map<String,Object> responder(HttpServletRequest p,@PathVariable String id,@Valid @RequestPart Responder datos,
         @RequestPart(required=false) MultipartFile archivo) throws IOException {
        return proveedores.responder(web.actor(p),id,datos,archivo==null || archivo.isEmpty() ? null : archivo.getBytes());
    }
    @GetMapping("/solicitudes/{id}/adjuntos/{adjunto}")
    ResponseEntity<byte[]> adjunto(HttpServletRequest p,@PathVariable String id,@PathVariable String adjunto) {
        return Descargas.archivo(proveedores.adjunto(web.actor(p),id,adjunto),"presupuesto.pdf","application/pdf",false);
    }
    @GetMapping("/solicitudes/{id}/acuerdo")
    ResponseEntity<byte[]> acuerdo(HttpServletRequest p,@PathVariable String id) {
        Map<String,Object> s=proveedores.detalle(web.actor(p),id);
        if (!Set.of("CONFIRMADO","FINALIZADO").contains(s.get("estado").toString())) throw Regla.ausente();
        Tabla tabla=new Tabla("Acuerdo de servicio",List.of("Referencia: "+s.get("id"),"Evento: "+s.get("evento"),
            "Servicio: "+s.get("servicio"),"Proveedor: "+s.get("proveedor"),"Organizador: "+s.get("organizador"),
            "Monto acordado: "+s.get("monto")+" EUR","Estado: "+s.get("estado")),List.of(),List.of(),Map.of());
        return Descargas.archivo(archivos.pdf(tabla),"acuerdo.pdf","application/pdf",false);
    }
}
