package co.nexoeventos.dominio;

import java.time.Instant;
import co.nexoeventos.dominio.Dominio.EstadoEntrada;

public record Entrada(String codigo, EstadoEntrada estado, Instant usada) { }
