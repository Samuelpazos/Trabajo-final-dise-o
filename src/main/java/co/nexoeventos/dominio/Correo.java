package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** Entidad de dominio para un mensaje del buzón de notificaciones. */
@Document("buzon_demo")
public class Correo {
    @Id private String id;
    @Indexed private String usuarioId;
    private String destinatario;
    private Instant fecha;
    private String asunto;
    private String contenido;
    private List<String> enlaces;

    protected Correo() {
        // Constructor requerido por Spring Data MongoDB.
    }

    public Correo(String id, String usuarioId, String destinatario, Instant fecha,
                  String asunto, String contenido, List<String> enlaces) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.destinatario = destinatario;
        this.fecha = fecha;
        this.asunto = asunto;
        this.contenido = contenido;
        this.enlaces = enlaces;
    }

    public String getId() { return id; }
    public String getUsuarioId() { return usuarioId; }
    public String getDestinatario() { return destinatario; }
    public Instant getFecha() { return fecha; }
    public String getAsunto() { return asunto; }
    public String getContenido() { return contenido; }
    public List<String> getEnlaces() { return enlaces; }
}
