import { $, $$, api, escapar, fecha, moneda, estado, nombreEstado, esRol, consulta, enlace, boton, vacio, titulo,
  caja, acciones, campo, select, check, formulario, tabla, definiciones, ir, codigo, sesion,
  alEnviar, alClic, mostrar, modal, mensaje, descarga, rutaActiva } from './comun.js';
import { categorias } from './eventos.js';

const roles = ['ADMINISTRADOR', 'ORGANIZADOR', 'ASISTENTE', 'PROVEEDOR'];
const temas = ['RECORDATORIOS', 'RECORDATORIOS_ACTIVIDAD', 'PROMOCIONES', 'PRESUPUESTOS', 'PROGRAMACION'];

export async function usuarios() {
  mostrar(titulo('Gestión de usuarios', 'Administra las cuentas y las solicitudes de privacidad.',
    enlace('nuevo-usuario', 'Registrar usuario', 'boton primario')) +
    caja(formulario('filtroUsuario', campo('filtro', 'Nombre o correo'), 'Buscar')) + '<div id="listaUsuarios"></div>');
  const cargar = async (datos = {}) => {
    const lista = await api('/usuarios' + consulta(datos));
    if ($('#listaUsuarios')) $('#listaUsuarios').innerHTML = caja(tabla(['Nombre', 'Correo', 'Rol', 'Estado', 'Privacidad'], lista.map(u => [
      enlace('usuario/' + codigo(u.id), u.nombre, ''), escapar(u.correo || '—'), escapar(nombreEstado(u.rol)), estado(u.estado),
      u.solicitudPrivacidad && u.estado !== 'ANONIMIZADO' ? 'Pendiente de procesar' : '—'
    ]), 'No hay cuentas que coincidan con la búsqueda'));
  };
  alEnviar('filtroUsuario', cargar);
  await cargar();
}

const camposCuenta = u => campo('nombre', 'Nombre completo', 'text', u.nombre, 'required maxlength="100"') +
  campo('identificacion', 'Identificación', 'text', u.identificacion, 'maxlength="60"') +
  campo('correo', 'Correo electrónico', 'email', u.correo, 'required maxlength="254"') +
  campo('telefono', 'Teléfono', 'tel', u.telefono, 'maxlength="25"') +
  select('rol', 'Rol', roles, u.rol ?? 'ASISTENTE') + campo('direccion', 'Dirección', 'text', u.direccion, 'maxlength="250"');

export async function editarUsuario(id) {
  const u = id ? await api('/usuarios/' + codigo(id)) : {};
  mostrar(titulo(id ? 'Editar usuario' : 'Registrar usuario',
    id ? u.nombre : 'El usuario recibirá credenciales temporales y un enlace válido durante 24 horas.') +
    caja(formulario('formCuenta', camposCuenta(u), id ? 'Guardar cambios' : 'Registrar usuario',
      enlace(id ? 'usuario/' + codigo(id) : 'usuarios', 'Cancelar'))));
  alEnviar('formCuenta', async datos => {
    const guardado = await api('/usuarios' + (id ? '/' + codigo(id) : ''), id ? 'PUT' : 'POST', {
      ...datos, telefono: datos.telefono || null, identificacion: datos.identificacion || null, direccion: datos.direccion || null
    });
    mensaje(id ? 'Cambios guardados' : 'Usuario registrado');
    ir('usuario/' + codigo(guardado.id));
  });
}

export async function detalleUsuario(id) {
  const ruta = '/usuarios/' + codigo(id);
  const u = await api(ruta);
  const otro = id !== sesion.usuario.id;
  const editable = u.estado !== 'ANONIMIZADO';
  const puedeEstado = otro && ['ACTIVO', 'INACTIVO'].includes(u.estado);
  mostrar(titulo(u.nombre, 'Ficha de usuario', enlace('usuarios', 'Volver a usuarios') +
    (editable ? enlace('editar-usuario/' + codigo(id), 'Editar usuario') : '')) +
    caja('<div class="flex espacio"><h2>Datos de la cuenta</h2>' + estado(u.estado) + '</div>' +
      definiciones([['Correo', u.correo || '—'], ['Rol', nombreEstado(u.rol)], ['Teléfono', u.telefono || '—'],
        ['Identificación', u.identificacion || '—'], ['Dirección', u.direccion || '—']]) +
      (puedeEstado ? acciones(boton(u.estado === 'ACTIVO' ? 'Desactivar usuario' : 'Activar usuario', 'estadoUsuario') +
        boton('Dar de baja', 'bajaUsuario', 'peligro')) : '')) +
    (u.compras?.length ? caja('<h2>Historial de compras</h2>' + tabla(['Evento', 'Fecha', 'Cantidad', 'Monto'],
      u.compras.map(c => [escapar(c.evento), escapar(fecha(c.fecha)), c.cantidad, moneda(c.monto)]))) : '') +
    (u.solicitudPrivacidad ? caja('<h2>Solicitud de privacidad</h2><p>Solicitud del ' + escapar(fecha(u.solicitudPrivacidad)) + '.</p>' +
      (otro && editable ? acciones(boton('Procesar anonimización', 'anonimizar', 'peligro')) :
        '<p class="aviso">La cuenta fue anonimizada.</p>')) : ''));
  const guardarEstado = (nombre, nota, estadoNuevo) => {
    modal(nombre, '<p class="campo-ancho">' + escapar(nota) + '</p>', 'Confirmar cambio', async () => {
      const respuesta = await api(ruta + '/estado', 'PATCH', { estado: estadoNuevo, confirmar: true });
      mensaje(respuesta.aviso ?? 'Estado de la cuenta actualizado');
      await rutaActiva();
    }, estadoNuevo === 'BAJA_LOGICA' ? 'peligro' : '');
  };
  alClic('estadoUsuario', () => guardarEstado(u.estado === 'ACTIVO' ? 'Desactivar usuario' : 'Activar usuario',
    'Confirma el cambio de acceso a la cuenta de ' + u.nombre + '.', u.estado === 'ACTIVO' ? 'INACTIVO' : 'ACTIVO'));
  alClic('bajaUsuario', () => guardarEstado('Dar de baja a un usuario', 'Esta acción es irreversible. Los datos no se borrarán.', 'BAJA_LOGICA'));
  alClic('anonimizar', () => modal('Procesar anonimización', '<p class="campo-ancho">Esta acción es irreversible. Se conservará el historial anonimizado.</p>' +
    '<div class="campo-ancho">' + check('confirmar', 'Confirmo la anonimización', false, 'required') + '</div>',
  'Anonimizar cuenta', async datos => {
    await api(ruta + '/anonimizacion', 'POST', { estado: 'ANONIMIZADO', confirmar: Boolean(datos.confirmar) });
    mensaje('Cuenta anonimizada');
    await rutaActiva();
  }, 'peligro'));
}

export async function perfil() {
  const u = sesion.usuario;
  mostrar(titulo('Seguridad y privacidad', u.nombre, enlace('preferencias', 'Preferencias de notificaciones')) +
    caja('<h2>Datos de sesión</h2>' + definiciones([['Correo', u.correo], ['Rol', nombreEstado(u.rol)],
      ['Conexión del navegador', sesion.https ? 'HTTPS' : 'HTTP local de demostración'],
      ['Protección de sesión', 'Cookie HttpOnly. Vigencia máxima de 24 horas.']])) +
    '<div class="dos">' + caja('<h2>Cambiar contraseña</h2>' +
      formulario('cambiarClave', campo('actual', 'Contraseña actual', 'password', '', 'required autocomplete="current-password"') +
        campo('nueva', 'Nueva contraseña', 'password', '', 'required minlength="10" maxlength="72" autocomplete="new-password"'),
      'Actualizar contraseña')) + caja('<h2>Eliminar mi cuenta y datos</h2>' +
      (u.solicitudPrivacidad ? '<p class="aviso">Tu solicitud está pendiente desde ' + escapar(fecha(u.solicitudPrivacidad)) + '.</p>' :
        '<p>El equipo administrador procesará tu solicitud de anonimización.</p>' +
        acciones(boton('Solicitar eliminación de datos', 'privacidad', 'peligro')))) + '</div>');
  alEnviar('cambiarClave', async (datos, form) => {
    Object.assign(sesion, await api('/perfil/clave', 'PUT', datos));
    form.reset();
    mensaje('Contraseña actualizada');
  });
  alClic('privacidad', () => modal('Eliminar mi cuenta y datos',
    '<p class="campo-ancho">Esta acción es irreversible. Conservaremos el historial de compras anonimizado.</p>' +
    campo('clave', 'Contraseña actual', 'password', '', 'required autocomplete="current-password"') +
    '<div class="campo-ancho">' + check('confirmar', 'Entiendo y confirmo', false, 'required') + '</div>',
    'Solicitar eliminación de datos', async datos => {
      await api('/perfil/privacidad', 'POST', { clave: datos.clave, confirmar: Boolean(datos.confirmar) });
      Object.assign(sesion, await api('/sesion'));
      mensaje('Solicitud de privacidad recibida');
      await rutaActiva();
    }, 'peligro'));
}

export async function preferencias() {
  const p = await api('/perfil/preferencias');
  mostrar(titulo('Preferencias de notificaciones', 'Elige cómo recibir los avisos secundarios.') +
    caja('<h2>Notificaciones obligatorias</h2><p>Las confirmaciones de compra, cancelaciones de eventos, reembolsos y cambios de solicitudes recibidas por el proveedor siempre se envían por correo.</p>') +
    caja(formulario('formPreferencias', select('canal', 'Canal preferido', ['CORREO','PLATAFORMA','AMBOS'], p.canal) +
      '<div class="pila campo-ancho">' + temas.map(t => '<div class="campos">' + check('temas', nombreEstado(t), p.temas.includes(t), '', t) +
        select('canal-' + t, 'Canal para ' + nombreEstado(t).toLowerCase(), [['', 'Canal preferido'], 'CORREO', 'PLATAFORMA', 'AMBOS'],
          p.canales?.[t] ?? '') + '</div>').join('') + '</div>',
    'Guardar preferencias', boton('Desactivar todas las opcionales', 'desactivarAvisos'))));
  alClic('desactivarAvisos', () => $$('[name=temas]').forEach(control => { control.checked = false; }));
  alEnviar('formPreferencias', async (datos, form) => {
    const canales = Object.fromEntries(temas.filter(t => datos['canal-' + t]).map(t => [t, datos['canal-' + t]]));
    await api('/perfil/preferencias', 'PUT', { canal: datos.canal, temas: new FormData(form).getAll('temas'), canales });
    mensaje('Preferencias guardadas');
  });
}

const enlacesMensaje = enlaces => acciones((enlaces ?? []).map(url => {
  try {
    const destino = new URL(url, location.origin);
    if (!['http:', 'https:'].includes(destino.protocol) || destino.origin !== location.origin) return '';
    return descarga(destino.pathname + destino.search + destino.hash, destino.hash ? 'Abrir detalle' : 'Descargar archivo');
  } catch { return ''; }
}).join(''));

export async function avisos(buzon = false) {
  const lista = await api(buzon ? '/demo/buzon' : '/avisos');
  mostrar(titulo(buzon ? 'Buzón de pruebas' : 'Notificaciones', buzon ? 'Mensajes ficticios generados por la aplicación.' : 'Consulta las novedades de tu cuenta.') +
    (lista.length ? lista.map(a => caja('<div class="flex espacio"><h2>' + escapar(a.asunto) + '</h2><small>' +
      escapar(fecha(a.fecha)) + '</small></div>' + (buzon ? '<p><strong>Para:</strong> ' + escapar(a.destinatario) + '</p>' : '') +
      '<p>' + escapar(a.contenido) + '</p>' + enlacesMensaje(a.enlaces))).join('') : caja(vacio('Todavía no hay mensajes'))));
}

export async function auditoria() {
  const filtros = campo('usuario', 'Usuario o identificador') + campo('accion', 'Acción') +
    campo('desde', 'Desde', 'date') + campo('hasta', 'Hasta', 'date');
  mostrar(titulo('Auditoría', 'Consulta las acciones críticas de la plataforma.') +
    caja(formulario('auditoriaFiltro', filtros, 'Aplicar filtros')) + '<div id="registroAuditoria"></div>');
  const cargar = async (datos = {}) => {
    const lista = await api('/auditoria' + consulta(datos));
    if (!$('#registroAuditoria')) return;
    $('#registroAuditoria').innerHTML = caja(acciones(
      descarga('/api/auditoria/archivo' + consulta({ ...datos, formato: 'pdf' }), 'Descargar PDF') +
      descarga('/api/auditoria/archivo' + consulta({ ...datos, formato: 'csv' }), 'Descargar CSV')) +
      tabla(['Usuario', 'Acción', 'Fecha y hora', 'IP', 'Elemento', 'Resultado', 'Detalle'], lista.map(r => [
        escapar(r.usuario), escapar(r.accion), escapar(fecha(r.fecha)), escapar(r.ip || '—'), escapar(r.elemento || '—'),
        escapar(nombreEstado(r.resultado)), escapar(r.detalle)
      ]), 'No hay coincidencias con los filtros'));
  };
  alEnviar('auditoriaFiltro', cargar);
  await cargar();
}

export async function reportes() {
  const eventos = await api('/eventos/gestion');
  const filtros = select('tipo', 'Tipo de reporte', [['ventas', 'Ventas'], ['ingresos', 'Ingresos'], ['asistencia', 'Asistencia'], ['servicios', 'Servicios']]) +
    campo('desde', 'Desde', 'date') + campo('hasta', 'Hasta', 'date') +
    select('eventoId', 'Evento', [['', 'Todos'], ...eventos.map(e => [e.id, e.nombre])]) +
    campo('proveedor', 'Proveedor (reporte de servicios)') + select('categoria', 'Categoría', categorias) +
    select('formato', 'Formato', [['pdf', 'PDF'], ['xlsx', 'Excel (.xlsx)']]);
  mostrar(titulo('Generar reportes', 'Exporta ventas, asistencia o servicios por período.') +
    caja(formulario('formReporte', filtros, 'Generar reporte')));
  alEnviar('formReporte', async (datos, form) => {
    const url = '/api/reportes' + consulta(datos);
    const respuesta = await fetch(url, { credentials: 'same-origin' });
    if (!respuesta.ok) {
      const error = await respuesta.json().catch(() => ({}));
      throw new Error(error.mensaje ?? 'No se pudo generar el reporte');
    }
    const objeto = URL.createObjectURL(await respuesta.blob());
    const a = document.createElement('a');
    a.href = objeto;
    a.download = 'reporte-' + datos.tipo + '.' + datos.formato;
    a.click();
    setTimeout(() => URL.revokeObjectURL(objeto), 5000);
    $('[data-resultado]', form).innerHTML = '<p class="aviso">Reporte preparado para descargar.</p>';
  });
}
