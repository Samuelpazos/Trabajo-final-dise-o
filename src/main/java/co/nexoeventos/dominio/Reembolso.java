package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.List;
import co.nexoeventos.dominio.Dominio.EstadoReembolso;

public record Reembolso(String id, long montoCentavos, String motivo, List<String> entradas,
                        EstadoReembolso estado, Instant iniciado, Instant acreditado) { }
