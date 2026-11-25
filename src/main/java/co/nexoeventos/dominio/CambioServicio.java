package co.nexoeventos.dominio;

import java.time.Instant;
import co.nexoeventos.dominio.Dominio.EstadoSolicitud;

public record CambioServicio(Instant fecha, String actorId, EstadoSolicitud estado,
                             Long montoCentavos, String comentario, String adjuntoId) { }
