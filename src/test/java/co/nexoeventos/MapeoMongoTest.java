package co.nexoeventos;

import java.time.Instant;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import co.nexoeventos.dominio.Compra;
import co.nexoeventos.dominio.Entrada;
import co.nexoeventos.dominio.Pago;
import co.nexoeventos.dominio.Reembolso;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.dominio.Dominio.EstadoCompra;
import co.nexoeventos.dominio.Dominio.EstadoCuenta;
import co.nexoeventos.dominio.Dominio.EstadoEntrada;
import co.nexoeventos.dominio.Dominio.EstadoReembolso;
import co.nexoeventos.dominio.Dominio.ResultadoPago;
import co.nexoeventos.dominio.Dominio.Rol;
import static org.assertj.core.api.Assertions.assertThat;

class MapeoMongoTest {
    private MappingMongoConverter conversor() {
        MongoCustomConversions conversiones=MongoCustomConversions.create(adaptador -> {});
        MongoMappingContext contexto=new MongoMappingContext();
        contexto.setSimpleTypeHolder(conversiones.getSimpleTypeHolder());
        contexto.afterPropertiesSet();
        MappingMongoConverter conversor=new MappingMongoConverter(NoOpDbRefResolver.INSTANCE,contexto);
        conversor.setCustomConversions(conversiones);
        conversor.afterPropertiesSet();
        return conversor;
    }

    @Test
    void conservaQrPagoYReembolsoEnBson() {
        Compra compra=new Compra();
        compra.id="orden-demo";
        compra.referenciaPago="pago-demo";
        compra.eventoId="evento-demo";
        compra.asistenteId="cliente-demo";
        compra.solicitudId="solicitud-demo";
        compra.cantidad=2;
        compra.precioCentavos=3550;
        Instant fecha=Instant.parse("2026-10-15T15:00:00Z");
        compra.pago=new Pago("pago-demo",ResultadoPago.APROBADO,fecha,"Tarjeta demo");
        compra.entradas.add(new Entrada("codigo-qr-demo",EstadoEntrada.VALIDA,null));
        compra.reembolsos.add(new Reembolso("reembolso-demo",3550,"Cancelación",List.of("entrada-cancelada"),
            EstadoReembolso.INICIADO,fecha,null));
        MappingMongoConverter conversor=conversor();
        Document bson=new Document();
        conversor.write(compra,bson);
        Compra leida=conversor.read(Compra.class,bson);
        assertThat(leida.pago).isEqualTo(compra.pago);
        assertThat(leida.entradas).isEqualTo(compra.entradas);
        assertThat(leida.reembolsos).isEqualTo(compra.reembolsos);
        assertThat(bson.getString("_id")).isEqualTo("orden-demo");
    }

    @Test
    void omiteDatosEliminadosYConservaPreferencias() {
        Usuario u=new Usuario();
        u.id="anonimo-demo";
        u.nombre="Usuario Anónimo";
        u.estado=EstadoCuenta.ANONIMIZADO;
        u.rol=Rol.ASISTENTE;
        u.temas.clear();
        MappingMongoConverter conversor=conversor();
        Document bson=new Document();
        conversor.write(u,bson);
        assertThat(bson).doesNotContainKeys("correo","clave","telefono","identificacion","direccion");
        Usuario anonimo=conversor.read(Usuario.class,bson);
        assertThat(anonimo.estado).isEqualTo(EstadoCuenta.ANONIMIZADO);
        assertThat(anonimo.temas).isEmpty();
    }
}
