package co.nexoeventos.dominio;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("adjuntos")
public record Adjunto(@Id String id, String solicitudId, byte[] contenido) { }
