package co.nexoeventos;

import com.mongodb.ConnectionString;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named="NEXO_ATLAS_URI_PRUEBAS",matches="mongodb\\+srv://.+")
@SpringBootTest(classes=NexoEventos.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="server.port=0")
@ActiveProfiles("demo")
class FlujosAtlasTest extends FlujosBase {
    @DynamicPropertySource
    static void usarAtlas(DynamicPropertyRegistry propiedades) {
        String uri=System.getenv("NEXO_ATLAS_URI_PRUEBAS");
        String base;
        try { base=new ConnectionString(uri).getDatabase(); }
        catch (IllegalArgumentException error) { throw new IllegalStateException("Revisa el formato de la URI de pruebas"); }
        if (base==null || !base.endsWith("_pruebas"))
            throw new IllegalStateException("La URI de integración debe incluir una base de datos que termine en _pruebas");
        propiedades.add("spring.data.mongodb.uri",() -> uri);
        propiedades.add("spring.data.mongodb.database",() -> base);
    }
}
