package co.nexoeventos;

import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.query.Criteria;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.repositorio.Repositorio;

public class DatosEnMemoria extends Repositorio {
    private final ObjectMapper json;
    private Map<Class<?>,Map<String,Map<String,Object>>> colecciones=new HashMap<>();

    DatosEnMemoria(ObjectMapper json) {
        super(null);
        this.json=json;
    }

    public synchronized <T> T ejecutar(Supplier<T> operacion) {
        Map<Class<?>,Map<String,Map<String,Object>>> copia=new HashMap<>();
        colecciones.forEach((tipo,registros) -> copia.put(tipo,new LinkedHashMap<>(registros)));
        try { return operacion.get(); }
        catch (RuntimeException error) { colecciones=copia; throw error; }
    }

    @Override public synchronized <T> Optional<T> id(Class<T> tipo,String id) {
        return Optional.ofNullable(coleccion(tipo).get(id)).map(mapa -> json.convertValue(mapa,tipo));
    }

    @Override public synchronized <T> List<T> listar(Class<T> tipo,Criteria... criterios) {
        return coleccion(tipo).values().stream().filter(mapa -> Arrays.stream(criterios)
            .allMatch(criterio -> coincide(mapa,criterio.getCriteriaObject())))
            .map(mapa -> json.convertValue(mapa,tipo)).toList();
    }

    @Override public <T> List<T> por(Class<T> tipo,String campo,Object valor) {
        return listar(tipo,Criteria.where(campo).is(valor));
    }

    @Override public <T> Optional<T> uno(Class<T> tipo,String campo,Object valor) {
        return por(tipo,campo,valor).stream().findFirst();
    }

    @Override public synchronized <T> T guardar(T documento) {
        Map<String,Object> contenido=mapa(documento);
        String id=(String)contenido.get("id");
        if (documento instanceof Usuario usuario && usuario.correo!=null &&
            por(Usuario.class,"correo",usuario.correo).stream().anyMatch(u -> !u.id.equals(usuario.id))) {
            throw new DuplicateKeyException("Correo duplicado");
        }
        coleccion(documento.getClass()).put(id,contenido);
        return documento;
    }

    @Override public synchronized <T> T insertar(T documento) {
        if (coleccion(documento.getClass()).containsKey((String)mapa(documento).get("id")))
            throw new DuplicateKeyException("Identificador duplicado");
        return guardar(documento);
    }

    @Override public synchronized void eliminar(Object documento) {
        coleccion(documento.getClass()).remove((String)mapa(documento).get("id"));
    }

    @Override public synchronized void eliminarPor(Class<?> tipo,String campo,Object valor) {
        coleccion(tipo).values().removeIf(mapa -> igual(campo(mapa,campo),valor));
    }

    private Map<String,Map<String,Object>> coleccion(Class<?> tipo) {
        return colecciones.computeIfAbsent(tipo,ignorado -> new LinkedHashMap<>());
    }

    @SuppressWarnings("unchecked")
    private Map<String,Object> mapa(Object objeto) { return json.convertValue(objeto,Map.class); }

    private boolean coincide(Map<?,?> mapa,Map<String,Object> criterios) {
        for (var criterio : criterios.entrySet()) {
            Object real=campo(mapa,criterio.getKey());
            Object esperado=criterio.getValue();
            if (esperado instanceof Document operadores) {
                for (var op : operadores.entrySet()) {
                    boolean valido=switch(op.getKey()) {
                        case "$in" -> ((Collection<?>)op.getValue()).stream().anyMatch(v -> igual(real,v));
                        case "$gt" -> real!=null && instante(real).isAfter(instante(op.getValue()));
                        case "$lte" -> real!=null && !instante(real).isAfter(instante(op.getValue()));
                        case "$exists" -> (real!=null)==(boolean)op.getValue();
                        case "$elemMatch" -> real instanceof List<?> lista && lista.stream().anyMatch(item ->
                            item instanceof Map<?,?> sub && coincide(sub,(Document)op.getValue()));
                        default -> throw new IllegalArgumentException("Operador de pruebas desconocido: "+op.getKey());
                    };
                    if (!valido) return false;
                }
            } else if (!igual(real,esperado)) return false;
        }
        return true;
    }

    private Instant instante(Object valor) {
        if (valor instanceof Instant fecha) return fecha;
        return Instant.parse(valor.toString());
    }

    private boolean igual(Object real,Object esperado) {
        if (real instanceof List<?> lista) return lista.stream().anyMatch(e -> igual(e,esperado));
        return Objects.equals(real,esperado instanceof Enum<?> estado ? estado.name() : esperado);
    }

    private Object campo(Map<?,?> mapa,String ruta) {
        Object valor=mapa;
        for (String parte : ruta.split("\\.")) {
            if (valor instanceof Map<?,?> m) valor=m.get("_id".equals(parte) ? "id" : parte);
            else if (valor instanceof List<?> l && parte.equals("0") && !l.isEmpty()) valor=l.getFirst();
            else return null;
        }
        return valor;
    }
}
