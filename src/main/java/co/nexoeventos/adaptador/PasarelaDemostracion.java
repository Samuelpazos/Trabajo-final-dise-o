package co.nexoeventos.adaptador;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import co.nexoeventos.dominio.Reembolso;
import co.nexoeventos.dominio.Regla;
import static co.nexoeventos.dominio.Valores.secreto;

@Component
@Profile("demo")
public class PasarelaDemostracion implements PasarelaPago {
    private final ObjectMapper json;
    private final byte[] clave = secreto().getBytes(StandardCharsets.UTF_8);

    public PasarelaDemostracion(ObjectMapper json) { this.json = json; }
    @Override public String iniciarPago(String compraId, long montoCentavos) { return "PAGO-DEMO-" + compraId; }

    @Override
    public void iniciarReembolso(String referenciaPago, Reembolso reembolso) {
        // La orden queda registrada en la compra. El simulador confirma el resultado bajo demanda.
    }

    public Confirmacion simular(Confirmacion confirmacion) {
        try {
            String cuerpo = json.writeValueAsString(confirmacion);
            return verificar(cuerpo, firmar(cuerpo));
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    public Confirmacion verificar(String cuerpo, String firma) {
        if (firma == null || !MessageDigest.isEqual(firmar(cuerpo).getBytes(StandardCharsets.UTF_8),
            firma.getBytes(StandardCharsets.UTF_8))) throw Regla.acceso();
        try {
            return json.readValue(cuerpo, Confirmacion.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw Regla.invalida("Confirmación de pasarela inválida");
        }
    }

    private String firmar(String cuerpo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(clave, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(cuerpo.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalStateException(error);
        }
    }
}
