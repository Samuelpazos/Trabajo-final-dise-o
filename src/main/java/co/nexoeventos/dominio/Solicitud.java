package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import co.nexoeventos.dominio.Dominio.EstadoSolicitud;

@Document("solicitudes")
public class Solicitud {
    @Id public String id;
    @Indexed public String organizadorId;
    @Indexed public String proveedorId;
    @Indexed public String ofertaId;
    public String eventoId, servicio, observaciones;
    public Instant creada, finalizada;
    public EstadoSolicitud estado = EstadoSolicitud.SOLICITADO;
    public Long montoCentavos;
    public List<CambioServicio> historial = new ArrayList<>();
}
