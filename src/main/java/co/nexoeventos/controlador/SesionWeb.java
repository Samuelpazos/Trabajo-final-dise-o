package co.nexoeventos.controlador;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
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
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.servicio.UsuarioServicio;
import static co.nexoeventos.dominio.Valores.secreto;

@Component
public class SesionWeb extends OncePerRequestFilter {
    private final UsuarioServicio usuarios;
    private final Clock reloj;

    public SesionWeb(UsuarioServicio usuarios,Clock reloj) { this.usuarios=usuarios; this.reloj=reloj; }

    public void abrir(HttpServletRequest peticion,Usuario usuario) {
        HttpSession anterior=peticion.getSession(false);
        if (anterior!=null) anterior.invalidate();
        HttpSession sesion=peticion.getSession(true);
        sesion.setAttribute("usuario",usuario.id);
        sesion.setAttribute("revision",usuario.revisionSesion);
        sesion.setAttribute("expira",reloj.instant().plus(Duration.ofHours(24)).toEpochMilli());
        sesion.setAttribute("csrf",secreto());
    }

    public void cerrar(HttpServletRequest peticion) {
        HttpSession sesion=peticion.getSession(false);
        if (sesion!=null) sesion.invalidate();
    }

    public Usuario usuario(HttpServletRequest peticion) {
        HttpSession s=peticion.getSession(false);
        if (s==null || !(s.getAttribute("usuario") instanceof String id) ||
            !(s.getAttribute("expira") instanceof Long expira) || expira<=reloj.millis()) {
            throw new Regla(401,"Inicia sesión para continuar");
        }
        return usuarios.sesion(id,(Integer)s.getAttribute("revision"));
    }

    public Actor actor(HttpServletRequest peticion) {
        return UsuarioServicio.actor(usuario(peticion));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,HttpServletResponse respuesta,FilterChain cadena)
        throws ServletException, IOException {
        respuesta.setHeader("X-Content-Type-Options","nosniff");
        respuesta.setHeader("Referrer-Policy","no-referrer");
        respuesta.setHeader("Permissions-Policy","camera=(self), microphone=(), geolocation=()");
        respuesta.setHeader("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self'; "
            +"img-src 'self' data:; media-src 'self' blob:; object-src 'none'; frame-ancestors 'none'; form-action 'self'; base-uri 'none'");
        if (peticion.isSecure()) respuesta.setHeader("Strict-Transport-Security","max-age=31536000");
        String ruta=peticion.getRequestURI();
        if (ruta.startsWith("/api/")) {
            respuesta.setHeader("Cache-Control","no-store");
            boolean cambia=!peticion.getMethod().matches("GET|HEAD|OPTIONS");
            if (cambia && !ruta.equals("/api/pasarela/webhook") && !autorizada(peticion,ruta)) {
                respuesta.setStatus(403);
                respuesta.setContentType("application/json;charset=UTF-8");
                respuesta.getWriter().write("{\"mensaje\":\"Solicitud sin protección de sesión válida\"}");
                return;
            }
        }
        cadena.doFilter(peticion,respuesta);
    }

    private boolean autorizada(HttpServletRequest peticion,String ruta) {
        if (!"web".equals(peticion.getHeader("X-Nexo-Solicitud"))) return false;
        if (ruta.equals("/api/sesion") || ruta.equals("/api/sesion/activar")) {
            if ("POST".equals(peticion.getMethod())) return true;
        }
        HttpSession sesion=peticion.getSession(false);
        String token=peticion.getHeader("X-Nexo-CSRF");
        return sesion!=null && token!=null && token.equals(sesion.getAttribute("csrf"));
    }
}
