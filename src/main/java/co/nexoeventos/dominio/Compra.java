package co.nexoeventos.dominio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import co.nexoeventos.dominio.Dominio.EstadoCompra;

@Document("compras")
@CompoundIndex(name="reserva_por_solicitud", def="{'asistenteId': 1, 'solicitudId': 1}", unique=true)
public class Compra {
    @Id public String id;
    @Indexed public String eventoId;
    @Indexed public String asistenteId;
    @Indexed(unique = true) public String referenciaPago;
    @Indexed(unique = true, sparse = true) public List<String> codigos;
    public String solicitudId;
    public Instant creada, vence;
    public int cantidad;
    public long precioCentavos;
    public EstadoCompra estado = EstadoCompra.RESERVADA;
    public Pago pago;
    public List<Entrada> entradas = new ArrayList<>();
    public List<Reembolso> reembolsos = new ArrayList<>();

    public long total() { return precioCentavos * cantidad; }
    public void confirmarPago(Pago pago) { this.pago = pago; estado = EstadoCompra.PAGADA; }
    public void expirar() { estado = EstadoCompra.EXPIRADA; }
    public void cancelar() { estado = EstadoCompra.CANCELADA; }
    public void cambiarEstado(EstadoCompra estado) { this.estado = estado; }
}
