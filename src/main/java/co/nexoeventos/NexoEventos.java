package co.nexoeventos;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class NexoEventos {
    public static void main(String[] args) {
        SpringApplication aplicacion = new SpringApplication(NexoEventos.class);
        aplicacion.addInitializers(contexto -> {
            String uri = contexto.getEnvironment().getProperty("MONGODB_URI", "");
            if (uri.isBlank()) {
                throw new IllegalStateException("Configura MONGODB_URI con la conexión de Atlas en el archivo .env");
            }
        });
        aplicacion.run(args);
    }

    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }

    @Bean
    BCryptPasswordEncoder contrasenas() {
        return new BCryptPasswordEncoder(12);
    }
}
