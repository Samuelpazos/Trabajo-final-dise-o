package co.nexoeventos.dominio;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static co.nexoeventos.dominio.Dominio.*;

public final class Valores {
    public static final ZoneId ZONA = ZoneId.of("America/Bogota");
    private static final SecureRandom AZAR = new SecureRandom();

    private Valores() {}
    public static String id() { return UUID.randomUUID().toString(); }
    public static String secreto() {
        byte[] bits = new byte[32];
        AZAR.nextBytes(bits);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bits);
    }
    public static String huella(String valor) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
    public static String texto(String valor) { return valor == null ? "" : valor.strip(); }
    public static long centavos(BigDecimal valor) { return valor.movePointRight(2).longValueExact(); }
    public static BigDecimal importe(long centavos) { return BigDecimal.valueOf(centavos,2); }
    public static boolean coincide(String valor, String filtro) {
        return texto(valor).toLowerCase(Locale.ROOT).contains(texto(filtro).toLowerCase(Locale.ROOT));
    }
    public static boolean periodo(Instant fecha, LocalDate desde, LocalDate hasta) {
        if (fecha == null) return false;
        LocalDate dia = fecha.atZone(ZONA).toLocalDate();
        return (desde == null || !dia.isBefore(desde)) && (hasta == null || !dia.isAfter(hasta));
    }
    public static void rol(Actor actor, Rol... roles) {
        if (Arrays.stream(roles).noneMatch(actor.rol()::equals)) throw Regla.acceso();
    }
    public static Map<String,Object> datos(Object... pares) {
        Map<String,Object> mapa = new LinkedHashMap<>();
        for (int i = 0; i < pares.length; i += 2) mapa.put((String) pares[i], pares[i+1]);
        return mapa;
    }
}
