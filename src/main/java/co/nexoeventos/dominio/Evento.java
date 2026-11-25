package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import co.nexoeventos.dominio.Dominio.EstadoEvento;

@Document("eventos")
public class Evento {
    @Id public String id;
    @Indexed public String organizadorId;
    public String nombre, descripcion, lugar, categoria, motivoCancelacion;
    public Instant inicio, fin, creado, cancelado;
    public int capacidad, reservadas, vendidas;
    public long precioCentavos;
    public Integer plazoHoras;
    public EstadoEvento estado = EstadoEvento.BORRADOR;
    public List<Actividad> actividades = new ArrayList<>();

    public int disponibles() { return capacidad - reservadas - vendidas; }
    public void publicar() { estado = EstadoEvento.ACTIVO; }
    public void ocultar() { estado = EstadoEvento.INACTIVO; }
    public void cancelar(String motivo, Instant momento) {
        motivoCancelacion = motivo;
        estado = EstadoEvento.CANCELADO;
        cancelado = momento;
    }
    public void finalizar() { estado = EstadoEvento.FINALIZADO; }
    public void reservar(int cantidad) {
        if (cantidad < 1 || cantidad > disponibles()) throw Regla.conflicto("No hay aforo disponible");
        reservadas += cantidad;
    }
    public void liberar(int cantidad) { reservadas = Math.max(0, reservadas - cantidad); }
}
