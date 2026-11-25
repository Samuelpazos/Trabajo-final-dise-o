package co.nexoeventos.servicio;

import java.time.*;
import java.util.List;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.scheduling.annotation.Scheduled;
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
import co.nexoeventos.dominio.Evento;
import co.nexoeventos.dominio.Usuario;
import co.nexoeventos.repositorio.*;
import static co.nexoeventos.dominio.Valores.ZONA;

@Service
public class RecordatorioServicio {
    private final Repositorio datos;
    private final Transacciones tx;
    private final NotificacionServicio notificaciones;
    private final Clock reloj;

    public RecordatorioServicio(Repositorio datos,Transacciones tx,NotificacionServicio notificaciones,Clock reloj) {
        this.datos=datos; this.tx=tx; this.notificaciones=notificaciones; this.reloj=reloj;
    }

    @Scheduled(initialDelay=60000,fixedDelay=60000)
    public void enviar() {
        Instant ahora=reloj.instant();
        for (Evento evento : datos.listar(Evento.class,Criteria.where("inicio").lte(ahora.plus(Duration.ofHours(24))),
                Criteria.where("fin").gt(ahora),Criteria.where("estado").in(EstadoEvento.ACTIVO,EstadoEvento.INACTIVO))) {
            boolean cerca=ventana(ahora,evento.inicio,24);
            List<Actividad> actividades=evento.actividades.stream().filter(a -> ventana(ahora,a.inicio(),1)).toList();
            if (!cerca && actividades.isEmpty()) continue;
            for (Compra compra : datos.por(Compra.class,"eventoId",evento.id)) {
                if (compra.entradas.stream().noneMatch(t -> t.estado()!=EstadoEntrada.CANCELADA)) continue;
                tx.ejecutar(() -> {
                    if (cerca) notificaciones.enviarUnaVez(compra.asistenteId,"RECORDATORIO:"+evento.id,"Recordatorio de evento",
                        evento.nombre+" comienza el "+evento.inicio.atZone(ZONA),Tema.RECORDATORIOS,"/#evento/"+evento.id);
                    for (Actividad a : actividades)
                        notificaciones.enviarUnaVez(compra.asistenteId,"RECORDATORIO:"+a.id(),"Recordatorio de actividad",
                            a.nombre()+" comienza el "+a.inicio().atZone(ZONA),Tema.RECORDATORIOS_ACTIVIDAD,"/#evento/"+evento.id);
                    return null;
                });
            }
        }
    }

    private boolean ventana(Instant ahora,Instant fecha,long horas) {
        Instant objetivo=fecha.minus(Duration.ofHours(horas));
        return !ahora.isBefore(objetivo) && ahora.isBefore(objetivo.plus(Duration.ofMinutes(30)));
    }
}
