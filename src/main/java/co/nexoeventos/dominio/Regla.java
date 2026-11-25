package co.nexoeventos.dominio;

public class Regla extends RuntimeException {
    public final int estado;

    public Regla(int estado, String mensaje) {
        super(mensaje);
        this.estado = estado;
    }

    public static Regla invalida(String mensaje) { return new Regla(400, mensaje); }
    public static Regla acceso() { return new Regla(403, "No tienes permiso para acceder a este recurso"); }
    public static Regla ausente() { return new Regla(404, "No se encontró el recurso"); }
    public static Regla conflicto(String mensaje) { return new Regla(409, mensaje); }
}
