package co.nexoeventos.servicio;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Aviso;
import co.nexoeventos.dominio.Compra;
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
import co.nexoeventos.dominio.EntradasApi.*;
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class UsuarioServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final AuditoriaServicio auditoria;
    private final NotificacionServicio avisos;
    private final BCryptPasswordEncoder claves;
    private final Clock reloj;
    private final String claveFicticia;

    public UsuarioServicio(Repositorio datos, Transacciones tx, AuditoriaServicio auditoria,
                          NotificacionServicio avisos, BCryptPasswordEncoder claves, Clock reloj) {
        this.datos = datos; this.tx = tx; this.auditoria = auditoria; this.avisos = avisos;
        this.claves = claves; this.reloj = reloj; this.claveFicticia = claves.encode(secreto());
    }

    public Usuario autenticar(Acceso acceso) {
        String correo = texto(acceso.correo()).toLowerCase(Locale.ROOT);
        Usuario usuario = datos.uno(Usuario.class, "correo", correo).orElse(null);
        boolean aprobada = coincideClave(acceso.clave(), usuario == null ? claveFicticia : usuario.clave);
        Actor actor = usuario == null ? null : actor(usuario);
        if (!aprobada || usuario == null) {
            auditoria.registrar(actor, "LOGIN", null, "RECHAZADO", usuario == null ? "Correo no registrado" : "Contraseña incorrecta");
            throw new Regla(401,"Correo o contraseña incorrectos");
        }
        if (usuario.estado != EstadoCuenta.ACTIVO || usuario.tokenAlta != null) {
            auditoria.registrar(actor, "LOGIN", usuario.id, "RECHAZADO", usuario.estado.name());
            String mensaje = switch (usuario.estado) {
                case INACTIVO -> "Tu cuenta está desactivada. Contacta al administrador";
                case BAJA_LOGICA -> "Tu cuenta ha sido dada de baja y no está disponible";
                case ANONIMIZADO -> "Correo o contraseña incorrectos";
                case ACTIVO -> "Configura tu contraseña con el enlace enviado al correo";
            };
            throw new Regla(401, mensaje);
        }
        auditoria.exito(actor, "LOGIN", usuario.id, "Inicio de sesión");
        return usuario;
    }

    public Usuario sesion(String id, int revision) {
        Usuario usuario = datos.id(Usuario.class, id).orElse(null);
        if (usuario == null || usuario.estado != EstadoCuenta.ACTIVO || usuario.revisionSesion != revision) {
            throw new Regla(401, "Sesión expirada. Vuelve a ingresar");
        }
        return usuario;
    }

    public static Actor actor(Usuario usuario) { return new Actor(usuario.id, usuario.nombre, usuario.rol); }

    public Map<String,Object> perfil(Usuario usuario) {
        return datos("id", usuario.id, "nombre", usuario.nombre, "correo", usuario.correo,
            "rol", usuario.rol, "estado", usuario.estado, "solicitudPrivacidad", usuario.solicitudPrivacidad);
    }

    private Map<String,Object> cuenta(Usuario usuario) {
        Map<String,Object> cuenta = perfil(usuario);
        cuenta.put("telefono", usuario.telefono);
        cuenta.put("identificacion", usuario.identificacion);
        cuenta.put("direccion", usuario.direccion);
        return cuenta;
    }

    public List<Map<String,Object>> listar(Actor actor, String filtro) {
        rol(actor, Rol.ADMINISTRADOR);
        List<Map<String,Object>> lista = datos.listar(Usuario.class).stream()
            .filter(u -> coincide(u.nombre, filtro) || coincide(u.correo, filtro))
            .sorted(Comparator.comparing(u -> u.nombre)).map(this::cuenta).toList();
        auditoria.registrar(actor, "ACCESO_DATOS", null, lista.isEmpty() ? "SIN_RESULTADOS" : "EXITOSO",
            "Consulta de nombre, correo, teléfono e identificación. Resultados: " + lista.size());
        return lista;
    }

    public Map<String,Object> detalle(Actor actor, String id) {
        rol(actor, Rol.ADMINISTRADOR);
        Usuario usuario = datos.id(Usuario.class, id).orElse(null);
        auditoria.registrar(actor, "ACCESO_DATOS", id, usuario == null ? "SIN_RESULTADOS" : "EXITOSO",
            "Ficha de nombre, correo, teléfono, identificación y dirección");
        if (usuario == null) throw Regla.ausente();
        Map<String,Object> ficha=cuenta(usuario);
        ficha.put("compras", datos.por(Compra.class,"asistenteId",id).stream().filter(CompraServicio::conPago)
            .map(c -> datos("fecha",c.creada,"evento",datos.id(Evento.class,c.eventoId).map(e -> e.nombre).orElse("Evento"),
                "cantidad",c.cantidad,"monto",importe(c.total()))).toList());
        return ficha;
    }

    public Map<String,Object> registrar(Actor actor, Cuenta entrada) {
        rol(actor, Rol.ADMINISTRADOR);
        String token = secreto();
        String temporal = secreto().substring(0, 16);
        String hashTemporal = claves.encode(temporal);
        try {
            return tx.ejecutar(() -> {
                Usuario usuario = new Usuario();
                usuario.id = id();
                aplicar(usuario, entrada);
                usuario.clave = hashTemporal;
                usuario.tokenAlta = huella(token);
                usuario.venceAlta = reloj.instant().plus(Duration.ofHours(24));
                usuario.creado = reloj.instant();
                datos.insertar(usuario);
                avisos.accesoInicial(usuario, token, temporal);
                auditoria.exito(actor, "USUARIO_CREADO", usuario.id, usuario.rol.name());
                return cuenta(usuario);
            });
        } catch (DuplicateKeyException error) {
            throw Regla.conflicto("El correo ya está registrado en el sistema");
        }
    }

    public Map<String,Object> editar(Actor actor, String id, Cuenta entrada) {
        rol(actor, Rol.ADMINISTRADOR);
        try {
            return tx.ejecutar(() -> {
                Usuario usuario = datos.exigir(Usuario.class, id);
                if (usuario.estado == EstadoCuenta.ANONIMIZADO) throw Regla.conflicto("La cuenta fue anonimizada");
                if (actor.id().equals(id) && entrada.rol() != actor.rol()) throw Regla.acceso();
                Map<String,Object> anterior = cuenta(usuario);
                if (!correo(entrada.correo()).equals(usuario.correo) &&
                    !datos.por(Compra.class, "asistenteId", id).isEmpty()) {
                    throw Regla.conflicto("El correo no se puede modificar cuando existen compras");
                }
                if (entrada.rol() != usuario.rol || !correo(entrada.correo()).equals(usuario.correo)) usuario.revisionSesion++;
                aplicar(usuario, entrada);
                datos.guardar(usuario);
                Map<String,Object> nuevo = cuenta(usuario);
                String cambios = List.of("nombre","correo","telefono","rol","identificacion","direccion").stream()
                    .filter(campo -> !Objects.equals(anterior.get(campo), nuevo.get(campo)))
                    .map(campo -> campo + ": " + anterior.get(campo) + " → " + nuevo.get(campo))
                    .reduce((a,b) -> a + ", " + b).orElse("Sin cambios");
                auditoria.exito(actor, "USUARIO_EDITADO", id, cambios);
                return nuevo;
            });
        } catch (DuplicateKeyException error) {
            throw Regla.conflicto("Este correo ya está registrado");
        }
    }

    private void aplicar(Usuario usuario, Cuenta entrada) {
        usuario.nombre = texto(entrada.nombre());
        usuario.correo = correo(entrada.correo());
        usuario.telefono = entrada.telefono();
        usuario.rol = entrada.rol();
        usuario.identificacion = entrada.identificacion();
        usuario.direccion = entrada.direccion();
    }

    public Map<String,Object> estado(Actor actor, String id, Estado entrada) {
        rol(actor, Rol.ADMINISTRADOR);
        if (!entrada.confirmar() || actor.id().equals(id)) throw Regla.acceso();
        EstadoCuenta estado;
        try { estado = EstadoCuenta.valueOf(entrada.estado()); }
        catch (IllegalArgumentException error) { throw Regla.invalida("Estado de cuenta inválido"); }
        if (estado == EstadoCuenta.ANONIMIZADO) throw Regla.invalida("Usa el flujo de privacidad");
        return tx.ejecutar(() -> {
            Usuario usuario = datos.exigir(Usuario.class, id);
            if (usuario.estado == estado) return cuenta(usuario);
            if (usuario.estado == EstadoCuenta.BAJA_LOGICA || usuario.estado == EstadoCuenta.ANONIMIZADO) {
                throw Regla.conflicto("La cuenta está dada de baja");
            }
            usuario.estado = estado;
            usuario.revisionSesion++;
            datos.guardar(usuario);
            auditoria.exito(actor, "USUARIO_" + estado, id, "Cambio de estado");
            Map<String,Object> cambio = cuenta(usuario);
            if (estado == EstadoCuenta.INACTIVO && datos.por(Evento.class,"organizadorId",id).stream()
                .anyMatch(e -> e.estado == EstadoEvento.ACTIVO)) cambio.put("aviso", "Este usuario tiene eventos activos");
            return cambio;
        });
    }

    public void configurar(AltaClave entrada) {
        String nueva = hash(entrada.clave());
        tx.ejecutar(() -> {
            Usuario usuario = datos.uno(Usuario.class, "tokenAlta", huella(entrada.token()))
                .orElseThrow(() -> Regla.invalida("El enlace no es válido o expiró"));
            if (usuario.estado != EstadoCuenta.ACTIVO || !reloj.instant().isBefore(usuario.venceAlta)) {
                throw Regla.invalida("El enlace no es válido o expiró");
            }
            usuario.clave = nueva;
            usuario.tokenAlta = null;
            usuario.venceAlta = null;
            usuario.revisionSesion++;
            datos.guardar(usuario);
            datos.eliminarPor(Correo.class, "_id", "ALTA:" + usuario.id);
            auditoria.exito(actor(usuario), "CLAVE_CONFIGURADA", usuario.id, "");
            return null;
        });
    }

    public Usuario cambiarClave(Actor actor, CambioClave entrada) {
        String nueva = hash(entrada.nueva());
        return tx.ejecutar(() -> {
            Usuario usuario = datos.exigir(Usuario.class, actor.id());
            if (!coincideClave(entrada.actual(), usuario.clave)) throw Regla.invalida("Contraseña actual incorrecta");
            usuario.clave = nueva;
            usuario.revisionSesion++;
            datos.guardar(usuario);
            auditoria.exito(actor, "CLAVE_CAMBIADA", usuario.id, "");
            return usuario;
        });
    }

    public void solicitarPrivacidad(Actor actor, Privacidad entrada) {
        tx.ejecutar(() -> {
            Usuario usuario = datos.exigir(Usuario.class, actor.id());
            if (!entrada.confirmar() || !coincideClave(entrada.clave(), usuario.clave)) {
                throw Regla.invalida("Confirma la solicitud con tu contraseña actual");
            }
            if (usuario.solicitudPrivacidad == null) {
                usuario.solicitudPrivacidad = reloj.instant();
                datos.guardar(usuario);
                auditoria.exito(actor, "PRIVACIDAD_SOLICITADA", usuario.id, "Confirmación de identidad");
            }
            return null;
        });
    }

    public Map<String,Object> anonimizar(Actor actor, String id, Estado confirmacion) {
        rol(actor, Rol.ADMINISTRADOR);
        if (actor.id().equals(id) || !confirmacion.confirmar()) throw Regla.acceso();
        return tx.ejecutar(() -> {
            Usuario usuario = datos.exigir(Usuario.class, id);
            if (usuario.estado == EstadoCuenta.ANONIMIZADO) return cuenta(usuario);
            if (usuario.solicitudPrivacidad == null) throw Regla.conflicto("La cuenta no tiene solicitud de privacidad");
            String alias = "Usuario Anónimo #" + secreto().substring(0, 8);
            auditoria.desidentificar(usuario, alias);
            datos.eliminarPor(Correo.class, "usuarioId", id);
            datos.eliminarPor(Aviso.class, "usuarioId", id);
            usuario.nombre = alias;
            usuario.correo = usuario.telefono = usuario.identificacion = usuario.direccion = usuario.clave = usuario.tokenAlta = null;
            usuario.venceAlta = null;
            usuario.favoritos.clear();
            usuario.busquedas.clear();
            usuario.temas.clear();
            usuario.canales.clear();
            usuario.procesadoPrivacidad = reloj.instant();
            usuario.estado = EstadoCuenta.ANONIMIZADO;
            usuario.revisionSesion++;
            datos.guardar(usuario);
            auditoria.exito(actor, "PRIVACIDAD_PROCESADA", id, alias);
            return cuenta(usuario);
        });
    }

    public String nombre(String id) {
        return datos.id(Usuario.class, id).map(u -> u.nombre).orElse("Usuario no disponible");
    }

    private String correo(String correo) {
        String valor = texto(correo).toLowerCase(Locale.ROOT);
        if (!valor.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) throw Regla.invalida("Ingresa un correo válido");
        return valor;
    }

    private boolean coincideClave(String clave, String hash) {
        return hash != null && clave.getBytes(StandardCharsets.UTF_8).length <= 72 && claves.matches(clave, hash);
    }

    private String hash(String clave) {
        if (clave.getBytes(StandardCharsets.UTF_8).length > 72) throw Regla.invalida("La contraseña supera 72 bytes");
        return claves.encode(clave);
    }
}
