package co.nexoeventos.controlador;

import org.springframework.http.*;

final class Descargas {
    private Descargas() {}
    static ResponseEntity<byte[]> archivo(byte[] bytes,String nombre,String tipo,boolean inline) {
        ContentDisposition disposicion=(inline ? ContentDisposition.inline() : ContentDisposition.attachment()).filename(nombre).build();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(tipo))
            .header(HttpHeaders.CONTENT_DISPOSITION,disposicion.toString()).body(bytes);
    }
}
