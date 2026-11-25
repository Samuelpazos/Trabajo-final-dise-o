package co.nexoeventos.servicio;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import co.nexoeventos.dominio.Regla;
import co.nexoeventos.dominio.Dominio.*;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Actividad;
import co.nexoeventos.dominio.Aviso;
import co.nexoeventos.dominio.CambioServicio;
import co.nexoeventos.dominio.Compra;
import co.nexoeventos.dominio.Correo;
import co.nexoeventos.dominio.Entrada;
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Oferta;
import co.nexoeventos.dominio.Pago;
import co.nexoeventos.dominio.Reembolso;
import co.nexoeventos.dominio.Registro;
import co.nexoeventos.dominio.Solicitud;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.dominio.Actor;
import co.nexoeventos.dominio.Compra;
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Registro;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class ConsultaServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final EventoServicio eventos;
    private final UsuarioServicio usuarios;
    private final ProveedorServicio proveedores;
    private final CompraServicio compras;
    private final Clock reloj;

    public ConsultaServicio(Repositorio datos,Transacciones tx,EventoServicio eventos,UsuarioServicio usuarios,
            ProveedorServicio proveedores,CompraServicio compras,Clock reloj) {
        this.datos=datos; this.tx=tx; this.eventos=eventos; this.usuarios=usuarios;
        this.proveedores=proveedores; this.compras=compras; this.reloj=reloj;
    }

    public Map<String,Object> panel(Actor actor) {
        LocalDate hoy=LocalDate.ofInstant(reloj.instant(),ZONA);
        return switch (actor.rol()) {
            case ADMINISTRADOR -> datos("indicadores",datos("Cuentas activas",datos.por(Usuario.class,"estado",EstadoCuenta.ACTIVO).size(),
                "Cuentas inactivas",datos.por(Usuario.class,"estado",EstadoCuenta.INACTIVO).size(),
                "Eventos creados",datos.listar(Evento.class).size(),
                "Eventos activos",datos.por(Evento.class,"estado",EstadoEvento.ACTIVO).size(),
                "Eventos cancelados",datos.por(Evento.class,"estado",EstadoEvento.CANCELADO).size(),
                "Ingresos vigentes",total(datos.listar(Compra.class),null),
                "Solicitudes de privacidad",datos.listar(Usuario.class).stream()
                    .filter(u -> u.solicitudPrivacidad!=null && u.procesadoPrivacidad==null).count()),
                "eventos",eventos.gestionados(actor,null,null,"fecha").stream().limit(5).toList(),
                "auditoria",datos.listar(Registro.class).stream().sorted(Comparator.comparing(Registro::fecha).reversed()).limit(5).toList());
            case ORGANIZADOR -> {
                List<Evento> propios=eventos.alcance(actor);
                List<Compra> ventas=propios.stream().flatMap(e -> datos.por(Compra.class,"eventoId",e.id).stream()).toList();
                yield datos("indicadores",datos("Eventos activos",propios.stream().filter(e -> e.estado==EstadoEvento.ACTIVO).count(),
                    "Eventos inactivos",propios.stream().filter(e -> e.estado==EstadoEvento.INACTIVO).count(),
                    "Eventos cancelados",propios.stream().filter(e -> e.estado==EstadoEvento.CANCELADO).count(),
                    "Ventas este mes",total(ventas,hoy.withDayOfMonth(1)),"Servicios por confirmar",
                    proveedores.listar(actor,null,null).stream().filter(s -> Set.of(EstadoSolicitud.SOLICITADO,
                        EstadoSolicitud.PRESUPUESTADO,EstadoSolicitud.CONTRAPROPUESTA).contains(s.get("estado"))).count()),
                    "eventos",propios.stream().filter(e -> e.estado==EstadoEvento.ACTIVO && e.inicio.isAfter(reloj.instant())
                        && periodo(e.inicio,hoy,hoy.plusDays(7))).sorted(Comparator.comparing(e -> e.inicio))
                        .limit(5).map(e -> eventos.vista(e,true,false)).toList());
            }
            case ASISTENTE -> {
                List<Map<String,Object>> historial=compras.listar(actor);
                List<Compra> pagadas=datos.por(Compra.class,"asistenteId",actor.id()).stream().filter(CompraServicio::conPago).toList();
                Set<String> categorias=new HashSet<>();
                Set<String> vigentes=new HashSet<>();
                for (Compra c : pagadas) {
                    datos.id(Evento.class,c.eventoId).ifPresent(e -> categorias.add(e.categoria));
                    if (c.entradas.stream().anyMatch(t -> t.estado()==EstadoEntrada.VALIDA)) vigentes.add(c.eventoId);
                }
                yield datos("indicadores",datos("Mis compras",historial.size(),"Eventos guardados",
                    datos.exigir(Usuario.class,actor.id()).favoritos.size(),"Entradas válidas",
                    pagadas.stream().flatMap(c -> c.entradas.stream()).filter(t -> t.estado()==EstadoEntrada.VALIDA).count()),
                    "eventos",vigentes.stream().map(id -> datos.exigir(Evento.class,id))
                        .filter(e -> e.inicio.isAfter(reloj.instant()) && e.estado!=EstadoEvento.CANCELADO)
                        .sorted(Comparator.comparing(e -> e.inicio)).map(e -> eventos.vista(e,false,false)).toList(),
                    "favoritos",eventos.catalogo(actor,null,null,null,null,null,true),
                    "recomendados",eventos.catalogo(actor,null,null,null,null,null,false).stream()
                        .filter(e -> !vigentes.contains(e.get("id")))
                        .sorted(Comparator.comparing(e -> !categorias.contains(e.get("categoria")))).limit(3).toList());
            }
            case PROVEEDOR -> {
                List<Map<String,Object>> solicitudes=proveedores.listar(actor,null,null);
                List<Oferta> ofertas=datos.por(Oferta.class,"proveedorId",actor.id());
                long ingresos=datos.por(Solicitud.class,"proveedorId",actor.id()).stream()
                    .filter(s -> s.montoCentavos!=null && s.historial.stream().anyMatch(h -> h.estado()==EstadoSolicitud.CONFIRMADO &&
                        periodo(h.fecha(),hoy.withDayOfMonth(1),hoy))).mapToLong(s -> s.montoCentavos).sum();
                yield datos("indicadores",datos("Servicios activos",ofertas.stream().filter(o -> o.disponible).count(),
                    "Servicios inactivos",ofertas.stream().filter(o -> !o.disponible).count(),"Pendientes de respuesta",
                    solicitudes.stream().filter(s -> s.get("estado")==EstadoSolicitud.SOLICITADO ||
                        s.get("estado")==EstadoSolicitud.CONTRAPROPUESTA).count(),
                    "Servicios confirmados",solicitudes.stream().filter(s -> s.get("estado")==EstadoSolicitud.CONFIRMADO).count(),
                    "Importe acordado este mes",importe(ingresos)+" EUR"),"solicitudes",solicitudes.stream().limit(5).toList(),
                    "calendario",solicitudes.stream().filter(s -> s.get("estado")==EstadoSolicitud.CONFIRMADO)
                        .filter(s -> ((Instant)s.get("fechaEvento")).isAfter(reloj.instant()))
                        .sorted(Comparator.comparing(s -> (Instant)s.get("fechaEvento"))).limit(10).toList());
            }
        };
    }

    private String total(List<Compra> compras,LocalDate desde) {
        long centavos=compras.stream().filter(CompraServicio::conPago).filter(c -> periodo(c.pago.fecha(),desde,null))
            .mapToLong(c -> c.entradas.stream().filter(t -> t.estado()!=EstadoEntrada.CANCELADA).count()*c.precioCentavos).sum();
        return importe(centavos)+" EUR";
    }

    public Map<String,Object> buscar(Actor actor,String texto,String tipo,String categoria,String lugar,LocalDate desde,LocalDate hasta) {
        String q=texto(texto);
        if (q.length()>160) throw Regla.invalida("La búsqueda supera 160 caracteres");
        if (!q.isEmpty()) guardarBusqueda(actor,q,tipo,categoria,lugar,desde,hasta);
        List<Map<String,Object>> resultados=new ArrayList<>();
        if (incluye(tipo,"EVENTOS")) {
            if (actor.rol()==Rol.ORGANIZADOR || actor.rol()==Rol.ADMINISTRADOR) {
                eventos.alcance(actor).stream().filter(e -> coincide(e.nombre,q) || coincide(e.descripcion,q))
                    .filter(e -> coincide(e.categoria,categoria) && coincide(e.lugar,lugar) && periodo(e.inicio,desde,hasta))
                    .forEach(e -> resultados.add(resultado(e.nombre,"Evento",e.lugar,"evento/"+e.id)));
            } else {
                eventos.catalogo(actor,null,categoria,lugar,desde,hasta,false).stream()
                    .filter(e -> coincide((String)e.get("nombre"),q) || coincide((String)e.get("descripcion"),q))
                    .forEach(e -> resultados.add(resultado(e.get("nombre"),"Evento",e.get("lugar"),"evento/"+e.get("id"))));
            }
        }
        if (incluye(tipo,"PROVEEDORES") || incluye(tipo,"SERVICIOS")) {
            for (Map<String,Object> proveedor : proveedores.catalogo(actor,q,null,null,null)) {
                if (incluye(tipo,"PROVEEDORES")) resultados.add(resultado(proveedor.get("nombre"),
                    "Proveedor",proveedor.get("tipos"),"proveedores/"+proveedor.get("id")));
                if (incluye(tipo,"SERVICIOS")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String,Object>> ofertas=(List<Map<String,Object>>)proveedor.get("servicios");
                    for (Map<String,Object> oferta : ofertas) resultados.add(resultado(oferta.get("nombre"),
                        "Servicio",oferta.get("tipo"),"proveedores/"+proveedor.get("id")));
                }
            }
        }
        if (actor.rol()==Rol.ASISTENTE && incluye(tipo,"COMPRAS")) {
            compras.listar(actor).stream().filter(c -> coincide((String)c.get("evento"),q) || coincide((String)c.get("id"),q))
                .forEach(c -> resultados.add(resultado(c.get("evento"),"Compra",c.get("estado"),"compra/"+c.get("id"))));
        }
        if (actor.rol()==Rol.ADMINISTRADOR && incluye(tipo,"USUARIOS") && !q.isEmpty()) {
            usuarios.listar(actor,q).forEach(u -> resultados.add(resultado(u.get("nombre"),"Usuario",u.get("rol"),"usuario/"+u.get("id"))));
        }
        List<String> sugerencias=resultados.isEmpty() ? sugerir(actor,q) : List.of();
        return datos("resultados",resultados.stream().limit(120).toList(),"sugerencias",sugerencias);
    }

    private boolean incluye(String tipo,String esperado) {
        return texto(tipo).isEmpty() || "TODOS".equals(tipo) || esperado.equals(tipo);
    }

    private Map<String,Object> resultado(Object nombre,String tipo,Object detalle,String ruta) {
        return datos("nombre",nombre,"tipo",tipo,"detalle",detalle,"ruta",ruta);
    }

    private List<String> sugerir(Actor actor,String q) {
        List<String> candidatos=new ArrayList<>();
        eventos.catalogo(actor,null,null,null,null,null,false).forEach(e -> candidatos.add((String)e.get("nombre")));
        proveedores.catalogo(actor,null,null,null,null).forEach(p -> candidatos.add((String)p.get("nombre")));
        String consulta=q.toLowerCase(Locale.ROOT);
        return candidatos.stream().distinct().sorted(Comparator.comparingInt(n -> distancia(consulta,n.toLowerCase(Locale.ROOT))))
            .limit(3).toList();
    }

    private int distancia(String x,String y) {
        int[] anterior=new int[y.length()+1];
        for (int j=0;j<anterior.length;j++) anterior[j]=j;
        for (int i=1;i<=x.length();i++) {
            int[] actual=new int[y.length()+1];
            actual[0]=i;
            for (int j=1;j<=y.length();j++) actual[j]=Math.min(Math.min(actual[j-1]+1,anterior[j]+1),
                anterior[j-1]+(x.charAt(i-1)==y.charAt(j-1) ? 0 : 1));
            anterior=actual;
        }
        return anterior[y.length()];
    }

    private void guardarBusqueda(Actor actor,String q,String tipo,String categoria,String lugar,LocalDate desde,LocalDate hasta) {
        tx.ejecutar(() -> {
            Usuario u=datos.exigir(Usuario.class,actor.id());
            Map<String,String> entrada=new LinkedHashMap<>();
            entrada.put("q",q); entrada.put("tipo",texto(tipo)); entrada.put("categoria",texto(categoria));
            entrada.put("lugar",texto(lugar)); entrada.put("desde",Objects.toString(desde,"")); entrada.put("hasta",Objects.toString(hasta,""));
            u.busquedas.removeIf(r -> r.entrySet().containsAll(entrada.entrySet()));
            entrada.put("fecha",reloj.instant().toString());
            u.busquedas.addFirst(entrada);
            if (u.busquedas.size()>5) u.busquedas.removeLast();
            datos.guardar(u);
            return null;
        });
    }

    public List<Map<String,String>> recientes(Actor actor) { return datos.exigir(Usuario.class,actor.id()).busquedas; }
}
