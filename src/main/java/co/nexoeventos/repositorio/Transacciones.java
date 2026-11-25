package co.nexoeventos.repositorio;

import com.mongodb.MongoException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class Transacciones {
    private final TransactionTemplate transaccion;

    public Transacciones(MongoDatabaseFactory fabrica) {
        transaccion = new TransactionTemplate(new MongoTransactionManager(fabrica) {
            @Override
            protected void doCommit(MongoTransactionObject objeto) throws Exception {
                for (int intento = 1; ; intento++) {
                    try {
                        super.doCommit(objeto);
                        return;
                    } catch (MongoException error) {
                        if (intento == 5 || !error.hasErrorLabel("UnknownTransactionCommitResult")) throw error;
                    }
                }
            }
        });
    }

    public <T> T ejecutar(Supplier<T> operacion) {
        for (int intento = 1; ; intento++) {
            try {
                return transaccion.execute(estado -> operacion.get());
            } catch (RuntimeException error) {
                if (intento == 12 || !esTransitorio(error)) throw error;
                try {
                    Thread.sleep(ThreadLocalRandom.current().nextLong(5, 12L * intento));
                } catch (InterruptedException interrupcion) {
                    Thread.currentThread().interrupt();
                    throw error;
                }
            }
        }
    }

    private boolean esTransitorio(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof MongoException mongo &&
                (mongo.hasErrorLabel("TransientTransactionError") || mongo.getCode() == 112)) return true;
        }
        return false;
    }
}
