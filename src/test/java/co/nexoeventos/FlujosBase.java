package co.nexoeventos;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import com.fasterxml.jackson.databind.*;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import static org.assertj.core.api.Assertions.assertThat;
import static co.nexoeventos.dominio.Valores.datos;

abstract class FlujosBase {
    private static final String CLAVE="NexoDemo2026!";
    private static final String INVITADO="ClaveInvitado2026!";
    @LocalServerPort int puerto;
    @Autowired ObjectMapper json;
    @Autowired Clock reloj;

    record Resultado(int estado,byte[] bytes,ObjectMapper json) {
        JsonNode cuerpo() throws Exception { return bytes.length==0 ? json.nullNode() : json.readTree(bytes); }
        String texto() { return new String(bytes,StandardCharsets.UTF_8); }
        Resultado espera(int valor) { assertThat(estado).as(texto()).isEqualTo(valor); return this; }
    }

    class Cliente {
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        String csrf;

        Resultado enviar(String metodo,String ruta,Object cuerpo) throws Exception {
            HttpRequest.Builder peticion=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api"+ruta))
                .timeout(Duration.ofSeconds(15)).header("X-Nexo-Solicitud","web");
            if (csrf!=null) peticion.header("X-Nexo-CSRF",csrf);
            if (cuerpo!=null) peticion.header("Content-Type","application/json");
            HttpResponse<byte[]> respuesta=http.send(peticion.method(metodo,cuerpo==null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo))).build(),HttpResponse.BodyHandlers.ofByteArray());
            return new Resultado(respuesta.statusCode(),respuesta.body(),json);
        }
        Resultado ver(String ruta) throws Exception { return enviar("GET",ruta,null); }
        Resultado enviar(String ruta,Object cuerpo) throws Exception { return enviar("POST",ruta,cuerpo); }
        JsonNode ingresar(String correo,String clave) throws Exception {
            JsonNode sesion=enviar("/sesion",datos("correo",correo,"clave",clave)).espera(200).cuerpo();
            csrf=sesion.path("csrf").asText();
            return sesion.path("usuario");
        }
    }

    Cliente rol(String correo) throws Exception {
        Cliente c=new Cliente();
        c.ingresar(correo+"@nexo.test",CLAVE);
        return c;
    }

    JsonNode evento(Cliente c,int cupos) throws Exception {
        Instant inicio=reloj.instant().plus(Duration.ofDays(10));
        JsonNode e=c.enviar("/eventos",datos("nombre","Evento prueba "+UUID.randomUUID(),"descripcion","Descripción de prueba",
            "inicio",inicio,"fin",inicio.plus(Duration.ofHours(4)),"lugar","Bogotá","categoria","Música",
            "capacidad",cupos,"precio",35.50,"plazoHoras",48)).espera(201).cuerpo();
        assertThat(e.path("estado").asText()).isEqualTo("BORRADOR");
        return c.enviar("PATCH","/eventos/"+id(e)+"/estado",datos("estado","ACTIVO","confirmar",true)).espera(200).cuerpo();
    }

    String id(JsonNode v) { return v.path("id").asText(); }

    JsonNode reservar(Cliente c,String evento,int cantidad) throws Exception {
        return c.enviar("/compras",datos("eventoId",evento,"cantidad",cantidad,"solicitudId",UUID.randomUUID().toString()))
            .espera(201).cuerpo();
    }

    JsonNode pago(Cliente c,String id,String resultado) throws Exception {
        return c.enviar("/demo/compras/"+id+"/pago",datos("resultado",resultado)).espera(200).cuerpo();
    }

    String codigo(JsonNode compra,int n) { return compra.path("entradas").get(n).path("codigo").asText(); }

    @Test
    void cuatroRolesYPermisosDeServidor() throws Exception {
        for (String cuenta : List.of("admin","organizador","asistente","proveedor")) {
            Cliente cliente=rol(cuenta);
            cliente.ver("/panel").espera(200);
        }
        Cliente asistente=rol("asistente");
        asistente.ver("/usuarios").espera(403);
        asistente.ver("/auditoria").espera(403);
        asistente.ver("/eventos/gestion").espera(403);
        Cliente externo=new Cliente();
        externo.ver("/compras").espera(401);
        externo.enviar("/sesion",datos("correo","noexiste@nexo.test","clave","incorrecta"))
            .espera(401);
        assertThat(externo.enviar("/sesion",datos("correo","asistente@nexo.test","clave","incorrecta")).cuerpo()
            .path("mensaje").asText()).isEqualTo("Correo o contraseña incorrectos");
        externo.enviar("/pasarela/webhook",datos("tipo","pago","referencia","desconocida","resultado","APROBADO")).espera(403);
    }

    @Test
    void soloElOrganizadorPropietarioPuedeCambiarSuEvento() throws Exception {
        Cliente admin=rol("admin"), organizador=rol("organizador");
        String evento=id(evento(organizador,4));
        String correo="organizacion."+UUID.randomUUID()+"@nexo.test";
        admin.enviar("/usuarios",datos("nombre","Otra organización","correo",correo,"rol","ORGANIZADOR")).espera(201);
        JsonNode alta=admin.ver("/demo/buzon").espera(200).cuerpo().findParents("destinatario").stream()
            .filter(c -> c.path("destinatario").asText().equals(correo)).findFirst().orElseThrow();
        String token=alta.path("enlaces").get(0).asText().split("/#activar/")[1];
        Cliente otro=new Cliente();
        otro.enviar("/sesion/activar",datos("token",token,"clave",INVITADO)).espera(204);
        otro.ingresar(correo,INVITADO);
        String ruta="/eventos/"+evento;
        Object estado=datos("estado","INACTIVO","confirmar",true);
        otro.enviar("PATCH",ruta+"/estado",estado).espera(403);
        admin.enviar("PATCH",ruta+"/estado",estado).espera(403);
        otro.ver(ruta+"/ventas").espera(403);
        assertThat(otro.ver("/eventos/gestion").espera(200).cuerpo()).isEmpty();
        assertThat(organizador.enviar("PATCH",ruta+"/estado",estado).espera(200).cuerpo().path("estado").asText())
            .isEqualTo("INACTIVO");
    }

    @Test
    void reservaConcurrenteUltimoCupoYPagoRechazadoDevuelveCupo() throws Exception {
        Cliente organizador=rol("organizador"), asistente=rol("asistente");
        String evento=id(evento(organizador,1));
        try (var ejecutor=Executors.newFixedThreadPool(7)) {
            CountDownLatch partida=new CountDownLatch(1);
            List<Future<Resultado>> llamadas=new ArrayList<>();
            for (int i=0;i<7;i++) llamadas.add(ejecutor.submit(() -> {
                partida.await();
                return asistente.enviar("/compras",datos("eventoId",evento,"cantidad",1,"solicitudId",UUID.randomUUID().toString()));
            }));
            partida.countDown();
            List<Resultado> resultados=new ArrayList<>();
            for (Future<Resultado> llamada : llamadas) resultados.add(llamada.get(18,TimeUnit.SECONDS));
            assertThat(resultados).filteredOn(r -> r.estado==201).hasSize(1);
            assertThat(resultados).filteredOn(r -> r.estado==409).hasSize(6);
            JsonNode compra=resultados.stream().filter(r -> r.estado==201).findFirst().orElseThrow().cuerpo();
            JsonNode rechazada=pago(asistente,id(compra),"SIN_FONDOS");
            assertThat(rechazada.path("estado").asText()).isEqualTo("RECHAZADA");
            assertThat(asistente.ver("/eventos/"+evento).cuerpo().path("disponibles").asInt()).isEqualTo(1);
            assertThat(reservar(asistente,evento,1).path("estado").asText()).isEqualTo("RESERVADA");
        }
    }

    @Test
    void pagoDuplicadoQrRealYReutilizacionCancelacionParcialYReembolsos() throws Exception {
        Cliente organizador=rol("organizador"), asistente=rol("asistente"), admin=rol("admin");
        String evento=id(evento(organizador,9));
        String compra=id(reservar(asistente,evento,5));
        JsonNode pagada=pago(asistente,compra,"APROBADO");
        JsonNode duplicada=pago(asistente,compra,"APROBADO");
        assertThat(pagada.path("entradas")).hasSize(5).isEqualTo(duplicada.path("entradas"));
        String base="/compras/"+compra;
        String valido=codigo(pagada,0), cancelado=codigo(pagada,1), segundo=codigo(pagada,2);
        try (var pdf=Loader.loadPDF(asistente.ver(base+"/comprobante").espera(200).bytes())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("NIF emisor ficticio","177.50","Comprobante de compra");
        }
        byte[] png=asistente.ver(base+"/qr/"+valido).espera(200).bytes();
        var img=ImageIO.read(new java.io.ByteArrayInputStream(png));
        String leido=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(img)))).getText();
        assertThat(leido).startsWith("NEXO1|"+evento+"|"+compra+"|").endsWith("|"+valido);
        asistente.enviar("/eventos/"+evento+"/asistencia",datos("codigo",valido)).espera(403);
        String rutaQr="/eventos/"+evento+"/asistencia";
        organizador.enviar(rutaQr,datos("codigo",leido)).espera(200);
        assertThat(organizador.enviar(rutaQr,datos("codigo",valido)).espera(409).cuerpo().path("mensaje").asText())
            .contains("ya fue utilizada");
        Object seleccion=datos("entradas",List.of(cancelado,segundo),"confirmar",true);
        JsonNode parcial=asistente.enviar(base+"/cancelaciones",seleccion).espera(200).cuerpo();
        JsonNode repetida=asistente.enviar(base+"/cancelaciones",seleccion).espera(200).cuerpo();
        assertThat(parcial.path("estado").asText()).isEqualTo("CANCELADA_PARCIAL");
        assertThat(parcial.path("reembolsos")).hasSize(1).isEqualTo(repetida.path("reembolsos"));
        JsonNode r=parcial.path("reembolsos").get(0);
        assertThat(r.path("monto").asDouble()).isEqualTo(71.0);
        assertThat(parcial.path("entradas").get(3).path("estado").asText()).isEqualTo("VALIDA");
        organizador.enviar(rutaQr,datos("codigo",cancelado)).espera(400);
        assertThat(asistente.ver("/eventos/"+evento).cuerpo().path("disponibles").asInt()).isEqualTo(6);
        String confirmacion="/demo"+base+"/reembolsos/"+id(r);
        admin.enviar(confirmacion,datos("resultado","COMPLETADO")).espera(204);
        admin.enviar(confirmacion,datos("resultado","COMPLETADO")).espera(204);
        assertThat(asistente.ver(base).cuerpo().path("reembolsos").get(0).path("estado").asText()).isEqualTo("COMPLETADO");
        JsonNode buzon=admin.ver("/demo/buzon").espera(200).cuerpo();
        assertThat(buzon.findValuesAsText("id").stream().filter(x -> x.startsWith(id(r)+":COMPLETADO"))).hasSize(1);
        assertThat(buzon.findValuesAsText("id").stream().filter(x -> x.startsWith(compra+":PAGO"))).hasSize(1);
        organizador.enviar("/eventos/"+evento+"/cancelacion",datos("motivo","Cambio de recinto","confirmar",true)).espera(200);
        JsonNode total=asistente.ver(base).cuerpo();
        assertThat(total.path("reembolsos")).hasSize(2);
        JsonNode restante=total.path("reembolsos").get(1);
        assertThat(restante.path("monto").asDouble()).isEqualTo(106.5);
        admin.enviar("/demo"+base+"/reembolsos/"+id(restante),datos("resultado","FALLIDO")).espera(204);
        assertThat(asistente.ver(base).cuerpo().path("reembolsos").get(1).path("estado").asText()).isEqualTo("FALLIDO");
        assertThat(admin.ver("/auditoria?accion=QR_DUPLICADO").espera(200).cuerpo()).isNotEmpty();
    }

    @Test
    void gestionEventoCatalogoAgendaYExportacion() throws Exception {
        Cliente organizador=rol("organizador"), asistente=rol("asistente"), proveedor=rol("proveedor");
        JsonNode e=evento(organizador,10);
        String ruta="/eventos/"+id(e);
        String inicio=e.path("inicio").asText();
        Object actividad=datos("nombre","Actividad de prueba","descripcion","Descripción","lugar","Sala", "inicio",inicio,
            "minutos",30,"responsable","Equipo","capacidad",5);
        proveedor.enviar(ruta+"/actividades",actividad).espera(403);
        organizador.enviar(ruta+"/actividades",actividad).espera(201);
        assertThat(asistente.ver(ruta).cuerpo().path("actividades")).hasSize(1);
        String consulta="/eventos?categoria=M%C3%BAsica&lugar=Bogot%C3%A1&nombre="+URLEncoder.encode(e.path("nombre").asText(),StandardCharsets.UTF_8);
        assertThat(asistente.ver(consulta).espera(200).cuerpo()).hasSize(1);
        asistente.enviar("PUT",ruta+"/favorito",null).espera(204);
        organizador.enviar("PATCH",ruta+"/estado",datos("estado","INACTIVO","confirmar",true)).espera(200);
        assertThat(asistente.ver(consulta).cuerpo()).isEmpty();
        assertThat(asistente.ver("/eventos?favoritos=true").cuerpo().findValuesAsText("id")).contains(id(e));
        organizador.enviar("PATCH",ruta+"/estado",datos("estado","ACTIVO","confirmar",true)).espera(200);
        String compra=id(reservar(asistente,id(e),1));
        pago(asistente,compra,"APROBADO");
        assertThat(organizador.ver(ruta+"/ventas").espera(200).cuerpo().path("ingresos").asDouble()).isEqualTo(35.50);
        try (var libro=new XSSFWorkbook(new java.io.ByteArrayInputStream(organizador.ver(
            "/reportes?tipo=ventas&formato=xlsx&eventoId="+id(e)).espera(200).bytes()))) {
            assertThat(libro.getNumberOfSheets()).isEqualTo(2);
            assertThat(libro.getSheet("Evolución diaria").getDrawingPatriarch().getCharts()).hasSize(1);
        }
        assertThat(rol("admin").ver("/auditoria/archivo?formato=csv").espera(200).texto()).contains("EVENTO_CREADO");
    }

    @Test
    void presupuestoContrapropuestaConfirmacionYAcuerdo() throws Exception {
        Cliente organizador=rol("organizador"), proveedor=rol("proveedor"), asistente=rol("asistente");
        String evento=id(evento(organizador,10));
        JsonNode catalogo=organizador.ver("/proveedores?tipo=Sonido&minimo=300").espera(200).cuerpo();
        assertThat(catalogo.findValuesAsText("nombre")).contains("Estudio Norte","Sonido en directo");
        JsonNode creada=organizador.enviar("/solicitudes",datos("eventoId",evento,"ofertaId","sonido-directo",
            "observaciones","Incluye montaje")).espera(201).cuerpo();
        String ruta="/solicitudes/"+id(creada);
        asistente.ver(ruta).espera(403);
        assertThat(responder(proveedor,ruta,"PRESUPUESTADO",380,true).path("estado").asText()).isEqualTo("PRESUPUESTADO");
        JsonNode contra=responder(organizador,ruta,"CONTRAPROPUESTA",350,false);
        assertThat(contra.path("historial")).hasSize(3);
        String adjunto=contra.path("historial").get(1).path("adjuntoId").asText();
        organizador.ver(ruta+"/adjuntos/"+adjunto).espera(200);
        assertThat(responder(proveedor,ruta,"CONFIRMADO",null,false).path("monto").asDouble()).isEqualTo(350.0);
        organizador.ver(ruta+"/acuerdo").espera(200);
        assertThat(responder(organizador,ruta,"FINALIZADO",null,false).path("estado").asText()).isEqualTo("FINALIZADO");
    }

    @Test
    void ofertaEditableYBusquedaConHistorialPorCuenta() throws Exception {
        Cliente proveedor=rol("proveedor"), organizador=rol("organizador"), asistente=rol("asistente");
        String nombre="Logística de prueba "+UUID.randomUUID();
        LocalDate hoy=LocalDate.now(reloj);
        var oferta=datos("nombre",nombre,"tipo","Logística","descripcion","Puestos para conferencias",
            "precio",123.50,"desde",hoy,"hasta",hoy.plusYears(1),"disponible",true);
        String id=id(proveedor.enviar("/ofertas",oferta).espera(201).cuerpo());
        String q=URLEncoder.encode(nombre,StandardCharsets.UTF_8);
        assertThat(organizador.ver("/proveedores?filtro="+q).espera(200).cuerpo().findValuesAsText("nombre")).contains(nombre);
        assertThat(organizador.ver("/busqueda?tipo=SERVICIOS&q="+q).espera(200).cuerpo().path("resultados")).hasSize(1);
        assertThat(organizador.ver("/busqueda/recientes").espera(200).cuerpo().findValuesAsText("q")).contains(nombre);
        assertThat(asistente.ver("/busqueda/recientes").espera(200).cuerpo().findValuesAsText("q")).doesNotContain(nombre);
        assertThat(asistente.ver("/busqueda?tipo=USUARIOS&q=Valentina").espera(200).cuerpo().path("resultados")).isEmpty();
        oferta.put("disponible",false);
        proveedor.enviar("PUT","/ofertas/"+id,oferta).espera(200);
        assertThat(organizador.ver("/proveedores?filtro="+q).espera(200).cuerpo()).isEmpty();
        proveedor.enviar("DELETE","/ofertas/"+id,null).espera(204);
        assertThat(proveedor.ver("/ofertas").espera(200).cuerpo().findValuesAsText("id")).doesNotContain(id);
    }

    JsonNode responder(Cliente cliente,String ruta,String estado,Number monto,boolean pdf) throws Exception {
        String limite="Nexo-Prueba";
        String cuerpo="--"+limite+"\r\nContent-Disposition: form-data; name=\"datos\"\r\nContent-Type: application/json\r\n\r\n"
            +json.writeValueAsString(datos("estado",estado,"monto",monto,"comentario","Condiciones para evento"))+"\r\n";
        if (pdf) cuerpo+="--"+limite+"\r\nContent-Disposition: form-data; name=\"archivo\"; filename=\"presupuesto.pdf\"\r\n"
            +"Content-Type: application/pdf\r\n\r\n%PDF-1.7\r\n%%EOF\r\n";
        cuerpo+="--"+limite+"--\r\n";
        HttpRequest peticion=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+puerto+"/api"+ruta+"/respuestas"))
            .header("X-Nexo-Solicitud","web").header("X-Nexo-CSRF",cliente.csrf)
            .header("Content-Type","multipart/form-data; boundary="+limite)
            .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build();
        var respuesta=cliente.http.send(peticion,HttpResponse.BodyHandlers.ofByteArray());
        return new Resultado(respuesta.statusCode(),respuesta.body(),json).espera(200).cuerpo();
    }

    @Test
    void altaEdicionBajaPreferenciasYAnonimizacion() throws Exception {
        Cliente admin=rol("admin"), usuario=new Cliente();
        String correo="invitado."+UUID.randomUUID()+"@nexo.test";
        var datos=datos("nombre","Persona de Prueba","correo",correo,"rol","ASISTENTE","telefono","+57 311 123 4567");
        String id=id(admin.enviar("/usuarios",datos).espera(201).cuerpo());
        admin.enviar("/usuarios",datos).espera(409);
        JsonNode correoAlta=admin.ver("/demo/buzon").cuerpo().findParents("destinatario").stream()
            .filter(x -> x.path("destinatario").asText().equals(correo)).findFirst().orElseThrow();
        String token=correoAlta.path("enlaces").get(0).asText().split("/#activar/")[1];
        usuario.enviar("/sesion/activar",datos("token",token,"clave",INVITADO)).espera(204);
        usuario.enviar("/sesion/activar",datos("token",token,"clave",INVITADO)).espera(400);
        usuario.ingresar(correo,INVITADO);
        admin.enviar("PATCH","/usuarios/"+id+"/estado",datos("estado","INACTIVO","confirmar",true)).espera(200);
        usuario.ver("/sesion").espera(401);
        admin.enviar("PATCH","/usuarios/"+id+"/estado",datos("estado","ACTIVO","confirmar",true)).espera(200);
        usuario.ingresar(correo,INVITADO);
        datos.put("nombre","Nombre Actualizado");
        admin.enviar("PUT","/usuarios/"+id,datos).espera(200);
        usuario.enviar("PUT","/perfil/preferencias",datos("canal","PLATAFORMA","temas",List.of())).espera(200);
        assertThat(usuario.ver("/perfil/preferencias").cuerpo().path("temas")).isEmpty();
        usuario.enviar("/perfil/privacidad",datos("clave",INVITADO,"confirmar",true)).espera(204);
        admin.enviar("PATCH","/usuarios/"+id+"/estado",datos("estado","BAJA_LOGICA","confirmar",true)).espera(200);
        usuario.enviar("/sesion",datos("correo",correo,"clave",INVITADO)).espera(401);
        JsonNode anonima=admin.enviar("/usuarios/"+id+"/anonimizacion",datos("estado","ANONIMIZADO","confirmar",true)).espera(200).cuerpo();
        assertThat(anonima.path("nombre").asText()).startsWith("Usuario Anónimo");
        assertThat(anonima.path("correo").isNull()).isTrue();
        assertThat(usuario.enviar("/sesion",datos("correo",correo,"clave",INVITADO)).espera(401).cuerpo().path("mensaje").asText())
            .isEqualTo("Correo o contraseña incorrectos");
        assertThat(admin.ver("/auditoria").texto()).doesNotContain(correo,"+57 311 123 4567","Nombre Actualizado");
    }

    @Test
    @EnabledIfEnvironmentVariable(named="NEXO_CONTROL_NAVEGADOR",matches=".+")
    void servidorParaNavegador() throws Exception {
        Path control=Path.of(System.getenv("NEXO_CONTROL_NAVEGADOR"));
        Files.writeString(control,Integer.toString(puerto));
        Instant limite=Instant.now().plusSeconds(180);
        while (Files.exists(control) && Instant.now().isBefore(limite)) Thread.sleep(250);
        assertThat(Files.exists(control)).as("La comprobación del navegador no terminó a tiempo").isFalse();
    }
}
