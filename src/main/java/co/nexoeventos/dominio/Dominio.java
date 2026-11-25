package co.nexoeventos.dominio;

/** Valores compartidos del dominio. Las entidades viven en clases separadas. */
public final class Dominio {
    private Dominio() { }

    public enum Rol { ADMINISTRADOR, ORGANIZADOR, ASISTENTE, PROVEEDOR }
    public enum EstadoCuenta { ACTIVO, INACTIVO, BAJA_LOGICA, ANONIMIZADO }
    public enum EstadoEvento { BORRADOR, ACTIVO, INACTIVO, CANCELADO, FINALIZADO }
    public enum EstadoCompra { RESERVADA, PAGADA, RECHAZADA, EXPIRADA, ABANDONADA, CANCELADA_PARCIAL, CANCELADA, REEMBOLSADA }
    public enum EstadoEntrada { VALIDA, USADA, CANCELADA }
    public enum EstadoReembolso { INICIADO, COMPLETADO, FALLIDO, PENDIENTE_CONFIRMACION }
    public enum EstadoSolicitud { SOLICITADO, PRESUPUESTADO, CONTRAPROPUESTA, CONFIRMADO, RECHAZADO, FINALIZADO }
    public enum Canal { CORREO, PLATAFORMA, AMBOS }
    public enum Tema { RECORDATORIOS, RECORDATORIOS_ACTIVIDAD, PROMOCIONES, PRESUPUESTOS, PROGRAMACION }
    public enum ResultadoPago { APROBADO, SIN_FONDOS, DATOS_INVALIDOS, TIMEOUT }
}
