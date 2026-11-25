package co.nexoeventos.servicio;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Actividad;
import co.nexoeventos.dominio.Compra;
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Usuario;
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
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class EventoServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final UsuarioServicio usuarios;
    private final CompraServicio compras;
    private final NotificacionServicio avisos;
    private final AuditoriaServicio auditoria;
    private final Clock reloj;
    private final int plazoGeneral;

    public EventoServicio(Repositorio datos, Transacciones tx, UsuarioServicio usuarios, CompraServicio compras,
            NotificacionServicio avisos, AuditoriaServicio auditoria, Clock reloj,
            @Value("${nexo.plazo-cancelacion-horas}") int plazoGeneral) {
        this.datos = datos; this.tx = tx; this.usuarios = usuarios; this.compras = compras;
        this.avisos = avisos; this.auditoria = auditoria; this.reloj = reloj; this.plazoGeneral = plazoGeneral;
    }

    public static void gestion(Actor actor, Evento evento) {
        rol(actor, Rol.ADMINISTRADOR, Rol.ORGANIZADOR);
        if (actor.rol() == Rol.ORGANIZADOR && !actor.id().equals(evento.organizadorId)) throw Regla.acceso();
    }

    public static void propietario(Actor actor, Evento evento) {
        rol(actor,Rol.ORGANIZADOR);
        gestion(actor,evento);
    }

    public List<Evento> alcance(Actor actor) {
        rol(actor, Rol.ADMINISTRADOR, Rol.ORGANIZADOR);
        return actor.rol() == Rol.ADMINISTRADOR ? datos.listar(Evento.class)
            : datos.por(Evento.class, "organizadorId", actor.id());
    }

    public List<Map<String,Object>> gestionados(Actor actor, String estado, String filtro, String orden) {
        Comparator<Evento> comparador = switch (texto(orden)) {
            case "nombre" -> Comparator.comparing(e -> e.nombre, String.CASE_INSENSITIVE_ORDER);
            case "estado" -> Comparator.comparing(e -> e.estado);
            default -> Comparator.comparing(e -> e.inicio);
        };
        return alcance(actor).stream().filter(e -> coincide(e.nombre,filtro) &&
            (texto(estado).isEmpty() || e.estado.name().equals(estado)))
            .sorted(comparador).map(e -> vista(e, true, false)).toList();
    }

    public List<Map<String,Object>> catalogo(Actor actor, String nombre, String categoria, String lugar,
            LocalDate desde, LocalDate hasta, boolean soloFavoritos) {
        Set<String> favoritos = new HashSet<>(datos.exigir(Usuario.class, actor.id()).favoritos);
        return datos.listar(Evento.class).stream().filter(e -> soloFavoritos ? favoritos.contains(e.id) : visible(e))
            .filter(e -> coincide(e.nombre,nombre) && coincide(e.lugar,lugar))
            .filter(e -> texto(categoria).isEmpty() || e.categoria.equalsIgnoreCase(categoria))
            .filter(e -> periodo(e.inicio,desde,hasta)).sorted(Comparator.comparing(e -> e.inicio))
            .map(e -> vista(e,false,favoritos.contains(e.id))).toList();
    }

    public Map<String,Object> detalle(Actor actor, String id) {
        Evento evento = datos.exigir(Evento.class,id);
        boolean gestion = actor.rol() == Rol.ADMINISTRADOR ||
            (actor.rol() == Rol.ORGANIZADOR && actor.id().equals(evento.organizadorId));
        boolean favorito = datos.exigir(Usuario.class,actor.id()).favoritos.contains(id);
        boolean comprado = datos.por(Compra.class,"asistenteId",actor.id()).stream()
            .anyMatch(c -> id.equals(c.eventoId) && CompraServicio.conPago(c));
        if (!visible(evento) && !gestion && !favorito && !comprado) throw Regla.ausente();
        return vista(evento,gestion,favorito);
    }

    private boolean visible(Evento evento) {
        return evento.estado == EstadoEvento.ACTIVO && evento.fin.isAfter(reloj.instant());
    }

    public Map<String,Object> vista(Evento e, boolean gestion, boolean favorito) {
        Map<String,Object> vista = datos("id",e.id,"nombre",e.nombre,"descripcion",e.descripcion,
            "lugar",e.lugar,"categoria",e.categoria,"inicio",e.inicio,"fin",e.fin,"capacidad",e.capacidad,
            "precio",importe(e.precioCentavos),"estado",e.estado,"disponibles",e.disponibles(),
            "organizador",usuarios.nombre(e.organizadorId),"actividades",e.actividades,
            "motivoCancelacion",e.motivoCancelacion,"favorito",favorito,
            "comprable",visible(e) && reloj.instant().isBefore(e.inicio) && e.disponibles()>0,"gestion",gestion);
        if (gestion) {
            vista.put("vendidas",e.vendidas);
            vista.put("reservadas",e.reservadas);
            vista.put("plazoHoras",e.plazoHoras);
            vista.put("plazoGeneral",plazoGeneral);
        }
        return vista;
    }

    public Map<String,Object> guardar(Actor actor, String eventoId, DatosEvento entrada) {
        if (eventoId == null) rol(actor,Rol.ORGANIZADOR);
        return tx.ejecutar(() -> {
            Evento e = eventoId == null ? new Evento() : datos.exigir(Evento.class,eventoId);
            if (eventoId != null) propietario(actor,e);
            boolean fechaCambia = !Objects.equals(entrada.inicio(),e.inicio) || !Objects.equals(entrada.fin(),e.fin);
            editable(e);
            if (fechaCambia && !entrada.inicio().isAfter(reloj.instant())) throw Regla.invalida("La fecha del evento debe ser en el futuro");
            if (!entrada.fin().isAfter(entrada.inicio())) throw Regla.invalida("El final debe ser posterior al inicio");
            if (entrada.capacidad() < e.reservadas + e.vendidas) throw Regla.conflicto("La capacidad es inferior a los cupos comprometidos");
            e.actividades.forEach(a -> validarActividad(a,entrada.inicio(),entrada.fin(),entrada.capacidad()));
            if (eventoId == null) {
                e.id = id();
                e.creado = reloj.instant();
                e.organizadorId = actor.id();
            }
            e.nombre = texto(entrada.nombre()); e.descripcion = texto(entrada.descripcion()); e.lugar = texto(entrada.lugar());
            e.categoria = texto(entrada.categoria()); e.inicio = entrada.inicio(); e.fin = entrada.fin();
            e.capacidad = entrada.capacidad(); e.precioCentavos = centavos(entrada.precio()); e.plazoHoras = entrada.plazoHoras();
            datos.guardar(e);
            auditoria.exito(actor,eventoId == null ? "EVENTO_CREADO" : "EVENTO_EDITADO",e.id,e.nombre);
            Map<String,Object> salida = vista(e,true,false);
            if (eventoId != null && fechaCambia && e.vendidas>0) {
                compradores(e.id).forEach(u -> avisos.enviar(u,id(),"Cambio de horario",
                    e.nombre + " se realizará el " + e.inicio.atZone(ZONA),null,List.of("/#evento/" + e.id)));
                salida.put("aviso","Los asistentes recibirán notificación del cambio");
            }
            return salida;
        });
    }

    public Map<String,Object> estado(Actor actor, String eventoId, Estado entrada) {
        if (!entrada.confirmar()) throw Regla.invalida("Debes confirmar el cambio");
        return tx.ejecutar(() -> {
            Evento e = datos.exigir(Evento.class,eventoId);
            propietario(actor,e);
            editable(e);
            if (!e.inicio.isAfter(reloj.instant())) throw Regla.conflicto("El evento ya comenzó");
            if ("ACTIVO".equals(entrada.estado())) e.publicar();
            else if ("INACTIVO".equals(entrada.estado()) && e.estado == EstadoEvento.ACTIVO) e.ocultar();
            else throw Regla.invalida("Transición de evento inválida");
            datos.guardar(e);
            auditoria.exito(actor,"EVENTO_" + e.estado,e.id,"");
            Map<String,Object> salida = vista(e,true,false);
            if (e.estado == EstadoEvento.INACTIVO && e.vendidas>0)
                salida.put("aviso","Los asistentes no podrán comprar más entradas. Las entradas emitidas siguen válidas.");
            return salida;
        });
    }

    public Map<String,Object> cancelar(Actor actor, String eventoId, CancelarEvento entrada) {
        return tx.ejecutar(() -> {
            Evento e = datos.exigir(Evento.class,eventoId);
            propietario(actor,e);
            if (e.estado == EstadoEvento.CANCELADO) return vista(e,true,false);
            if (!reloj.instant().isBefore(e.inicio)) throw Regla.conflicto("El evento ya ha comenzado y no puede cancelarse");
            if (!entrada.confirmar() || texto(entrada.motivo()).isEmpty()) throw Regla.invalida("Confirma e ingresa el motivo");
            e.cancelar(texto(entrada.motivo()), reloj.instant());
            CompraServicio.ResumenCancelacion resumen=compras.cancelarPorEvento(actor,e);
            datos.guardar(e);
            auditoria.exito(actor,"EVENTO_CANCELADO",e.id,e.motivoCancelacion+" · Entradas: "+resumen.entradas()
                +" · Reembolso total: "+importe(resumen.centavos())+" EUR");
            return vista(e,true,false);
        });
    }

    public Map<String,Object> actividad(Actor actor,String eventoId,DatosActividad entrada) {
        return tx.ejecutar(() -> {
            Evento e = datos.exigir(Evento.class,eventoId);
            propietario(actor,e);
            editable(e);
            String lugar = texto(entrada.lugar()).isEmpty() ? e.lugar : texto(entrada.lugar());
            Actividad a = new Actividad(id(),texto(entrada.nombre()),texto(entrada.descripcion()),lugar,entrada.inicio(),
                entrada.minutos(),texto(entrada.responsable()),entrada.capacidad());
            validarActividad(a,e.inicio,e.fin,e.capacidad);
            e.actividades.add(a);
            e.actividades.sort(Comparator.comparing(Actividad::inicio));
            datos.guardar(e);
            auditoria.exito(actor,"ACTIVIDAD_CREADA",e.id,a.nombre());
            compradores(e.id).forEach(u -> avisos.enviar(u,a.id(),"Programación actualizada",
                "Se agregó " + a.nombre() + " a " + e.nombre,Tema.PROGRAMACION,List.of("/#evento/" + e.id)));
            return vista(e,true,false);
        });
    }

    private void validarActividad(Actividad a, Instant inicio, Instant fin, int capacidad) {
        if (a.capacidad()>capacidad) throw Regla.invalida("La capacidad no puede exceder la del evento (" + capacidad + ")");
        if (a.inicio().isBefore(inicio) || a.inicio().plusSeconds(a.minutos()*60L).isAfter(fin))
            throw Regla.invalida("La actividad debe estar dentro del horario del evento");
    }

    private void editable(Evento e) {
        if (e.estado == EstadoEvento.CANCELADO || e.estado == EstadoEvento.FINALIZADO) throw Regla.conflicto("El evento está cerrado");
    }

    public void favorito(Actor actor,String eventoId,boolean agregar) {
        rol(actor,Rol.ASISTENTE);
        tx.ejecutar(() -> {
            Usuario u = datos.exigir(Usuario.class,actor.id());
            if (agregar) {
                if (!visible(datos.exigir(Evento.class,eventoId))) throw Regla.ausente();
                if (!u.favoritos.contains(eventoId)) u.favoritos.add(eventoId);
            } else u.favoritos.remove(eventoId);
            datos.guardar(u);
            return null;
        });
    }

    private Set<String> compradores(String eventoId) {
        Set<String> ids = new HashSet<>();
        datos.por(Compra.class,"eventoId",eventoId).stream()
            .filter(c -> c.entradas.stream().anyMatch(e -> e.estado()!=EstadoEntrada.CANCELADA))
            .forEach(c -> ids.add(c.asistenteId));
        return ids;
    }
}
