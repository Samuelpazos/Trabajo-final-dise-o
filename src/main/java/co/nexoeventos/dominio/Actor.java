package co.nexoeventos.dominio;

import co.nexoeventos.dominio.Dominio.Rol;

/** Usuario autenticado que ejecuta una operación. */
public record Actor(String id, String nombre, Rol rol) { }
