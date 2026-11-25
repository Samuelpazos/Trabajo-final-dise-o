package co.nexoeventos.dominio;

import java.time.Instant;
import co.nexoeventos.dominio.Dominio.ResultadoPago;

public record Pago(String referencia, ResultadoPago resultado, Instant fecha, String metodo) { }
