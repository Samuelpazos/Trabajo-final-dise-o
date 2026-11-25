import { $, api, escapar, moneda, fecha, estado, nombreEstado, esRol, consulta, enlace, boton, vacio, titulo,
  caja, acciones, campo, area, select, check, formulario, tabla, definiciones, ir, codigo,
  alEnviar, alClic, mostrar, modal, mensaje, descarga, rutaActiva } from './comun.js';

const estados = ['SOLICITADO', 'PRESUPUESTADO', 'CONTRAPROPUESTA', 'CONFIRMADO', 'RECHAZADO', 'FINALIZADO'];
const tipos = ['Sonido', 'Logística', 'Catering', 'Seguridad', 'Iluminación', 'Transporte', 'Mobiliario'];

function editarOferta(s = {}) {
  const fechaHoy = new Date().toLocaleDateString('sv-SE', { timeZone: 'America/Bogota' });
  modal(s.id ? 'Editar servicio' : 'Registrar servicio', campo('nombre', 'Nombre del servicio', 'text', s.nombre, 'required maxlength="150"') +
    campo('tipo', 'Tipo de servicio', 'text', s.tipo, 'required maxlength="80" list="tiposServicio"') +
    '<datalist id="tiposServicio">' + tipos.map(t => '<option value="' + escapar(t) + '"></option>').join('') + '</datalist>' +
    campo('precio', 'Precio (EUR)', 'number', s.precio, 'required min="0" step="0.01"') +
    campo('desde', 'Disponible desde', 'date', s.desde ?? fechaHoy, 'required') +
    campo('hasta', 'Disponible hasta', 'date', s.hasta, 'required') + area('descripcion', 'Descripción', s.descripcion, 'required maxlength="3000"') +
    '<div class="campo-ancho">' + check('disponible', 'Visible en el catálogo', s.disponible ?? true) + '</div>',
    s.id ? 'Guardar cambios' : 'Publicar servicio', async datos => {
      await api('/ofertas' + (s.id ? '/' + codigo(s.id) : ''), s.id ? 'PUT' : 'POST',
        { ...datos, precio: Number(datos.precio), disponible: Boolean(datos.disponible) });
      mensaje(s.id ? 'Servicio actualizado' : 'Servicio registrado');
      await rutaActiva();
    });
}

export async function ofertas() {
  const lista = await api('/ofertas');
  mostrar(titulo('Mis servicios', 'Publica tus servicios y actualiza su disponibilidad.', boton('Registrar servicio', 'nuevaOferta', 'primario')) +
    caja(tabla(['Servicio', 'Tipo', 'Precio', 'Disponibilidad', 'Acciones'], lista.map(s => [
      '<strong>' + escapar(s.nombre) + '</strong><p>' + escapar(s.descripcion) + '</p>', escapar(s.tipo), moneda(s.precio),
      escapar(s.desde + ' a ' + s.hasta) + '<br>' + estado(s.disponible ? 'ACTIVO' : 'INACTIVO'),
      acciones(boton('Editar', 'editar-' + s.id) + boton(s.disponible ? 'Desactivar' : 'Activar', 'alternar-' + s.id) +
        boton('Eliminar', 'eliminar-' + s.id, 'peligro'))
    ]))));
  alClic('nuevaOferta', () => editarOferta());
  lista.forEach(s => {
    alClic('editar-' + s.id, () => editarOferta(s));
    alClic('alternar-' + s.id, async () => {
      const { nombre, descripcion, tipo, precio, desde, hasta } = s;
      await api('/ofertas/' + codigo(s.id), 'PUT', { nombre, descripcion, tipo, precio, desde, hasta, disponible: !s.disponible });
      mensaje('Disponibilidad actualizada');
      await rutaActiva();
    });
    alClic('eliminar-' + s.id, () => modal('Eliminar servicio',
      '<p class="campo-ancho">Confirma la eliminación de ' + escapar(s.nombre) + '.</p>', 'Eliminar servicio', async () => {
        await api('/ofertas/' + codigo(s.id), 'DELETE');
        mensaje('Servicio eliminado');
        await rutaActiva();
      }, 'peligro'));
  });
}

async function pedirServicio(proveedor, s) {
  const eventos = (await api('/eventos/gestion')).filter(e => !['CANCELADO', 'FINALIZADO'].includes(e.estado) && new Date(e.inicio) > new Date());
  if (!eventos.length) { mensaje('Crea un evento futuro antes de solicitar un servicio.', true); return; }
  modal('Solicitar servicio', '<div class="campo-ancho">' +
    definiciones([['Proveedor', proveedor.nombre], ['Servicio', s.nombre], ['Precio publicado', moneda(s.precio)]]) + '</div>' +
    select('eventoId', 'Evento', eventos.map(e => [e.id, e.nombre])) + '<p id="fechaServicio"></p>' +
    area('observaciones', 'Detalles de la solicitud', '', 'maxlength="3000"'), 'Enviar solicitud', async datos => {
      const creada = await api('/solicitudes', 'POST', { ofertaId: s.id, eventoId: datos.eventoId, observaciones: datos.observaciones });
      mensaje('Solicitud enviada');
      ir('solicitud/' + codigo(creada.id));
    });
  const opciones = $('#formDialogo [name=eventoId]');
  const actualizar = () => {
    const e = eventos.find(x => x.id === opciones.value);
    $('#fechaServicio').textContent = fecha(e?.inicio);
  };
  opciones.addEventListener('change', actualizar);
  actualizar();
}

export async function proveedores(id) {
  mostrar(titulo(id ? 'Detalle del proveedor' : 'Buscar proveedores', 'Consulta los servicios y su disponibilidad.',
    id ? enlace('proveedores', 'Todos los proveedores') : '') +
    (id ? '' : caja(formulario('filtroProveedores', campo('filtro', 'Proveedor o servicio') +
      campo('tipo', 'Tipo de servicio') + campo('minimo', 'Precio mínimo (EUR)', 'number', '', 'min="0" step="0.01"') +
      campo('fecha', 'Disponible para la fecha', 'date'), 'Buscar', boton('Limpiar filtros', 'limpiarProveedores')))) +
    '<div class="pila" id="listaProveedores"></div>');
  const cargar = async (filtros = {}) => {
    const lista = (await api('/proveedores' + consulta(filtros))).filter(p => !id || p.id === id);
    const salida = $('#listaProveedores');
    if (!salida) return;
    salida.innerHTML = lista.length ? lista.map(p => caja('<div class="flex espacio"><h2>' +
      (id ? escapar(p.nombre) : enlace('proveedores/' + codigo(p.id), p.nombre, '')) + '</h2>' +
      '<span class="secundario">' + p.cantidad + ' servicios · ' + escapar(p.tipos.join(', ')) + '</span></div>' +
      tabla(['Servicio', 'Tipo', 'Precio', 'Disponibilidad', ''], p.servicios.map(s => [
        '<strong>' + escapar(s.nombre) + '</strong><p>' + escapar(s.descripcion) + '</p>', escapar(s.tipo), moneda(s.precio),
        escapar(s.desde + ' a ' + s.hasta), esRol('ORGANIZADOR') ? boton('Solicitar servicio', 'solicitar-' + s.id) : ''
      ])))).join('') : caja(vacio('No hay proveedores con estos filtros'));
    lista.forEach(p => p.servicios.forEach(s => alClic('solicitar-' + s.id, () => pedirServicio(p, s))));
  };
  alEnviar('filtroProveedores', cargar);
  alClic('limpiarProveedores', async () => { $('#filtroProveedores').reset(); await cargar(); });
  await cargar();
}

export function filasSolicitudes(lista) {
  return tabla(['Servicio', 'Evento', 'Organizador', 'Proveedor', 'Recibida', 'Estado'], lista.map(s => [
    enlace('solicitud/' + codigo(s.id), s.servicio, ''), escapar(s.evento), escapar(s.organizador), escapar(s.proveedor),
    escapar(fecha(s.creada)), estado(s.estado)
  ]), 'No hay solicitudes para este filtro');
}

export async function solicitudes(filtrosIniciales = {}) {
  const eventos = esRol('ORGANIZADOR') ? await api('/eventos/gestion') : [];
  const filtros = select('estado', 'Estado', [['', 'Todos'], ...estados], filtrosIniciales.estado) +
    (eventos.length ? select('eventoId', 'Evento', [['', 'Todos'], ...eventos.map(e => [e.id, e.nombre])], filtrosIniciales.eventoId) : '');
  mostrar(titulo(esRol('PROVEEDOR') ? 'Solicitudes recibidas' : 'Estado de servicios',
    'Consulta presupuestos, contrapropuestas y acuerdos.',
    esRol('ORGANIZADOR') ? enlace('proveedores', 'Buscar proveedores', 'boton primario') : '') +
    caja(formulario('filtroSolicitudes', filtros, 'Filtrar solicitudes')) + '<div id="listaSolicitudes"></div>');
  const cargar = async datos => {
    const lista = await api('/solicitudes' + consulta(datos));
    if ($('#listaSolicitudes')) $('#listaSolicitudes').innerHTML = caja(filasSolicitudes(lista));
  };
  alEnviar('filtroSolicitudes', cargar);
  await cargar(filtrosIniciales);
}

function respuestasPosibles(estado) {
  const proveedor = esRol('PROVEEDOR');
  if (estado === 'SOLICITADO' || estado === 'CONTRAPROPUESTA')
    return proveedor ? ['PRESUPUESTADO', 'CONFIRMADO', 'RECHAZADO'] : [];
  if (estado === 'PRESUPUESTADO')
    return proveedor ? ['PRESUPUESTADO', 'RECHAZADO'] : ['CONFIRMADO', 'CONTRAPROPUESTA', 'RECHAZADO'];
  return estado === 'CONFIRMADO' ? ['FINALIZADO'] : [];
}
const accionesSolicitud = { PRESUPUESTADO: 'Presupuestar', CONFIRMADO: 'Confirmar servicio', RECHAZADO: 'Rechazar',
  CONTRAPROPUESTA: 'Hacer contrapropuesta', FINALIZADO: 'Marcar como finalizado' };

export async function detalleSolicitud(id) {
  const ruta = '/solicitudes/' + codigo(id);
  const s = await api(ruta);
  const opciones = respuestasPosibles(s.estado);
  mostrar(titulo(s.servicio, 'Solicitud de servicio', enlace('solicitudes', 'Volver a solicitudes')) +
    '<div class="dos">' + caja('<div class="flex espacio"><h2>Detalle de solicitud</h2>' + estado(s.estado) + '</div>' +
      definiciones([['Evento', s.evento], ['Fecha del evento', fecha(s.fechaEvento)], ['Lugar', s.lugar], ['Organizador', s.organizador],
        ['Proveedor', s.proveedor], ['Fecha recibida', fecha(s.creada)], ['Observaciones', s.observaciones || '—'],
        ['Último monto', s.monto == null ? '—' : moneda(s.monto)], ['Finalización', fecha(s.finalizada)]]) +
      (['CONFIRMADO','FINALIZADO'].includes(s.estado) ? acciones(descarga('/api' + ruta + '/acuerdo', 'Descargar acuerdo PDF')) : '')) +
    (opciones.length ? caja('<h2>Responder solicitud</h2>' +
      acciones(opciones.map(o => boton(accionesSolicitud[o], 'respuesta-' + o, o === 'RECHAZADO' ? 'peligro' : '')).join(''))) : '') +
    '</div>' + caja('<h2>Historial de estados</h2>' +
      '<ol class="timeline">' + s.historial.map(h => '<li><div class="flex espacio">' + estado(h.estado) + '<small>' +
        escapar(fecha(h.fecha)) + '</small></div><strong>' + escapar(h.actor) + (h.monto != null ? ' · ' + moneda(h.monto) : '') +
        '</strong><p>' + escapar(h.comentario) + '</p>' +
        (h.adjuntoId ? acciones(descarga('/api' + ruta + '/adjuntos/' + codigo(h.adjuntoId), 'Descargar presupuesto PDF')) : '') + '</li>').join('') + '</ol>'));
  opciones.forEach(o => alClic('respuesta-' + o, () => {
    const monto = ['PRESUPUESTADO', 'CONTRAPROPUESTA'].includes(o);
    modal(accionesSolicitud[o], (monto ? campo('monto', 'Monto (EUR)', 'number', s.monto, 'required min="0.01" step="0.01"') : '') +
      (o === 'PRESUPUESTADO' ? campo('archivo', 'Adjuntar presupuesto PDF', 'file', '', 'accept="application/pdf,.pdf"') : '') +
      area('comentario', 'Comentario', '', 'maxlength="3000"'),
    'Enviar respuesta', async (datos, form) => {
      const carga = new FormData();
      carga.set('datos', new Blob([JSON.stringify({ estado: o, monto: monto ? Number(datos.monto) : null, comentario: datos.comentario })],
        { type: 'application/json' }));
      if (form.elements.archivo?.files.length) carga.set('archivo', form.elements.archivo.files[0]);
      await api(ruta + '/respuestas', 'POST', carga);
      mensaje('Solicitud actualizada');
      await rutaActiva();
    });
  }));
}
