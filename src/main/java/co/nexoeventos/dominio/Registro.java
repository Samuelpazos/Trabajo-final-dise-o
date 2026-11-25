package co.nexoeventos.dominio;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("auditoria")
public record Registro(@Id String id, @Indexed String actorId, String usuario, @Indexed String accion,
                       @Indexed Instant fecha, String ip, String elemento, String resultado, String detalle) { }
