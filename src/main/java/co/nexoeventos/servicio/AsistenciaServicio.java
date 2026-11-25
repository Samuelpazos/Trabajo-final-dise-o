package co.nexoeventos.servicio;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
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
import co.nexoeventos.dominio.Entrada;
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.dominio.QrEntrada;
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.*;

@Service
public class AsistenciaServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final AuditoriaServicio auditoria;
    private final UsuarioServicio usuarios;
    private final Clock reloj;

    public AsistenciaServicio(Repositorio datos, Transacciones tx, AuditoriaServicio auditoria, UsuarioServicio usuarios, Clock reloj) {
        this.datos=datos; this.tx=tx; this.auditoria=auditoria; this.usuarios=usuarios; this.reloj=reloj;
    }

    public Map<String,Object> validar(Actor actor,String eventoId,String codigo) {
        return tx.ejecutar(() -> {
            Evento evento=datos.exigir(Evento.class,eventoId);
            EventoServicio.propietario(actor,evento);
            String valor=texto(codigo);
            boolean esQr=QrEntrada.esQr(valor);
            QrEntrada qr=esQr ? QrEntrada.leer(valor) : null;
            String token=qr==null ? valor : qr.entrada();
            Compra c=datos.uno(Compra.class,"codigos",token).orElse(null);
            Entrada t=c==null || (esQr && (qr==null || !qr.corresponde(c))) ? null :
                c.entradas.stream().filter(e -> e.codigo().equals(token)).findFirst().orElse(null);
            if (t==null || !eventoId.equals(c.eventoId) || t.estado()==EstadoEntrada.CANCELADA ||
                evento.estado==EstadoEvento.CANCELADO || evento.estado==EstadoEvento.FINALIZADO) {
                auditoria.registrar(actor,"QR_INVALIDO",eventoId,"RECHAZADO","Código inválido");
                return datos("estado",400,"mensaje","Código inválido, cancelado o de otro evento");
            }
            if (t.estado()==EstadoEntrada.USADA) {
                auditoria.registrar(actor,"QR_DUPLICADO",c.id,"RECHAZADO","Entrada ya utilizada");
                return datos("estado",409,"mensaje","Esta entrada ya fue utilizada","usada",t.usada());
            }
            Entrada usada=new Entrada(t.codigo(),EstadoEntrada.USADA,reloj.instant());
            c.entradas.set(c.entradas.indexOf(t),usada);
            datos.guardar(c);
            auditoria.exito(actor,"QR_VALIDADO",c.id,evento.id);
            return datos("estado",200,"mensaje","Entrada válida. Asistencia registrada. Bienvenida/o",
                "asistente",usuarios.nombre(c.asistenteId),"usada",usada.usada());
        });
    }

    public Map<String,Object> estadisticas(Actor actor,String eventoId) {
        Evento evento=datos.exigir(Evento.class,eventoId);
        EventoServicio.gestion(actor,evento);
        List<Map<String,Object>> ingresos=new ArrayList<>();
        for (Compra c : datos.por(Compra.class,"eventoId",eventoId))
            for (Entrada t : c.entradas)
                if (t.estado()==EstadoEntrada.USADA) ingresos.add(datos("asistente",usuarios.nombre(c.asistenteId),"fecha",t.usada()));
        ingresos.sort(Comparator.comparing((Map<String,Object> i) -> (Instant)i.get("fecha")).reversed());
        return datos("esperados",evento.vendidas,"presentes",ingresos.size(),"capacidad",evento.capacidad,
            "ocupacion",Math.round(ingresos.size()*100.0/evento.capacidad),"ultimos",ingresos.stream().limit(10).toList());
    }
}
