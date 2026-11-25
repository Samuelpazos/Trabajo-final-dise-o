package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import co.nexoeventos.dominio.Dominio.Canal;
import co.nexoeventos.dominio.Dominio.EstadoCuenta;
import co.nexoeventos.dominio.Dominio.Rol;
import co.nexoeventos.dominio.Dominio.Tema;

@Document("usuarios")
public class Usuario {
    @Id public String id;
    @Indexed(unique = true, sparse = true) public String correo;
    public String nombre, identificacion, telefono, direccion, clave, tokenAlta;
    public Instant venceAlta, creado, solicitudPrivacidad, procesadoPrivacidad;
    public Rol rol;
    public EstadoCuenta estado = EstadoCuenta.ACTIVO;
    public Canal canal = Canal.AMBOS;
    public Set<Tema> temas = EnumSet.allOf(Tema.class);
    public Map<Tema, Canal> canales = new HashMap<>();
    public List<String> favoritos = new ArrayList<>();
    public List<Map<String, String>> busquedas = new ArrayList<>();
    public int revisionSesion;

    public boolean tieneRol(Rol rol) { return this.rol == rol; }
    public void asignarRol(Rol rol) { this.rol = rol; revisionSesion++; }
    public void cambiarEstado(EstadoCuenta estado) { this.estado = estado; revisionSesion++; }
    public void darBaja() { cambiarEstado(EstadoCuenta.BAJA_LOGICA); }
    public void anonimizar() {
        correo = telefono = identificacion = direccion = clave = tokenAlta = null;
        estado = EstadoCuenta.ANONIMIZADO;
        revisionSesion++;
    }
}
