package co.nexoeventos.controlador;

import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import co.nexoeventos.dominio.Correo;
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
import static co.nexoeventos.dominio.Valores.datos;

@RestController
@RequestMapping("/api")
public class UsuarioControlador {
    private final SesionWeb web;
    private final UsuarioServicio usuarios;
    private final NotificacionServicio avisos;

    public UsuarioControlador(SesionWeb web,UsuarioServicio usuarios,NotificacionServicio avisos) {
        this.web=web; this.usuarios=usuarios; this.avisos=avisos;
    }

    @PostMapping("/sesion")
    Map<String,Object> iniciar(HttpServletRequest p,@Valid @RequestBody Acceso acceso) {
        Usuario usuario=usuarios.autenticar(acceso);
        web.abrir(p,usuario);
        return respuesta(p,usuario);
    }
    @GetMapping("/sesion")
    Map<String,Object> sesion(HttpServletRequest p) { return respuesta(p,web.usuario(p)); }
    @DeleteMapping("/sesion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void salir(HttpServletRequest p) { web.cerrar(p); }
    @PostMapping("/sesion/activar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void activar(@Valid @RequestBody AltaClave datos) { usuarios.configurar(datos); }
    @PutMapping("/perfil/clave")
    Map<String,Object> cambiarClave(HttpServletRequest p,@Valid @RequestBody CambioClave datos) {
        Usuario usuario=usuarios.cambiarClave(web.actor(p),datos);
        web.abrir(p,usuario);
        return respuesta(p,usuario);
    }
    @PostMapping("/perfil/privacidad")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void privacidad(HttpServletRequest p,@Valid @RequestBody Privacidad datos) {
        usuarios.solicitarPrivacidad(web.actor(p),datos);
    }
    @GetMapping("/usuarios")
    List<Map<String,Object>> listar(HttpServletRequest p,@RequestParam(required=false) String filtro) {
        return usuarios.listar(web.actor(p),filtro);
    }
    @GetMapping("/usuarios/{id}")
    Map<String,Object> detalle(HttpServletRequest p,@PathVariable String id) { return usuarios.detalle(web.actor(p),id); }
    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String,Object> crear(HttpServletRequest p,@Valid @RequestBody Cuenta datos) { return usuarios.registrar(web.actor(p),datos); }
    @PutMapping("/usuarios/{id}")
    Map<String,Object> editar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody Cuenta datos) {
        return usuarios.editar(web.actor(p),id,datos);
    }
    @PatchMapping("/usuarios/{id}/estado")
    Map<String,Object> estado(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody Estado datos) {
        return usuarios.estado(web.actor(p),id,datos);
    }
    @PostMapping("/usuarios/{id}/anonimizacion")
    Map<String,Object> anonimizar(HttpServletRequest p,@PathVariable String id,@Valid @RequestBody Estado datos) {
        return usuarios.anonimizar(web.actor(p),id,datos);
    }
    @GetMapping("/perfil/preferencias")
    Preferencias preferencias(HttpServletRequest p) { return avisos.preferencias(web.actor(p)); }
    @PutMapping("/perfil/preferencias")
    Preferencias guardarPreferencias(HttpServletRequest p,@Valid @RequestBody Preferencias datos) {
        return avisos.cambiar(web.actor(p),datos);
    }
    @GetMapping("/avisos")
    List<Aviso> avisos(HttpServletRequest p) { return avisos.avisos(web.actor(p)); }

    private Map<String,Object> respuesta(HttpServletRequest p,Usuario usuario) {
        return datos("usuario",usuarios.perfil(usuario),"csrf",p.getSession(false).getAttribute("csrf"),"https",p.isSecure());
    }
}

@RestController
@Profile("demo")
class BuzonControlador {
    private final SesionWeb web;
    private final NotificacionServicio avisos;
    BuzonControlador(SesionWeb web,NotificacionServicio avisos) { this.web=web; this.avisos=avisos; }

    @GetMapping("/api/demo/buzon")
    List<Correo> buzon(HttpServletRequest p) { return avisos.buzon(web.actor(p)); }
}
