package co.nexoeventos.adaptador;

import co.nexoeventos.dominio.Reembolso;

public interface PasarelaPago {
    String iniciarPago(String compraId, long montoCentavos);
    void iniciarReembolso(String referenciaPago, Reembolso reembolso);
    Confirmacion verificar(String cuerpo, String firma);

    record Confirmacion(String tipo, String referencia, String reembolsoId, String resultado) {}
}
