package co.nexoeventos.adaptador;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import co.nexoeventos.dominio.Correo;
import co.nexoeventos.repositorio.Repositorio;

@Component
@Profile("demo")
public class BuzonDemostracion implements CorreoSalida {
    private final Repositorio datos;
    public BuzonDemostracion(Repositorio datos) { this.datos = datos; }
    @Override public void enviar(Correo correo) { datos.guardar(correo); }
}
