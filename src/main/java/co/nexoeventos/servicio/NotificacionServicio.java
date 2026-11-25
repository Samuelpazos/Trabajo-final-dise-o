package co.nexoeventos.servicio;

import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import co.nexoeventos.adaptador.CorreoSalida;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Aviso;
import co.nexoeventos.dominio.Correo;
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
import co.nexoeventos.dominio.EntradasApi.Preferencias;
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class NotificacionServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final CorreoSalida salida;
    private final Clock reloj;
    private final String origen;

    public NotificacionServicio(Repositorio datos, Transacciones tx, CorreoSalida salida, Clock reloj,
                         @Value("${nexo.base-url}") String origen) {
        this.datos = datos; this.tx = tx; this.salida = salida; this.reloj = reloj;
        this.origen = origen.replaceAll("/+$", "");
    }

    public void enviar(String usuarioId, String referencia, String asunto, String contenido,
                Tema tema, List<String> enlaces) {
        Usuario usuario = datos.id(Usuario.class, usuarioId).orElse(null);
        if (usuario == null || usuario.estado == EstadoCuenta.ANONIMIZADO) return;
        boolean obligatorio = tema == null;
        if (!obligatorio && !usuario.temas.contains(tema)) return;
        String id = referencia + ":" + usuarioId;
        Canal canal=tema==null ? usuario.canal : usuario.canales.getOrDefault(tema,usuario.canal);
        if (canal != Canal.CORREO || obligatorio) {
            datos.guardar(new Aviso(id, usuarioId, reloj.instant(), asunto, contenido, enlaces));
        }
        if (usuario.correo != null && (canal != Canal.PLATAFORMA || obligatorio)) {
            List<String> enlacesCorreo = enlaces.stream().map(enlace -> origen + enlace).toList();
            salida.enviar(new Correo(id, usuarioId, usuario.correo, reloj.instant(), asunto, contenido, enlacesCorreo));
        }
    }

    public void enviarUnaVez(String usuarioId,String referencia,String asunto,String contenido,Tema tema,String enlace) {
        String id=referencia+":"+usuarioId;
        if (datos.id(Aviso.class,id).isEmpty() && datos.id(Correo.class,id).isEmpty())
            enviar(usuarioId,referencia,asunto,contenido,tema,List.of(enlace));
    }

    public void accesoInicial(Usuario usuario, String token, String temporal) {
        String enlace = origen + "/#activar/" + token;
        salida.enviar(new Correo("ALTA:" + usuario.id, usuario.id, usuario.correo, reloj.instant(),
            "Configura tu cuenta de Nexo Eventos", "Contraseña temporal: " + temporal
            + ". Configura tu contraseña en las próximas 24 horas.", List.of(enlace)));
    }

    public Preferencias preferencias(Actor actor) {
        Usuario usuario = datos.exigir(Usuario.class, actor.id());
        return new Preferencias(usuario.canal, usuario.temas, usuario.canales);
    }

    public Preferencias cambiar(Actor actor, Preferencias preferencias) {
        return tx.ejecutar(() -> {
            Usuario usuario = datos.exigir(Usuario.class, actor.id());
            usuario.canal = preferencias.canal();
            usuario.temas = preferencias.temas();
            usuario.canales = preferencias.canales()==null ? new HashMap<>() : new HashMap<>(preferencias.canales());
            usuario.canales.values().removeIf(Objects::isNull);
            datos.guardar(usuario);
            return preferencias;
        });
    }

    public List<Aviso> avisos(Actor actor) {
        return datos.por(Aviso.class, "usuarioId", actor.id()).stream()
            .sorted(Comparator.comparing(Aviso::fecha).reversed()).toList();
    }

    public List<Correo> buzon(Actor actor) {
        rol(actor, Rol.ADMINISTRADOR);
        return datos.listar(Correo.class).stream().sorted(Comparator.comparing(Correo::getFecha).reversed()).toList();
    }
}
