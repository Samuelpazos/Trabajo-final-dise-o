package co.nexoeventos;

import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import co.nexoeventos.repositorio.Transacciones;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestConfiguration(proxyBeanMethods=false)
class ConfiguracionMemoria {
    @Bean
    static org.springframework.beans.factory.config.BeanFactoryPostProcessor sustituirMongo() {
        return fabrica -> {
            BeanDefinitionRegistry registro=(BeanDefinitionRegistry)fabrica;
            registro.removeBeanDefinition("repositorio");
            registro.removeBeanDefinition("transacciones");
        };
    }

    @Bean
    DatosEnMemoria memoria(ObjectMapper json) { return new DatosEnMemoria(json); }

    @Bean
    Transacciones transaccionesDePrueba(DatosEnMemoria memoria) {
        Transacciones tx=mock(Transacciones.class);
        when(tx.ejecutar(any())).thenAnswer(llamada -> memoria.ejecutar(llamada.<Supplier<?>>getArgument(0)));
        return tx;
    }
}
