package co.nexoeventos.dominio;

import java.time.Instant;

/** Actividad programada dentro de un evento. */
public record Actividad(String id, String nombre, String descripcion, String lugar,
                        Instant inicio, int minutos, String responsable, int capacidad) { }
