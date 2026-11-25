import { $, $$, api, escapar, moneda, fecha, estado, esRol, consulta, enlace, boton, vacio, titulo,
  caja, acciones, indicador, campo, area, select, check, formulario, tabla, definiciones, ir, codigo,
  alEnviar, alClic, mostrar, modal, mensaje, localFecha, aInstante, descarga, actual, rutaActiva } from './comun.js';

export const categorias = ['', 'Música', 'Cultura', 'Tecnología', 'Educación', 'Deportes', 'Otra']
  .map(c => [c, c || 'Todas']);
const estados = [['', 'Todos'], 'BORRADOR', 'ACTIVO', 'INACTIVO', 'CANCELADO', 'FINALIZADO'];

export function tarjetas(eventos) {
  return eventos.length ? '<div class="grilla">' + eventos.map(e => '<article class="superficie tarjeta-evento">' +
    '<header class="friso" data-categoria="' + escapar(e.categoria) + '"><strong>' + escapar(e.categoria) + '</strong></header>' +
    '<div class="tarjeta-interior"><h2>' + enlace('evento/' + codigo(e.id), e.nombre, '') + '</h2><p>' + escapar(fecha(e.inicio)) +
    ' · ' + escapar(e.lugar) + '</p><div class="flex espacio"><strong>' + moneda(e.precio) + '</strong>' +
    (e.estado === 'ACTIVO' ? '<span class="secundario">' + e.disponibles + ' entradas disponibles</span>' : estado(e.estado)) +
    '</div>' + acciones(enlace('evento/' + codigo(e.id), 'Ver evento')) + '</div></article>').join('') + '</div>' : '';
}

export async function catalogo(favoritos = false) {
  const filtros = campo('nombre', 'Nombre del evento') + select('categoria', 'Categoría', categorias) +
    campo('desde', 'Desde', 'date') + campo('hasta', 'Hasta', 'date') + campo('lugar', 'Ubicación');
  mostrar(titulo(favoritos ? 'Mis favoritos' : 'Explorar eventos', 'Consulta fechas, lugares y disponibilidad.',
    favoritos ? enlace('eventos', 'Explorar eventos') : enlace('favoritos', 'Mis favoritos')) +
    caja(formulario('filtros', filtros, 'Aplicar filtros', boton('Limpiar filtros', 'limpiar'))) + '<div id="resultadosEventos"></div>');
  const actualizar = async (datos = {}) => {
    const lista = await api('/eventos' + consulta({ ...datos, favoritos }));
    const salida = $('#resultadosEventos');
    if (salida) salida.innerHTML = tarjetas(lista) || caja(vacio('No hay eventos que coincidan con tu búsqueda'));
  };
  alEnviar('filtros', actualizar);
  alClic('limpiar', async () => { $('#filtros').reset(); await actualizar(); });
  await actualizar();
}

export async function gestion() {
  mostrar(titulo(esRol('ADMINISTRADOR') ? 'Eventos del sistema' : 'Mis eventos', 'Gestiona el estado, el aforo y la agenda.',
    esRol('ORGANIZADOR') ? enlace('nuevo-evento', 'Crear evento', 'boton primario') : '') +
    caja(formulario('filtros', campo('filtro', 'Buscar por nombre') + select('estado', 'Estado', estados) +
      select('orden', 'Ordenar por', [['fecha', 'Fecha'], ['nombre', 'Nombre'], ['estado', 'Estado']]), 'Aplicar filtros')) +
    '<div id="listaGestion"></div>');
  const actualizar = async (datos = {}) => {
    const lista = await api('/eventos/gestion' + consulta(datos));
    const salida = $('#listaGestion');
    if (salida) salida.innerHTML = caja(tabla(['Evento', 'Fecha', 'Lugar', 'Aforo', 'Vendidas', 'Estado'], lista.map(e => [
      enlace('evento/' + codigo(e.id), e.nombre, ''), escapar(fecha(e.inicio)), escapar(e.lugar), e.capacidad, e.vendidas, estado(e.estado)
    ]), 'No hay eventos para este filtro'));
  };
  alEnviar('filtros', actualizar);
  await actualizar();
}

function camposEvento(e = {}) {
  return campo('nombre', 'Nombre del evento', 'text', e.nombre, 'required maxlength="150"') +
    select('categoria', 'Categoría', [['', 'Selecciona'], ...categorias.slice(1)], e.categoria, 'required') +
    campo('inicio', 'Fecha y hora de inicio', 'datetime-local', localFecha(e.inicio), 'required') +
    campo('fin', 'Fecha y hora de fin', 'datetime-local', localFecha(e.fin), 'required') +
    campo('lugar', 'Lugar', 'text', e.lugar, 'required maxlength="200"') + campo('capacidad', 'Aforo', 'number', e.capacidad, 'required min="1"') +
    campo('precio', 'Precio por entrada (EUR)', 'number', e.precio, 'required min="0" step="0.01"') +
    campo('plazoHoras', 'Plazo de cancelación (horas antes)', 'number', e.plazoHoras, 'min="0" max="8760" placeholder="Plazo general"') +
    area('descripcion', 'Descripción', e.descripcion, 'maxlength="6000"');
}

export async function editarEvento(id) {
  const e = id ? await api('/eventos/' + codigo(id)) : {};
  mostrar(titulo(id ? 'Editar evento' : 'Crear evento', 'Introduce la fecha y la hora de Bogotá.') +
    caja(formulario('formEvento', camposEvento(e), 'Guardar evento', enlace(id ? 'evento/' + codigo(id) : 'gestion', 'Cancelar'))));
  alEnviar('formEvento', async datos => {
    const guardado = await api('/eventos' + (id ? '/' + codigo(id) : ''), id ? 'PUT' : 'POST', {
      ...datos, inicio: aInstante(datos.inicio), fin: aInstante(datos.fin),
      capacidad: Number(datos.capacidad), precio: Number(datos.precio), plazoHoras: datos.plazoHoras === '' ? null : Number(datos.plazoHoras)
    });
    mensaje(guardado.aviso ?? (id ? 'Evento actualizado' : 'Evento registrado como borrador'));
    ir('evento/' + codigo(guardado.id));
  });
}

function agenda(e) {
  const actividades = e.actividades ?? [];
  return caja('<div class="flex espacio"><h2>Programación del evento</h2>' +
    (e.gestion && esRol('ORGANIZADOR') && !['CANCELADO', 'FINALIZADO'].includes(e.estado) ? boton('Nueva actividad', 'nuevaActividad') : '') + '</div>' +
    (actividades.length ? '<ol class="timeline">' + actividades.map(a => '<li><strong>' + escapar(fecha(a.inicio)) +
      ' · ' + escapar(a.nombre) + '</strong><p class="secundario">' + escapar(a.lugar) + ' · ' + a.minutos + ' min' +
      (a.responsable ? ' · ' + escapar(a.responsable) : '') + '</p><details><summary>Detalles de la actividad</summary>' +
      '<p>' + escapar(a.descripcion) + '</p><p>Capacidad ' + a.capacidad + '</p></details></li>').join('') + '</ol>'
      : vacio('El organizador aún no ha publicado la programación')));
}

export async function detalleEvento(id) {
  const ruta = '/eventos/' + codigo(id);
  const e = await api(ruta);
  const cerrado = ['CANCELADO', 'FINALIZADO'].includes(e.estado);
  const futuro = new Date(e.inicio) > new Date();
  const edicion = e.gestion && esRol('ORGANIZADOR');
  const gestion = e.gestion ? (cerrado || !edicion ? '' : enlace('editar-evento/' + codigo(id), 'Editar evento') +
    (futuro ? boton(e.estado === 'ACTIVO' ? 'Desactivar evento' : 'Activar evento', 'estadoEvento') +
      boton('Cancelar evento', 'cancelarEvento', 'peligro') : '')) +
      enlace('ventas/' + codigo(id), 'Ventas y estadísticas') +
      (edicion ? enlace('asistencia/' + codigo(id), 'Control de asistencia') +
        enlace('solicitudes?eventoId=' + codigo(id), 'Servicios del evento') : '') : '';
  const accionesCompra = esRol('ASISTENTE') ? boton('Comprar entradas', 'comprar', 'primario') +
    boton(e.favorito ? 'Quitar de favoritos' : 'Guardar en favoritos', 'favorito') : '';
  mostrar(titulo(e.nombre, e.categoria + ' · ' + e.lugar, gestion) +
    '<div class="dos">' + caja('<div class="flex espacio"><h2>Información del evento</h2>' + estado(e.estado) + '</div>' +
      definiciones([['Inicio', fecha(e.inicio)], ['Fin', fecha(e.fin)], ['Lugar', e.lugar], ['Organiza', e.organizador], ['Aforo', e.capacidad]]) +
      '<p>' + escapar(e.descripcion || 'Descripción pendiente') + '</p>' +
      (e.motivoCancelacion ? '<p class="error">' + escapar(e.motivoCancelacion) + '</p>' : '') +
      '<details><summary>Información del organizador</summary><p>' + escapar(e.organizador) + '</p></details>') +
      caja('<h2>Entradas</h2><div class="indicador"><strong>' + moneda(e.precio) + '</strong><span class="secundario">por entrada</span></div>' +
        '<p id="disponibilidad">' + e.disponibles + ' entradas disponibles</p><p id="avisoCupos"></p>' + acciones(accionesCompra)) +
    '</div>' + agenda(e));
  const cambiarCupos = vigente => {
    if ($('#comprar')) $('#comprar').disabled = !vigente.comprable;
    $('#disponibilidad').textContent = vigente.disponibles + ' entradas disponibles';
    const aviso = $('#avisoCupos');
    aviso.textContent = vigente.estado !== 'ACTIVO' ? 'Compra no disponible' :
      vigente.disponibles === 0 ? 'Evento agotado' :
      vigente.disponibles < vigente.capacidad * .1 ? '¡Quedan pocas entradas!' : '';
    aviso.className = aviso.textContent ? 'aviso' : '';
  };
  cambiarCupos(e);
  const nombreRuta = actual();
  const interval = setInterval(async () => {
    if (actual() !== nombreRuta || !$('#disponibilidad')) { clearInterval(interval); return; }
    try {
      const vigente = await api(ruta);
      if (actual() === nombreRuta && $('#disponibilidad')) cambiarCupos(vigente);
    } catch { /* La información volverá a actualizarse en la próxima consulta. */ }
  }, 12_000);
  alClic('favorito', async () => {
    await api(ruta + '/favorito', e.favorito ? 'DELETE' : 'PUT');
    await rutaActiva();
  });
  alClic('comprar', async () => {
    const vigente = await api(ruta);
    if (!vigente.comprable) { cambiarCupos(vigente); return; }
    const solicitudId = crypto.randomUUID();
    modal('Seleccionar entradas', '<p class="campo-ancho">' + escapar(vigente.nombre) + ' · ' + escapar(fecha(vigente.inicio)) +
      '</p>' + campo('cantidad', 'Cantidad de entradas', 'number', 1, 'required min="1" max="' + Math.min(500, vigente.disponibles) + '"') +
      '<p class="campo-ancho" id="totalCompra">Total ' + moneda(vigente.precio) + '</p>', 'Continuar al pago', async datos => {
        const c = await api('/compras', 'POST', { eventoId: id, cantidad: Number(datos.cantidad), solicitudId });
        ir('compra/' + codigo(c.id));
      });
    $('#formDialogo [name=cantidad]').addEventListener('input', evento => {
      $('#totalCompra').textContent = 'Total ' + moneda(Number(evento.target.value) * vigente.precio);
    });
  });
  alClic('estadoEvento', () => {
    const activo = e.estado === 'ACTIVO';
    modal(activo ? 'Desactivar evento' : 'Activar evento', '<p class="campo-ancho">' +
      (activo ? 'El evento se ocultará del catálogo. Las entradas emitidas siguen válidas.' : 'El evento aparecerá en el catálogo.') +
      '</p>', 'Confirmar cambio', async () => {
        const salida = await api(ruta + '/estado', 'PATCH', { estado: activo ? 'INACTIVO' : 'ACTIVO', confirmar: true });
        mensaje(salida.aviso ?? 'Estado actualizado');
        await rutaActiva();
      });
  });
  alClic('cancelarEvento', () => {
    modal('Cancelar evento', '<p class="campo-ancho">Esta acción es irreversible. Se anularán ' + e.vendidas +
      ' entradas vendidas y se iniciarán los reembolsos.</p>' + area('motivo', 'Motivo de cancelación', '', 'required maxlength="1500"') +
      '<div class="campo-ancho">' + check('confirmar', 'Entiendo y confirmo la cancelación', false, 'required') + '</div>',
    'Confirmar cancelación', async datos => {
      await api(ruta + '/cancelacion', 'POST', { motivo: datos.motivo, confirmar: Boolean(datos.confirmar) });
      mensaje('Evento cancelado. Reembolsos iniciados.');
      await rutaActiva();
    }, 'peligro');
  });
  alClic('nuevaActividad', () => {
    modal('Crear actividad', campo('nombre', 'Título', 'text', '', 'required maxlength="150"') +
      campo('inicio', 'Fecha y hora', 'datetime-local', localFecha(e.inicio), 'required') +
      campo('minutos', 'Duración (minutos)', 'number', 30, 'required min="1"') +
      campo('capacidad', 'Capacidad', 'number', e.capacidad, 'required min="1" max="' + e.capacidad + '"') +
      campo('lugar', 'Lugar', 'text', e.lugar) + campo('responsable', 'Responsable') + area('descripcion', 'Descripción'),
    'Guardar actividad', async datos => {
      await api(ruta + '/actividades', 'POST', { ...datos, inicio: aInstante(datos.inicio), minutos: Number(datos.minutos),
        capacidad: Number(datos.capacidad) });
      mensaje('Actividad registrada');
      await rutaActiva();
    });
  });
}

export async function ventas(id) {
  const e = await api('/eventos/' + codigo(id));
  mostrar(titulo('Ventas del evento', e.nombre, enlace('evento/' + codigo(id), 'Volver al evento')) +
    caja(formulario('filtrosVentas', select('periodo', 'Período', [['', 'Todo el período'], ['24h', 'Últimas 24 horas'], ['7d', 'Últimos 7 días']]) +
      campo('desde', 'Desde', 'date') + campo('hasta', 'Hasta', 'date'), 'Actualizar ventas')) + '<div id="ventasDatos" class="pila"></div>');
  let filtros = {};
  const cargar = async datos => {
    filtros = datos;
    const v = await api('/eventos/' + codigo(id) + '/ventas' + consulta(filtros));
    const salida = $('#ventasDatos');
    if (!salida) return;
    const base = { ...filtros, eventoId: id, tipo: 'ventas' };
    const serie = Object.entries(v.serie);
    const max = Math.max(1, ...serie.map(([, valor]) => valor));
    salida.innerHTML = '<div class="grilla">' + [
      ['Aforo', v.capacidad], ['Entradas vendidas', v.vendidas], ['Disponibles', v.disponibles], ['Ocupación', v.ocupacion + ' %'],
      ['Ingresos del período', moneda(v.ingresos)], ['Ticket promedio', moneda(v.ticket)]
    ].map(([n, valor]) => indicador(n, valor)).join('') + '</div>' +
      '<div class="dos">' + caja('<h2>Ingresos por día</h2>' + (serie.length ? serie.map(([dia, valor]) =>
        '<label>' + escapar(dia) + ' · ' + moneda(valor) + '<progress value="' + valor + '" max="' + max + '"></progress></label>').join('')
        : vacio('Sin ventas en el período'))) + caja('<h2>Proyección de ocupación</h2>' +
          definiciones([['Ritmo de venta', v.ritmo + ' entradas por día'], ['Proyección al evento', v.proyeccion + ' entradas']]) +
          acciones(descarga('/api/reportes' + consulta({ ...base, formato: 'pdf' }), 'Descargar PDF') +
            descarga('/api/reportes' + consulta({ ...base, formato: 'xlsx' }), 'Descargar Excel'))) + '</div>' +
      caja('<h2>Compras del período</h2>' + tabla(['Asistente', 'Fecha de pago', 'Entradas vigentes', 'Importe', 'Estado'],
        v.compradores.map(c => [escapar(c.nombre), escapar(fecha(c.fecha)), c.cantidad, moneda(c.importe), estado(c.estado)])));
  };
  alEnviar('filtrosVentas', cargar);
  await cargar({});
  const ruta = actual();
  const intervalo = setInterval(() => {
    if (actual() !== ruta || !$('#ventasDatos')) { clearInterval(intervalo); return; }
    cargar(filtros).catch(() => {});
  }, 15_000);
}
