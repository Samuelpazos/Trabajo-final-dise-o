package co.nexoeventos.servicio;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import co.nexoeventos.adaptador.PasarelaPago;
import co.nexoeventos.adaptador.PasarelaPago.Confirmacion;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Compra;
import co.nexoeventos.dominio.Entrada;
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Pago;
import co.nexoeventos.dominio.Reembolso;
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
public class CompraServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final PasarelaPago pasarela;
    private final NotificacionServicio avisos;
    private final AuditoriaServicio auditoria;
    private final Clock reloj;
    private final int plazo, minutos;

    public CompraServicio(Repositorio datos, Transacciones tx, PasarelaPago pasarela, NotificacionServicio avisos,
             AuditoriaServicio auditoria, Clock reloj, @Value("${nexo.plazo-cancelacion-horas}") int plazo,
             @Value("${nexo.reserva-minutos}") int minutos) {
        this.datos=datos; this.tx=tx; this.pasarela=pasarela; this.avisos=avisos; this.auditoria=auditoria;
        this.reloj=reloj; this.plazo=plazo; this.minutos=minutos;
    }

    public Map<String,Object> reservar(Actor actor, Reserva reserva) {
        rol(actor,Rol.ASISTENTE);
        return tx.ejecutar(() -> {
            Evento evento = datos.exigir(Evento.class,reserva.eventoId());
            Instant ahora = reloj.instant();
            Compra previa = datos.listar(Compra.class,Criteria.where("asistenteId").is(actor.id()),
                Criteria.where("solicitudId").is(reserva.solicitudId())).stream().findFirst().orElse(null);
            if (previa != null) {
                if (previa.cantidad != reserva.cantidad() || !previa.eventoId.equals(evento.id))
                    throw Regla.conflicto("La referencia ya se usó en otra reserva");
                return vista(previa,evento);
            }
            if (evento.estado != EstadoEvento.ACTIVO || !ahora.isBefore(evento.inicio))
                throw Regla.conflicto("El evento no admite nuevas compras");
            for (Compra compra : datos.listar(Compra.class,Criteria.where("eventoId").is(evento.id),
                    Criteria.where("estado").is(EstadoCompra.RESERVADA),Criteria.where("vence").lte(ahora)))
                liberar(compra,evento,EstadoCompra.EXPIRADA);
            if (reserva.cantidad()<1) throw Regla.invalida("Debes seleccionar al menos 1 entrada");
            if (reserva.cantidad()>evento.disponibles())
                throw Regla.conflicto("Solo hay " + evento.disponibles() + " entradas disponibles");
            Compra compra = new Compra();
            compra.id=id(); compra.eventoId=evento.id; compra.asistenteId=actor.id();
            compra.solicitudId=reserva.solicitudId(); compra.creada=ahora;
            compra.vence=ahora.plusSeconds(minutos*60L);
            if (compra.vence.isAfter(evento.inicio)) compra.vence=evento.inicio;
            compra.cantidad=reserva.cantidad(); compra.precioCentavos=evento.precioCentavos;
            compra.referenciaPago=pasarela.iniciarPago(compra.id,compra.total());
            evento.reservar(compra.cantidad);
            datos.guardar(evento);
            datos.insertar(compra);
            auditoria.exito(actor,"COMPRA_RESERVADA",compra.id,"Entradas: " + compra.cantidad);
            return vista(compra,evento);
        });
    }

    private void liberar(Compra c, Evento e, EstadoCompra estado) {
        e.liberar(c.cantidad);
        c.cambiarEstado(estado);
        datos.guardar(c);
    }

    public Map<String,Object> abandonar(Actor actor,String id) {
        return tx.ejecutar(() -> {
            Compra c=propia(actor,id);
            Evento e=datos.exigir(Evento.class,c.eventoId);
            if (c.estado==EstadoCompra.RESERVADA) {
                liberar(c,e,EstadoCompra.ABANDONADA);
                datos.guardar(e);
            }
            return vista(c,e);
        });
    }

    public Map<String,Object> confirmar(Confirmacion confirmacion) {
        if (confirmacion.referencia()==null || confirmacion.resultado()==null)
            throw Regla.invalida("Confirmación incompleta");
        return tx.ejecutar(() -> {
            Compra c=datos.uno(Compra.class,"referenciaPago",confirmacion.referencia()).orElseThrow(Regla::ausente);
            Evento e=datos.exigir(Evento.class,c.eventoId);
            try {
                switch (texto(confirmacion.tipo())) {
                    case "pago" -> confirmarPago(c,e,ResultadoPago.valueOf(confirmacion.resultado()));
                    case "reembolso" -> confirmarReembolso(c,confirmacion.reembolsoId(),EstadoReembolso.valueOf(confirmacion.resultado()));
                    default -> throw Regla.invalida("Tipo de confirmación inválido");
                }
            } catch (IllegalArgumentException error) {
                throw Regla.invalida("Resultado de pasarela inválido");
            }
            return vista(c,e);
        });
    }

    private void confirmarPago(Compra c, Evento e, ResultadoPago resultado) {
        if (c.estado!=EstadoCompra.RESERVADA) return;
        Instant ahora=reloj.instant();
        if (!ahora.isBefore(c.vence) || e.estado==EstadoEvento.CANCELADO) resultado=ResultadoPago.TIMEOUT;
        c.pago=new Pago(c.referenciaPago,resultado,ahora,"Tarjeta demo •••• 1234");
        if (resultado==ResultadoPago.TIMEOUT) liberar(c,e,EstadoCompra.EXPIRADA);
        else if (resultado!=ResultadoPago.APROBADO) liberar(c,e,EstadoCompra.RECHAZADA);
        else {
            e.liberar(c.cantidad);
            e.vendidas+=c.cantidad;
            c.confirmarPago(c.pago);
            c.codigos=new ArrayList<>();
            for (int i=0;i<c.cantidad;i++) {
                String codigo=secreto();
                c.codigos.add(codigo);
                c.entradas.add(new Entrada(codigo,EstadoEntrada.VALIDA,null));
            }
            datos.guardar(c);
            avisos.enviar(c.asistenteId,c.id+":PAGO","Compra confirmada · "+e.nombre,
                "Compra "+c.id+". Entradas: "+c.cantidad+". Total: "+importe(c.total())+" EUR. "
                +"Descarga el comprobante y los códigos QR desde el detalle.",null,
                List.of("/#compra/"+c.id,"/api/compras/"+c.id+"/comprobante"));
        }
        datos.guardar(e);
        auditoria.registrar(null,"PAGO_CONFIRMADO",c.id,c.estado.name(),resultado.name());
        if (resultado==ResultadoPago.APROBADO) {
            Actor comprador=datos.id(Usuario.class,c.asistenteId).map(UsuarioServicio::actor).orElse(null);
            auditoria.exito(comprador,"COMPRA_REALIZADA",e.id,"Compra: "+c.id+", entradas: "+c.cantidad+
                ", monto: "+importe(c.total())+" EUR, método: "+c.pago.metodo());
        }
    }

    public Map<String,Object> cancelar(Actor actor,String id,CancelarCompra entrada) {
        return tx.ejecutar(() -> {
            Compra c=propia(actor,id);
            Evento e=datos.exigir(Evento.class,c.eventoId);
            Set<String> codigos=new LinkedHashSet<>(entrada.entradas());
            if (!entrada.confirmar() || codigos.isEmpty()) throw Regla.invalida("Selecciona y confirma las entradas que deseas cancelar");
            if (c.reembolsos.stream().anyMatch(r -> new HashSet<>(r.entradas()).equals(codigos))) return vista(c,e);
            Instant ahora=reloj.instant();
            if (!ahora.isBefore(e.inicio)) throw Regla.conflicto("El evento ya ha comenzado. No se puede cancelar");
            if (ahora.isAfter(limite(e))) throw Regla.conflicto("El plazo de cancelación expiró el "+
                limite(e).atZone(ZONA)+". Contacta al organizador para solicitudes especiales");
            if (e.estado==EstadoEvento.CANCELADO || !conPago(c)) throw Regla.conflicto("La compra no se puede cancelar");
            cancelarEntradas(actor,c,e,codigos,"Cancelación solicitada por el asistente",false);
            datos.guardar(e);
            return vista(c,e);
        });
    }

    public record ResumenCancelacion(int entradas,long centavos) {}

    public ResumenCancelacion cancelarPorEvento(Actor actor, Evento e) {
        int entradas=0;
        long centavos=0;
        for (Compra c : datos.por(Compra.class,"eventoId",e.id)) {
            if (c.estado==EstadoCompra.RESERVADA) liberar(c,e,EstadoCompra.ABANDONADA);
            else if (conPago(c)) {
                Set<String> activas=new LinkedHashSet<>();
                c.entradas.stream().filter(t -> t.estado()!=EstadoEntrada.CANCELADA).forEach(t -> activas.add(t.codigo()));
                if (activas.isEmpty()) continue;
                avisos.enviar(c.asistenteId,c.id+":EVENTO_CANCELADO","Evento cancelado · "+e.nombre,
                    e.motivoCancelacion,null,List.of("/#compra/"+c.id));
                cancelarEntradas(actor,c,e,activas,"Evento cancelado: "+e.motivoCancelacion,true);
                entradas+=activas.size();
                centavos+=c.precioCentavos*activas.size();
            }
        }
        return new ResumenCancelacion(entradas,centavos);
    }

    private void cancelarEntradas(Actor actor,Compra c,Evento e,Set<String> codigos,String motivo,boolean forzada) {
        List<Entrada> seleccion=c.entradas.stream().filter(t -> codigos.contains(t.codigo())).toList();
        if (seleccion.size()!=codigos.size() || seleccion.stream().anyMatch(t ->
                t.estado()==EstadoEntrada.CANCELADA || (!forzada && t.estado()!=EstadoEntrada.VALIDA)))
            throw Regla.conflicto("Alguna entrada no pertenece a esta compra, ya se canceló o fue utilizada");
        c.entradas.replaceAll(t -> codigos.contains(t.codigo()) ? new Entrada(t.codigo(),EstadoEntrada.CANCELADA,t.usada()) : t);
        e.vendidas-=seleccion.size();
        c.cambiarEstado(c.entradas.stream().allMatch(t -> t.estado()==EstadoEntrada.CANCELADA)
            ? EstadoCompra.CANCELADA : EstadoCompra.CANCELADA_PARCIAL);
        Reembolso r=new Reembolso(id(),c.precioCentavos*seleccion.size(),motivo,List.copyOf(codigos),
            EstadoReembolso.INICIADO,reloj.instant(),null);
        c.reembolsos.add(r);
        datos.guardar(c);
        pasarela.iniciarReembolso(c.referenciaPago,r);
        auditoria.exito(actor,"COMPRA_CANCELADA",c.id,"Entradas: "+seleccion.size()+". "+motivo);
        auditoria.exito(null,"REEMBOLSO_INICIADO",r.id(),"Importe: "+importe(r.montoCentavos()));
        avisos.enviar(c.asistenteId,r.id()+":INICIADO","Cancelación confirmada · reembolso iniciado",
            "Compra "+c.id+". "+motivo+". Entradas canceladas: "+seleccion.size()+". Importe: "+importe(r.montoCentavos())+
            " EUR. Referencia: "+r.id()+". Acreditación estimada: 3–10 días bancarios.",null,List.of("/#compra/"+c.id));
    }

    private void confirmarReembolso(Compra c,String id,EstadoReembolso resultado) {
        if (resultado!=EstadoReembolso.COMPLETADO && resultado!=EstadoReembolso.FALLIDO)
            throw Regla.invalida("Resultado de reembolso inválido");
        for (int i=0;i<c.reembolsos.size();i++) {
            Reembolso anterior=c.reembolsos.get(i);
            if (!anterior.id().equals(id)) continue;
            if (anterior.estado()==EstadoReembolso.COMPLETADO || anterior.estado()==EstadoReembolso.FALLIDO) return;
            Instant acreditado=resultado==EstadoReembolso.COMPLETADO ? reloj.instant() : null;
            c.reembolsos.set(i,new Reembolso(id,anterior.montoCentavos(),anterior.motivo(),anterior.entradas(),
                resultado,anterior.iniciado(),acreditado));
            if (c.estado==EstadoCompra.CANCELADA && c.reembolsos.stream().allMatch(r -> r.estado()==EstadoReembolso.COMPLETADO))
                c.estado=EstadoCompra.REEMBOLSADA;
            datos.guardar(c);
            auditoria.registrar(null,"REEMBOLSO_"+resultado,id,resultado.name(),c.id);
            String mensaje=acreditado!=null ? "Tu reembolso de "+importe(anterior.montoCentavos())+
                " EUR ha sido acreditado exitosamente." : "No pudimos procesar tu reembolso. Contacta al soporte.";
            avisos.enviar(c.asistenteId,id+":"+resultado,"Reembolso "+resultado.name().toLowerCase(Locale.ROOT),
                mensaje+" Referencia: "+id,null,List.of("/#compra/"+c.id));
            return;
        }
        throw Regla.ausente();
    }

    public Compra propia(Actor actor,String id) {
        rol(actor,Rol.ASISTENTE);
        Compra c=datos.exigir(Compra.class,id);
        if (!actor.id().equals(c.asistenteId)) throw Regla.acceso();
        return c;
    }

    public Compra reembolsoGestionado(Actor actor,String id) {
        Compra c=datos.exigir(Compra.class,id);
        EventoServicio.gestion(actor,datos.exigir(Evento.class,c.eventoId));
        return c;
    }

    public Map<String,Object> detalle(Actor actor,String id) {
        Compra c=propia(actor,id);
        return vista(c,datos.exigir(Evento.class,c.eventoId));
    }

    public List<Map<String,Object>> listar(Actor actor) {
        rol(actor,Rol.ASISTENTE);
        return datos.por(Compra.class,"asistenteId",actor.id()).stream()
            .sorted(Comparator.comparing((Compra c) -> c.creada).reversed())
            .map(c -> vista(c,datos.exigir(Evento.class,c.eventoId))).toList();
    }

    public List<Map<String,Object>> reembolsos(Actor actor) {
        rol(actor,Rol.ADMINISTRADOR,Rol.ORGANIZADOR);
        return datos.listar(Compra.class,Criteria.where("reembolsos.0").exists(true)).stream()
            .filter(c -> actor.rol()==Rol.ADMINISTRADOR || datos.exigir(Evento.class,c.eventoId).organizadorId.equals(actor.id()))
            .map(c -> {
                Map<String,Object> resultado=vista(c,datos.exigir(Evento.class,c.eventoId));
                resultado.remove("entradas");
                return resultado;
            }).toList();
    }

    public static boolean conPago(Compra c) { return c.pago!=null && c.pago.resultado()==ResultadoPago.APROBADO; }

    private List<Map<String,Object>> resumenReembolsos(Compra c) {
        return c.reembolsos.stream().map(r -> datos("id",r.id(),"monto",importe(r.montoCentavos()),
            "motivo",r.motivo(),"estado",r.estado(),"iniciado",r.iniciado(),"acreditado",r.acreditado(),
            "cantidad",r.entradas().size())).toList();
    }

    private Map<String,Object> vista(Compra c,Evento e) {
        Instant ahora=reloj.instant();
        EstadoCompra estado=c.estado==EstadoCompra.RESERVADA && !ahora.isBefore(c.vence) ? EstadoCompra.EXPIRADA : c.estado;
        return datos("id",c.id,"eventoId",e.id,"evento",e.nombre,"inicio",e.inicio,"creada",c.creada,"vence",c.vence,
            "cantidad",c.cantidad,"precio",importe(c.precioCentavos),"total",importe(c.total()),"estado",estado,"pago",c.pago,
            "entradas",c.entradas,"reembolsos",resumenReembolsos(c),"plazoHoras",e.plazoHoras==null ? plazo : e.plazoHoras,
            "limite",limite(e),"cancelable",ahora.isBefore(e.inicio) && !ahora.isAfter(limite(e)) && e.estado!=EstadoEvento.CANCELADO
            && c.entradas.stream().anyMatch(t -> t.estado()==EstadoEntrada.VALIDA));
    }

    private Instant limite(Evento e) { return e.inicio.minus(Duration.ofHours(e.plazoHoras==null ? plazo : e.plazoHoras)); }

    @Scheduled(initialDelay=15000,fixedDelay=15000)
    public void procesarVencimientos() {
        Instant ahora=reloj.instant();
        for (Compra c : datos.listar(Compra.class,Criteria.where("estado").is(EstadoCompra.RESERVADA),Criteria.where("vence").lte(ahora))) {
            tx.ejecutar(() -> {
                Compra vigente=datos.exigir(Compra.class,c.id);
                if (vigente.estado==EstadoCompra.RESERVADA && !vigente.vence.isAfter(ahora)) {
                    Evento e=datos.exigir(Evento.class,vigente.eventoId);
                    liberar(vigente,e,EstadoCompra.EXPIRADA);
                    datos.guardar(e);
                    auditoria.exito(null,"RESERVA_EXPIRADA",vigente.id,"Cupos liberados");
                }
                return null;
            });
        }
        for (Evento candidato : datos.listar(Evento.class,Criteria.where("estado").in(EstadoEvento.ACTIVO,EstadoEvento.INACTIVO),
                                              Criteria.where("fin").lte(ahora))) {
            tx.ejecutar(() -> {
                Evento e=datos.exigir(Evento.class,candidato.id);
                if ((e.estado==EstadoEvento.ACTIVO || e.estado==EstadoEvento.INACTIVO) && !e.fin.isAfter(ahora)) {
                    e.finalizar();
                    datos.guardar(e);
                }
                return null;
            });
        }
        Instant corte=ahora.minus(Duration.ofDays(30));
        for (Compra candidata : datos.listar(Compra.class,Criteria.where("reembolsos").elemMatch(
                 Criteria.where("estado").is(EstadoReembolso.INICIADO).and("iniciado").lte(corte)))) {
            tx.ejecutar(() -> {
                Compra c=datos.exigir(Compra.class,candidata.id);
                c.reembolsos.replaceAll(r -> {
                    if (r.estado()!=EstadoReembolso.INICIADO || r.iniciado().isAfter(corte)) return r;
                    datos.por(Usuario.class,"rol",Rol.ADMINISTRADOR).stream().filter(u -> u.estado==EstadoCuenta.ACTIVO)
                        .forEach(admin -> avisos.enviar(admin.id,r.id()+":PENDIENTE","Reembolso pendiente de confirmación",
                            "Revisar el reembolso "+r.id(),null,List.of("/#reembolsos")));
                    auditoria.exito(null,"REEMBOLSO_PENDIENTE",r.id(),"Sin confirmación en 30 días");
                    return new Reembolso(r.id(),r.montoCentavos(),r.motivo(),r.entradas(),EstadoReembolso.PENDIENTE_CONFIRMACION,r.iniciado(),null);
                });
                datos.guardar(c);
                return null;
            });
        }
    }
}
