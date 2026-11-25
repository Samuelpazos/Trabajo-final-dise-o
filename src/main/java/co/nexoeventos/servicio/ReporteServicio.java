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
import co.nexoeventos.repositorio.Repositorio;
import co.nexoeventos.servicio.ArchivoServicio.Tabla;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class ReporteServicio {
    private final Repositorio datos;
    private final EventoServicio eventos;
    private final UsuarioServicio usuarios;
    private final Clock reloj;

    public ReporteServicio(Repositorio datos,EventoServicio eventos,UsuarioServicio usuarios,Clock reloj) {
        this.datos=datos; this.eventos=eventos; this.usuarios=usuarios; this.reloj=reloj;
    }

    public Map<String,Object> ventas(Actor actor,String id,LocalDate desde,LocalDate hasta,String periodo) {
        Evento e=datos.exigir(Evento.class,id);
        EventoServicio.gestion(actor,e);
        Instant ahora=reloj.instant();
        Instant corte=switch(texto(periodo)) {
            case "24h" -> ahora.minus(Duration.ofHours(24));
            case "7d" -> ahora.minus(Duration.ofDays(7));
            default -> Instant.EPOCH;
        };
        List<Compra> compras=datos.por(Compra.class,"eventoId",id).stream().filter(CompraServicio::conPago)
            .filter(c -> !c.pago.fecha().isBefore(corte) && periodo(c.pago.fecha(),desde,hasta)).toList();
        Map<String,Long> serie=new TreeMap<>();
        long ingresos=0, vendidas=0;
        List<Map<String,Object>> compradores=new ArrayList<>();
        for (Compra c : compras) {
            long cantidad=vigentes(c);
            long total=cantidad*c.precioCentavos;
            ingresos+=total; vendidas+=cantidad;
            serie.merge(LocalDate.ofInstant(c.pago.fecha(),ZONA).toString(),total,Long::sum);
            compradores.add(datos("nombre",usuarios.nombre(c.asistenteId),"cantidad",cantidad,"fecha",c.pago.fecha(),
                "importe",importe(total),"estado",c.estado));
        }
        long ventasSemana=datos.por(Compra.class,"eventoId",id).stream().filter(CompraServicio::conPago)
            .filter(c -> c.pago.fecha().isAfter(ahora.minus(Duration.ofDays(7)))).mapToLong(this::vigentes).sum();
        double promedio=ventasSemana/7.0;
        long dias=Math.max(0,Duration.between(ahora,e.inicio).toDays());
        return datos("capacidad",e.capacidad,"vendidas",e.vendidas,"disponibles",e.disponibles(),"ocupacion",Math.round(e.vendidas*100.0/e.capacidad),
            "periodoVendidas",vendidas,"ingresos",importe(ingresos),"ticket",importe(vendidas==0 ? 0 : ingresos/vendidas),
            "serie",monedaSerie(serie),"compradores",compradores,"ritmo",Math.round(promedio*10)/10.0,
            "proyeccion",Math.min(e.capacidad,Math.round(e.vendidas+promedio*dias)));
    }

    public Tabla construir(Actor actor,String tipo,LocalDate desde,LocalDate hasta,String eventoId,
            String proveedor,String categoria,String periodo) {
        if (desde!=null && hasta!=null && hasta.isBefore(desde)) throw Regla.invalida("El final del período es anterior al inicio");
        if (!texto(eventoId).isEmpty()) EventoServicio.gestion(actor,datos.exigir(Evento.class,eventoId));
        List<Evento> seleccion=eventos.alcance(actor).stream()
            .filter(e -> texto(eventoId).isEmpty() || eventoId.equals(e.id))
            .filter(e -> coincide(e.categoria,categoria)).sorted(Comparator.comparing(e -> e.inicio)).toList();
        String rango="Período: "+(desde==null ? "inicio" : desde)+" a "+(hasta==null ? "hoy" : hasta);
        List<String> resumen=new ArrayList<>(List.of(rango,"Generado: "+reloj.instant().atZone(ZONA)));
        List<List<?>> filas=new ArrayList<>();
        Map<String,Long> serie=new TreeMap<>();
        switch (tipo) {
            case "ventas", "ingresos" -> {
                long unidades=0, ingresos=0;
                Instant corte=switch(texto(periodo)) {
                    case "24h" -> reloj.instant().minus(Duration.ofHours(24));
                    case "7d" -> reloj.instant().minus(Duration.ofDays(7));
                    default -> Instant.EPOCH;
                };
                for (Evento e : seleccion) {
                    long cantidad=0, monto=0;
                    for (Compra c : datos.por(Compra.class,"eventoId",e.id)) {
                        if (!CompraServicio.conPago(c) || c.pago.fecha().isBefore(corte) || !periodo(c.pago.fecha(),desde,hasta)) continue;
                        long vigentes=vigentes(c);
                        cantidad+=vigentes; monto+=vigentes*c.precioCentavos;
                        serie.merge(LocalDate.ofInstant(c.pago.fecha(),ZONA).toString(),vigentes*c.precioCentavos,Long::sum);
                    }
                    unidades+=cantidad; ingresos+=monto;
                    filas.add(List.of(e.nombre,LocalDate.ofInstant(e.inicio,ZONA).toString(),cantidad,importe(monto)));
                }
                resumen.add("Eventos: "+seleccion.size()+" · Entradas vendidas: "+unidades+" · Ingresos: "+importe(ingresos)+" EUR");
                return new Tabla("Reporte de "+tipo,resumen,List.of("Evento","Fecha","Entradas","Ingresos EUR"),filas,monedaSerie(serie));
            }
            case "asistencia" -> {
                int esperadas=0; long asistentes=0;
                for (Evento e : seleccion) {
                    if (!periodo(e.inicio,desde,hasta)) continue;
                    List<Compra> compras=datos.por(Compra.class,"eventoId",e.id);
                    long presentes=compras.stream().flatMap(c -> c.entradas.stream()).filter(t -> t.estado()==EstadoEntrada.USADA).count();
                    String nombres=compras.stream().filter(c -> c.entradas.stream().anyMatch(t -> t.estado()==EstadoEntrada.USADA))
                        .map(c -> usuarios.nombre(c.asistenteId)).distinct().reduce((a,b) -> a+", "+b).orElse("");
                    esperadas+=e.vendidas; asistentes+=presentes;
                    filas.add(List.of(e.nombre,e.capacidad,e.vendidas,presentes,
                        Math.round(presentes*100.0/(e.vendidas==0 ? 1 : e.vendidas)),nombres));
                    serie.merge(LocalDate.ofInstant(e.inicio,ZONA).toString(),presentes,Long::sum);
                }
                resumen.add("Asistentes registrados: "+asistentes+" de "+esperadas+" esperados");
                return new Tabla("Reporte de asistencia",resumen,List.of("Evento","Aforo","Esperados","Presentes","Asistencia %","Asistentes"),filas,serie);
            }
            case "servicios" -> {
                long total=0;
                int confirmados=0;
                Map<String,Long> porTipo=new TreeMap<>();
                Map<String,Long> porProveedor=new TreeMap<>();
                for (Evento e : seleccion) {
                    for (Solicitud s : datos.por(Solicitud.class,"eventoId",e.id)) {
                        String nombre=usuarios.nombre(s.proveedorId);
                        if (!periodo(s.creada,desde,hasta) || !(coincide(nombre,proveedor) || coincide(s.proveedorId,proveedor))) continue;
                        String tipoServicio=datos.id(Oferta.class,s.ofertaId).map(o -> o.tipo).orElse("General");
                        porProveedor.merge(nombre,1L,Long::sum);
                        if (s.montoCentavos!=null && (s.estado==EstadoSolicitud.CONFIRMADO || s.estado==EstadoSolicitud.FINALIZADO)) {
                            confirmados++;
                            total+=s.montoCentavos;
                            porTipo.merge(tipoServicio,s.montoCentavos,Long::sum);
                        }
                        filas.add(List.of(e.nombre,nombre,s.servicio,tipoServicio,s.estado.name(),
                            s.montoCentavos==null ? "" : importe(s.montoCentavos)));
                    }
                }
                resumen.add("Solicitudes: "+filas.size()+" · Confirmadas: "+confirmados+" · Total contratado: "+importe(total)+" EUR");
                porTipo.forEach((nombre,monto) -> resumen.add("Total de "+nombre+": "+importe(monto)+" EUR"));
                porProveedor.entrySet().stream().sorted(Map.Entry.<String,Long>comparingByValue().reversed()).limit(5)
                    .forEach(entrada -> resumen.add("Proveedor: "+entrada.getKey()+" · Solicitudes: "+entrada.getValue()));
                return new Tabla("Reporte de servicios",resumen,List.of("Evento","Proveedor","Servicio","Tipo","Estado","Monto EUR"),filas,Map.of());
            }
            default -> throw Regla.invalida("Tipo de reporte inválido");
        }
    }

    private long vigentes(Compra c) {
        return c.entradas.stream().filter(t -> t.estado()!=EstadoEntrada.CANCELADA).count();
    }

    private Map<String,java.math.BigDecimal> monedaSerie(Map<String,Long> serie) {
        Map<String,java.math.BigDecimal> montos=new LinkedHashMap<>();
        serie.forEach((dia,centavos) -> montos.put(dia,importe(centavos)));
        return montos;
    }
}
