package co.nexoeventos.adaptador;

import java.time.*;
import org.springframework.boot.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
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
import co.nexoeventos.repositorio.Repositorio;
import static co.nexoeventos.dominio.Valores.ZONA;

@Component
@Profile("demo")
public class DatosDemostracion implements ApplicationRunner {
    private final Repositorio datos;
    private final BCryptPasswordEncoder claves;
    private final Clock reloj;

    public DatosDemostracion(Repositorio datos,BCryptPasswordEncoder claves,Clock reloj) {
        this.datos=datos; this.claves=claves; this.reloj=reloj;
    }

    @Override
    public void run(ApplicationArguments args) {
        cuenta("demo-admin","admin","Valentina Cruz",Rol.ADMINISTRADOR);
        cuenta("demo-organizador","organizador","Colectivo Horizonte",Rol.ORGANIZADOR);
        cuenta("demo-asistente","asistente","Laura Méndez",Rol.ASISTENTE);
        cuenta("demo-proveedor","proveedor","Estudio Norte",Rol.PROVEEDOR);
        LocalDate hoy=LocalDate.now(reloj.withZone(ZONA));
        evento("festival-horizonte","Festival Horizonte","Música","Auditorio Central, Bogotá",
            "Tres presentaciones en vivo y un espacio para conocer proyectos musicales independientes.",hoy.plusDays(20),120,3550);
        evento("encuentro-creativo","Encuentro Creativo","Cultura","Centro Cultural, Medellín",
            "Conversaciones, talleres y exhibiciones para la comunidad creativa.",hoy.plusDays(25),64,2000);
        evento("noche-tecnologia","Noche de Tecnología","Tecnología","Centro de Innovación, Bogotá",
            "Una jornada de demostraciones y conversaciones sobre tecnología aplicada.",hoy.plusDays(35),32,2500);
        if (datos.id(Oferta.class,"sonido-directo").isEmpty()) {
            Oferta so=new Oferta();
            so.id="sonido-directo"; so.proveedorId="demo-proveedor"; so.nombre="Sonido en directo";
            so.descripcion="Montaje, sonido y operación técnica para recitales y conferencias.";
            so.tipo="Sonido"; so.precioCentavos=35000;
            so.desde=hoy.minusDays(30).toString(); so.hasta=hoy.plusYears(2).toString();
            datos.insertar(so);
        }
    }

    private void cuenta(String id,String correo,String nombre,Rol rol) {
        if (datos.id(Usuario.class,id).isPresent()) return;
        Usuario u=new Usuario();
        u.id=id; u.nombre=nombre; u.rol=rol; u.creado=reloj.instant();
        u.correo=correo+"@nexo.test"; u.clave=claves.encode("NexoDemo2026!");
        datos.insertar(u);
    }

    private void evento(String id,String nombre,String categoria,String lugar,String descripcion,LocalDate fecha,int capacidad,long precio) {
        if (datos.id(Evento.class,id).isPresent()) return;
        Evento e=new Evento();
        e.id=id; e.organizadorId="demo-organizador"; e.nombre=nombre; e.descripcion=descripcion;
        e.categoria=categoria; e.lugar=lugar; e.capacidad=capacidad; e.precioCentavos=precio;
        e.inicio=fecha.atTime(19,0).atZone(ZONA).toInstant(); e.fin=e.inicio.plus(Duration.ofHours(4));
        e.creado=reloj.instant(); e.estado=EstadoEvento.ACTIVO;
        if ("festival-horizonte".equals(id)) {
            e.actividades.add(new Actividad("apertura","Apertura","Bienvenida a los asistentes","Sala principal",e.inicio,30,"Equipo Horizonte",capacidad));
            e.actividades.add(new Actividad("concierto","Concierto","Presentaciones de música en vivo","Sala principal",
                e.inicio.plus(Duration.ofMinutes(45)),75,"Equipo Horizonte",capacidad));
        }
        datos.insertar(e);
    }
}
