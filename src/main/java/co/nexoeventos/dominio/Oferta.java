package co.nexoeventos.dominio;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("ofertas")
public class Oferta {
    @Id public String id;
    @Indexed public String proveedorId;
    public String nombre, descripcion, tipo;
    public long precioCentavos;
    public String desde, hasta;
    public boolean disponible = true;
}
