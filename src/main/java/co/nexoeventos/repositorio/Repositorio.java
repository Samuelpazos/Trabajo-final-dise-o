package co.nexoeventos.repositorio;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;
import co.nexoeventos.dominio.Regla;

@Repository
public class Repositorio {
    private final MongoTemplate mongo;

    public Repositorio(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    public <T> Optional<T> id(Class<T> tipo, String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(mongo.findById(id, tipo));
    }

    public <T> T exigir(Class<T> tipo, String id) {
        return id(tipo, id).orElseThrow(Regla::ausente);
    }

    public <T> List<T> listar(Class<T> tipo, Criteria... criterios) {
        Query consulta = new Query();
        for (Criteria criterio : criterios) consulta.addCriteria(criterio);
        return mongo.find(consulta, tipo);
    }

    public <T> List<T> por(Class<T> tipo, String campo, Object valor) {
        return listar(tipo, Criteria.where(campo).is(valor));
    }

    public <T> Optional<T> uno(Class<T> tipo, String campo, Object valor) {
        return Optional.ofNullable(mongo.findOne(Query.query(Criteria.where(campo).is(valor)), tipo));
    }

    public <T> T guardar(T documento) { return mongo.save(documento); }
    public <T> T insertar(T documento) { return mongo.insert(documento); }
    public void eliminar(Object documento) { mongo.remove(documento); }

    public void eliminarPor(Class<?> tipo, String campo, Object valor) {
        mongo.remove(Query.query(Criteria.where(campo).is(valor)), tipo);
    }
}
