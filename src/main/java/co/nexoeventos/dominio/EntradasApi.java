package co.nexoeventos.dominio;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.validation.constraints.*;
import static co.nexoeventos.dominio.Dominio.*;

public final class EntradasApi {
    private EntradasApi() {}

    public record Acceso(@NotBlank @Size(max=254) String correo, @NotBlank @Size(max=100) String clave) {}
    public record AltaClave(@NotBlank @Size(max=100) String token, @NotBlank @Size(min=10,max=72) String clave) {}
    public record CambioClave(@NotBlank @Size(max=100) String actual, @NotBlank @Size(min=10,max=72) String nueva) {}
    public record Cuenta(@NotBlank @Size(max=100) String nombre, @NotBlank @Size(max=254) String correo,
                         @Pattern(regexp="[+0-9 ()-]{7,25}") String telefono, @NotNull Rol rol,
                         @Size(max=60) String identificacion, @Size(max=250) String direccion) {}
    public record Estado(@NotBlank String estado, @AssertTrue(message="Debes confirmar la acción") boolean confirmar) {}
    public record Privacidad(@NotBlank @Size(max=100) String clave,
                             @AssertTrue(message="Debes confirmar la solicitud") boolean confirmar) {}
    public record DatosEvento(@NotBlank @Size(max=150) String nombre, @Size(max=6000) String descripcion,
        @NotNull Instant inicio, @NotNull Instant fin, @NotBlank @Size(max=200) String lugar,
        @NotBlank @Size(max=80) String categoria, @Min(1) @Max(1000000) int capacidad,
        @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal precio,
        @Min(0) @Max(8760) Integer plazoHoras) {}
    public record DatosActividad(@NotBlank @Size(max=150) String nombre, @Size(max=3000) String descripcion,
        @Size(max=150) String lugar, @NotNull Instant inicio, @Min(1) @Max(10080) int minutos,
        @Size(max=100) String responsable, @Min(1) int capacidad) {}
    public record CancelarEvento(@NotBlank @Size(max=1500) String motivo,
                                @AssertTrue(message="Debes confirmar la cancelación") boolean confirmar) {}
    public record Reserva(@NotBlank String eventoId, @Min(value=1,message="Debes seleccionar al menos 1 entrada")
                         @Max(500) int cantidad, @NotBlank @Size(max=100) String solicitudId) {}
    public record CancelarCompra(@NotEmpty @Size(max=500) List<@NotBlank String> entradas,
                                 @AssertTrue(message="Debes confirmar la cancelación") boolean confirmar) {}
    public record Codigo(@NotBlank @Size(max=260) String codigo) {}
    public record SimularPago(@NotNull ResultadoPago resultado) {}
    public record SimularReembolso(@NotNull EstadoReembolso resultado) {}
    public record Preferencias(@NotNull Canal canal, @NotNull Set<Tema> temas, Map<Tema,Canal> canales) {}
    public record DatosOferta(@NotBlank @Size(max=150) String nombre, @NotBlank @Size(max=3000) String descripcion,
        @NotBlank @Size(max=80) String tipo, @NotNull @DecimalMin("0") @Digits(integer=8,fraction=2) BigDecimal precio,
        @NotNull LocalDate desde, @NotNull LocalDate hasta, boolean disponible) {}
    public record PedirServicio(@NotBlank String ofertaId, @NotBlank String eventoId,
                                @Size(max=3000) String observaciones) {}
    public record Responder(@NotNull EstadoSolicitud estado,
        @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal monto, @Size(max=3000) String comentario) {}
}
