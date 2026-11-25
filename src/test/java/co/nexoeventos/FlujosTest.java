package co.nexoeventos;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration,"
    +"org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration,"
    +"org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
    "server.port=0"
})
@Import(ConfiguracionMemoria.class)
@ActiveProfiles("demo")
class FlujosTest extends FlujosBase {}
