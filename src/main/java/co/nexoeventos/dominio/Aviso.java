package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("avisos")
public record Aviso(@Id String id, @Indexed String usuarioId, Instant fecha, String asunto,
                    String contenido, List<String> enlaces) { }
