package co.nexoeventos.controlador;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import co.nexoeventos.adaptador.PasarelaPago;
import co.nexoeventos.adaptador.PasarelaPago.Confirmacion;
import co.nexoeventos.adaptador.PasarelaDemostracion;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.QrEntrada;
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
import co.nexoeventos.dominio.EntradasApi.*;
import co.nexoeventos.servicio.*;
import co.nexoeventos.servicio.ArchivoServicio.Tabla;
import static co.nexoeventos.dominio.Valores.*;

@RestController
@RequestMapping("/api")
public class CompraControlador {
    private final SesionWeb web;
    private final CompraServicio compras;
    private final PasarelaPago pasarela;
    private final ArchivoServicio archivos;
    private final String nif;

    public CompraControlador(SesionWeb web,CompraServicio compras,PasarelaPago pasarela,ArchivoServicio archivos,
            @Value("${nexo.nif-emisor}") String nif) {
        this.web=web; this.compras=compras; this.pasarela=pasarela; this.archivos=archivos; this.nif=nif;
    }

    @PostMapping("/compras")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> reservar(HttpServletRequest p,@Valid @RequestBody Reserva datos) {
        return compras.reservar(web.actor(p),datos);
    }
    @GetMapping("/compras")
    List<Map<String,Object>> listar(HttpServletRequest p) { return compras.listar(web.actor(p)); }
    @GetMapping("/compras/{id}")
    Map<String,Object> detalle(HttpServletRequest p,@PathVariable String id) { return compras.detalle(web.actor(p),id); }
    @DeleteMapping("/compras/{id}/reserva")
    Map<String,Object> abandonar(HttpServletRequest p,@PathVariable String id) { return compras.abandonar(web.actor(p),id); }
    @PostMapping("/compras/{id}/cancelaciones")
    Map<String,Object> cancelar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody CancelarCompra datos) {
        return compras.cancelar(web.actor(p),id,datos);
    }
    @GetMapping("/reembolsos")
    List<Map<String,Object>> reembolsos(HttpServletRequest p) { return compras.reembolsos(web.actor(p)); }
    @PostMapping("/pasarela/webhook")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void webhook(@RequestBody String cuerpo,@RequestHeader(value="X-Nexo-Firma",required=false) String firma) {
        if (cuerpo.length()>4096) throw Regla.invalida("Confirmación demasiado grande");
        compras.confirmar(pasarela.verificar(cuerpo,firma));
    }
    @GetMapping("/compras/{id}/comprobante")
    ResponseEntity<byte[]> comprobante(HttpServletRequest p,@PathVariable String id) {
        Actor actor=web.actor(p);
        Compra c=compras.propia(actor,id);
        if (!CompraServicio.conPago(c)) throw Regla.ausente();
        String evento=(String)compras.detalle(actor,id).get("evento");
        Tabla tabla=new Tabla("Comprobante de compra",List.of("Compra: "+c.id,"Factura de demostración: FAC-"+c.id,
            "NIF emisor ficticio: "+nif,"Fecha de emisión: "+c.pago.fecha().atZone(ZONA),"Titular: "+actor.nombre(),
            "Concepto: entradas para "+evento,"Cantidad: "+c.cantidad,"Precio unitario: "+importe(c.precioCentavos)+" EUR",
            "Base imponible: "+importe(c.total())+" EUR","IVA de demostración: 0.00 EUR","Total: "+importe(c.total())+" EUR",
            "Método: "+c.pago.metodo(),"Estado: "+c.estado),List.of(),List.of(),Map.of());
        return Descargas.archivo(archivos.pdf(tabla),"comprobante-"+id+".pdf","application/pdf",false);
    }
    @GetMapping("/compras/{id}/qr/{codigo}")
    ResponseEntity<byte[]> qr(HttpServletRequest p,@PathVariable String id,@PathVariable String codigo) {
        Compra c=compras.propia(web.actor(p),id);
        if (c.entradas.stream().noneMatch(t -> t.codigo().equals(codigo) && t.estado()!=EstadoEntrada.CANCELADA)) throw Regla.ausente();
        return Descargas.archivo(archivos.qr(QrEntrada.generar(c,codigo)),"entrada-"+codigo.substring(0,8)+".png","image/png",true);
    }
}

@RestController
@Profile("demo")
@RequestMapping("/api/demo")
class PasarelaDemoControlador {
    private final SesionWeb web;
    private final CompraServicio compras;
    private final PasarelaDemostracion pasarela;

    PasarelaDemoControlador(SesionWeb web,CompraServicio compras,PasarelaDemostracion pasarela) {
        this.web=web; this.compras=compras; this.pasarela=pasarela;
    }
    @PostMapping("/compras/{id}/pago")
    Map<String,Object> pago(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody SimularPago datos) {
        Compra c=compras.propia(web.actor(p),id);
        return compras.confirmar(pasarela.simular(new Confirmacion("pago",c.referenciaPago,null,datos.resultado().name())));
    }
    @PostMapping("/compras/{id}/reembolsos/{reembolso}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reembolso(HttpServletRequest p,@PathVariable String id,@PathVariable String reembolso,
                   @Valid @RequestBody SimularReembolso datos) {
        Compra c=compras.reembolsoGestionado(web.actor(p),id);
        compras.confirmar(pasarela.simular(new Confirmacion("reembolso",c.referenciaPago,reembolso,datos.resultado().name())));
    }
}
