package co.nexoeventos.servicio;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.Dominio.*;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Adjunto;
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
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Oferta;
import co.nexoeventos.dominio.Solicitud;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.dominio.EntradasApi.*;
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class ProveedorServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final NotificacionServicio avisos;
    private final AuditoriaServicio auditoria;
    private final UsuarioServicio usuarios;
    private final Clock reloj;

    public ProveedorServicio(Repositorio datos,Transacciones tx,NotificacionServicio avisos,
                             AuditoriaServicio auditoria,UsuarioServicio usuarios,Clock reloj) {
        this.datos=datos; this.tx=tx; this.avisos=avisos; this.auditoria=auditoria; this.usuarios=usuarios; this.reloj=reloj;
    }

    public List<Map<String,Object>> propias(Actor actor) {
        rol(actor,Rol.PROVEEDOR);
        return datos.por(Oferta.class,"proveedorId",actor.id()).stream().map(this::vistaOferta).toList();
    }

    public Map<String,Object> guardar(Actor actor,String ofertaId,DatosOferta entrada) {
        rol(actor,Rol.PROVEEDOR);
        if (entrada.hasta().isBefore(entrada.desde())) throw Regla.invalida("La disponibilidad termina antes de comenzar");
        return tx.ejecutar(() -> {
            Oferta o=ofertaId==null ? new Oferta() : propia(actor,ofertaId);
            if (ofertaId==null) { o.id=id(); o.proveedorId=actor.id(); }
            o.nombre=texto(entrada.nombre()); o.descripcion=texto(entrada.descripcion()); o.tipo=texto(entrada.tipo());
            o.precioCentavos=centavos(entrada.precio()); o.desde=entrada.desde().toString(); o.hasta=entrada.hasta().toString();
            o.disponible=entrada.disponible();
            datos.guardar(o);
            auditoria.exito(actor,ofertaId==null ? "SERVICIO_CREADO" : "SERVICIO_EDITADO",o.id,o.nombre);
            return vistaOferta(o);
        });
    }

    public void eliminar(Actor actor,String ofertaId) {
        rol(actor,Rol.PROVEEDOR);
        tx.ejecutar(() -> {
            Oferta o=propia(actor,ofertaId);
            if (!datos.por(Solicitud.class,"ofertaId",o.id).isEmpty())
                throw Regla.conflicto("El servicio tiene solicitudes asociadas. Puedes desactivarlo.");
            datos.eliminar(o);
            auditoria.exito(actor,"SERVICIO_ELIMINADO",o.id,o.nombre);
            return null;
        });
    }

    private Oferta propia(Actor actor,String id) {
        Oferta o=datos.exigir(Oferta.class,id);
        if (!actor.id().equals(o.proveedorId)) throw Regla.acceso();
        return o;
    }

    public List<Map<String,Object>> catalogo(Actor actor,String filtro,String tipo,Long minimo,LocalDate fecha) {
        rol(actor,Rol.ORGANIZADOR,Rol.ADMINISTRADOR,Rol.ASISTENTE,Rol.PROVEEDOR);
        Map<String,Usuario> proveedores=new HashMap<>();
        datos.por(Usuario.class,"rol",Rol.PROVEEDOR).stream().filter(u -> u.estado==EstadoCuenta.ACTIVO)
            .forEach(u -> proveedores.put(u.id,u));
        Map<String,List<Oferta>> agrupadas=new LinkedHashMap<>();
        LocalDate hoy=LocalDate.ofInstant(reloj.instant(),ZONA);
        for (Oferta o : datos.por(Oferta.class,"disponible",true)) {
            Usuario proveedor=proveedores.get(o.proveedorId);
            if (proveedor==null || LocalDate.parse(o.hasta).isBefore(hoy)) continue;
            if (!coincide(proveedor.nombre,filtro) && !coincide(o.nombre,filtro) && !coincide(o.descripcion,filtro)) continue;
            if (!coincide(o.tipo,tipo) || (minimo!=null && o.precioCentavos<minimo)) continue;
            if (fecha!=null && !disponible(o,fecha)) continue;
            agrupadas.computeIfAbsent(o.proveedorId,k -> new ArrayList<>()).add(o);
        }
        return agrupadas.entrySet().stream().sorted(Comparator.comparing(e -> proveedores.get(e.getKey()).nombre))
            .map(e -> datos("id",e.getKey(),"nombre",proveedores.get(e.getKey()).nombre,"cantidad",e.getValue().size(),
                "tipos",e.getValue().stream().map(o -> o.tipo).distinct().toList(),
                "servicios",e.getValue().stream().map(this::vistaOferta).toList())).toList();
    }

    private boolean disponible(Oferta o,LocalDate fecha) {
        return o.disponible && !fecha.isBefore(LocalDate.parse(o.desde)) && !fecha.isAfter(LocalDate.parse(o.hasta));
    }

    private Map<String,Object> vistaOferta(Oferta o) {
        return datos("id",o.id,"proveedorId",o.proveedorId,"nombre",o.nombre,"descripcion",o.descripcion,
            "tipo",o.tipo,"precio",importe(o.precioCentavos),"desde",o.desde,"hasta",o.hasta,"disponible",o.disponible);
    }

    public Map<String,Object> solicitar(Actor actor,PedirServicio entrada) {
        rol(actor,Rol.ORGANIZADOR);
        return tx.ejecutar(() -> {
            Oferta o=datos.exigir(Oferta.class,entrada.ofertaId());
            Evento e=datos.exigir(Evento.class,entrada.eventoId());
            EventoServicio.gestion(actor,e);
            if (e.estado==EstadoEvento.CANCELADO || !e.inicio.isAfter(reloj.instant()))
                throw Regla.conflicto("El evento está cerrado o ya comenzó");
            Usuario proveedor=datos.exigir(Usuario.class,o.proveedorId);
            if (proveedor.estado!=EstadoCuenta.ACTIVO || proveedor.rol!=Rol.PROVEEDOR ||
                !disponible(o,LocalDate.ofInstant(e.inicio,ZONA))) throw Regla.conflicto("El servicio no está disponible en esa fecha");
            // Serializa la solicitud con la eliminación o desactivación concurrente de la oferta.
            datos.guardar(o);
            Solicitud s=new Solicitud();
            s.id=id(); s.ofertaId=o.id; s.proveedorId=o.proveedorId; s.organizadorId=actor.id();
            s.eventoId=e.id; s.servicio=o.nombre; s.observaciones=texto(entrada.observaciones()); s.creada=reloj.instant();
            s.historial.add(new CambioServicio(s.creada,actor.id(),s.estado,null,s.observaciones,null));
            datos.insertar(s);
            auditoria.exito(actor,"SERVICIO_SOLICITADO",s.id,s.servicio);
            avisos.enviar(s.proveedorId,s.id,"Nueva solicitud · "+s.servicio,"Evento: "+e.nombre+
                ". "+s.observaciones,null,List.of("/#solicitud/"+s.id));
            return vistaSolicitud(s);
        });
    }

    public List<Map<String,Object>> listar(Actor actor,String estado,String eventoId) {
        rol(actor,Rol.ORGANIZADOR,Rol.PROVEEDOR);
        return datos.por(Solicitud.class,actor.rol()==Rol.PROVEEDOR ? "proveedorId" : "organizadorId",actor.id())
            .stream().filter(s -> texto(estado).isEmpty() || s.estado.name().equals(estado))
            .filter(s -> texto(eventoId).isEmpty() || s.eventoId.equals(eventoId))
            .sorted(Comparator.comparing((Solicitud s) -> s.creada).reversed()).map(this::vistaSolicitud).toList();
    }

    public Map<String,Object> detalle(Actor actor,String id) { return vistaSolicitud(autorizada(actor,id)); }

    private Solicitud autorizada(Actor actor,String id) {
        rol(actor,Rol.ORGANIZADOR,Rol.PROVEEDOR);
        Solicitud s=datos.exigir(Solicitud.class,id);
        if (!actor.id().equals(actor.rol()==Rol.PROVEEDOR ? s.proveedorId : s.organizadorId)) throw Regla.acceso();
        return s;
    }

    public Map<String,Object> responder(Actor actor,String id,Responder entrada,byte[] pdf) {
        return tx.ejecutar(() -> {
            Solicitud s=autorizada(actor,id);
            EstadoSolicitud siguiente=entrada.estado();
            boolean proveedor=actor.rol()==Rol.PROVEEDOR;
            boolean permitido=switch(s.estado) {
                case SOLICITADO -> proveedor && EnumSet.of(EstadoSolicitud.PRESUPUESTADO,EstadoSolicitud.CONFIRMADO,
                    EstadoSolicitud.RECHAZADO).contains(siguiente);
                case PRESUPUESTADO -> proveedor ? EnumSet.of(EstadoSolicitud.PRESUPUESTADO,EstadoSolicitud.RECHAZADO).contains(siguiente)
                    : EnumSet.of(EstadoSolicitud.CONTRAPROPUESTA,EstadoSolicitud.CONFIRMADO,EstadoSolicitud.RECHAZADO).contains(siguiente);
                case CONTRAPROPUESTA -> proveedor && EnumSet.of(EstadoSolicitud.PRESUPUESTADO,EstadoSolicitud.CONFIRMADO,
                    EstadoSolicitud.RECHAZADO).contains(siguiente);
                case CONFIRMADO -> siguiente==EstadoSolicitud.FINALIZADO;
                case RECHAZADO, FINALIZADO -> false;
            };
            if (!permitido) throw Regla.conflicto("La solicitud no admite esa transición");
            boolean oferta=siguiente==EstadoSolicitud.PRESUPUESTADO || siguiente==EstadoSolicitud.CONTRAPROPUESTA;
            if (oferta && entrada.monto()==null) throw Regla.invalida("Indica el monto de la propuesta");
            String adjunto=null;
            if (pdf!=null) {
                if (siguiente!=EstadoSolicitud.PRESUPUESTADO || pdf.length<5 || pdf.length>2_000_000 ||
                    !new String(pdf,0,5,StandardCharsets.US_ASCII).equals("%PDF-")) throw Regla.invalida("Adjunta un PDF de hasta 2 MB");
                adjunto=id();
                datos.insertar(new Adjunto(adjunto,s.id,pdf));
            }
            if (oferta) s.montoCentavos=centavos(entrada.monto());
            if (siguiente==EstadoSolicitud.CONFIRMADO && s.montoCentavos==null)
                s.montoCentavos=datos.exigir(Oferta.class,s.ofertaId).precioCentavos;
            s.estado=siguiente;
            Instant fecha=reloj.instant();
            if (siguiente==EstadoSolicitud.FINALIZADO) s.finalizada=fecha;
            s.historial.add(new CambioServicio(fecha,actor.id(),siguiente,s.montoCentavos,texto(entrada.comentario()),adjunto));
            datos.guardar(s);
            auditoria.exito(actor,"SOLICITUD_"+siguiente,s.id,s.servicio);
            String otro=proveedor ? s.organizadorId : s.proveedorId;
            boolean confirmado=siguiente==EstadoSolicitud.CONFIRMADO;
            List<String> enlaces=confirmado ? List.of("/#solicitud/"+s.id,"/api/solicitudes/"+s.id+"/acuerdo")
                : List.of("/#solicitud/"+s.id);
            String mensaje="Servicio "+s.servicio+" · "+siguiente+". "+texto(entrada.comentario());
            avisos.enviar(otro,id(),"Solicitud actualizada",mensaje,
                proveedor && siguiente==EstadoSolicitud.PRESUPUESTADO ? Tema.PRESUPUESTOS : null,enlaces);
            if (confirmado) avisos.enviar(actor.id(),id(),"Acuerdo de servicio confirmado",mensaje,null,enlaces);
            return vistaSolicitud(s);
        });
    }

    public byte[] adjunto(Actor actor,String id,String adjuntoId) {
        autorizada(actor,id);
        Adjunto adjunto=datos.exigir(Adjunto.class,adjuntoId);
        if (!id.equals(adjunto.solicitudId())) throw Regla.ausente();
        return adjunto.contenido();
    }

    private Map<String,Object> vistaSolicitud(Solicitud s) {
        Evento evento=datos.exigir(Evento.class,s.eventoId);
        return datos("id",s.id,"eventoId",s.eventoId,"evento",evento.nombre,"fechaEvento",evento.inicio,
            "lugar",evento.lugar,"servicio",s.servicio,"proveedor",usuarios.nombre(s.proveedorId),
            "organizador",usuarios.nombre(s.organizadorId),"observaciones",s.observaciones,
            "creada",s.creada,"finalizada",s.finalizada,"estado",s.estado,
            "monto",s.montoCentavos==null ? null : importe(s.montoCentavos),
            "historial",s.historial.stream().map(c -> datos("fecha",c.fecha(),"actor",usuarios.nombre(c.actorId()),
            "estado",c.estado(),"comentario",c.comentario(),"adjuntoId",c.adjuntoId(),
            "monto",c.montoCentavos()==null ? null : importe(c.montoCentavos()))).toList());
    }
}
