package co.nexoeventos.servicio;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;
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
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Registro;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.repositorio.Repositorio;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class AuditoriaServicio {
    private final Repositorio datos;
    private final Clock reloj;

    public AuditoriaServicio(Repositorio datos, Clock reloj) {
        this.datos = datos;
        this.reloj = reloj;
    }

    public void registrar(Actor actor, String accion, String elemento, String resultado, String detalle) {
        String ip = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes solicitud
            ? solicitud.getRequest().getRemoteAddr() : null;
        datos.insertar(new Registro(id(), actor == null ? null : actor.id(), actor == null ? "Sistema" : datos.id(Usuario.class,actor.id()).map(u -> u.correo).orElse(actor.nombre()),
            accion, reloj.instant(), ip, elemento, resultado, detalle));
    }

    public void exito(Actor actor, String accion, String elemento, String detalle) {
        registrar(actor, accion, elemento, "EXITOSO", detalle);
    }

    public List<Registro> consultar(Actor actor, String usuario, String accion, LocalDate desde, LocalDate hasta) {
        rol(actor, Rol.ADMINISTRADOR);
        return datos.listar(Registro.class).stream()
            .filter(r -> coincide(r.usuario(), usuario) || coincide(r.actorId(), usuario))
            .filter(r -> coincide(r.accion(), accion) && periodo(r.fecha(), desde, hasta))
            .sorted(Comparator.comparing(Registro::fecha).reversed()).toList();
    }

    public void desidentificar(Usuario usuario, String alias) {
        List<String> referencias = new ArrayList<>();
        referencias.add(usuario.id);
        if (usuario.correo != null) referencias.add(usuario.correo);
        if (usuario.identificacion != null && !usuario.identificacion.isBlank()) referencias.add(usuario.identificacion);
        for (Registro registro : datos.listar(Registro.class)) {
            boolean actor = usuario.id.equals(registro.actorId());
            boolean elemento = referencias.contains(registro.elemento());
            boolean textoPersonal = referencias.stream().anyMatch(ref ->
                registro.detalle() != null && registro.detalle().contains(ref));
            if (actor || elemento || textoPersonal) {
                datos.guardar(new Registro(registro.id(), registro.actorId(), actor ? alias : registro.usuario(),
                    registro.accion(), registro.fecha(), actor ? null : registro.ip(),
                    elemento ? usuario.id : registro.elemento(), registro.resultado(), ""));
            }
        }
    }
}
