import { $, $$, sesion, api, escapar, nombreEstado, estado, consulta, enlace, boton, vacio, titulo, caja, indicador, campo,
  select, formulario, tabla, fecha, ir, alEnviar, alClic, mostrar, mensaje, cerrarModal, actual,
  conectarNavegacion, navegadorError } from './comun.js';
import { catalogo, gestion, editarEvento, detalleEvento, ventas, tarjetas, categorias } from './eventos.js';
import { compras, detalleCompra, reembolsos, asistencia } from './compras.js';
import { usuarios, editarUsuario, detalleUsuario, perfil, preferencias, avisos, auditoria, reportes } from './administracion.js';
import { proveedores, ofertas, solicitudes, detalleSolicitud, filasSolicitudes } from './servicios.js';

const navegacion = {
  ADMINISTRADOR: [['panel', 'Panel'], ['usuarios', 'Usuarios'], ['gestion', 'Eventos'], ['reportes', 'Reportes'],
    ['reembolsos', 'Reembolsos'], ['auditoria', 'Auditoría'], ['buzon', 'Buzón de pruebas']],
  ORGANIZADOR: [['panel', 'Panel'], ['gestion', 'Mis eventos'], ['proveedores', 'Proveedores'], ['solicitudes', 'Estado de servicios'],
    ['reportes', 'Reportes'], ['reembolsos', 'Reembolsos']],
  ASISTENTE: [['panel', 'Panel'], ['eventos', 'Explorar eventos'], ['favoritos', 'Mis favoritos'], ['compras', 'Mis compras']],
  PROVEEDOR: [['panel', 'Panel'], ['ofertas', 'Mis servicios'], ['solicitudes', 'Solicitudes recibidas']]
};
const ajustes = [['avisos', 'Notificaciones'], ['preferencias', 'Preferencias'], ['perfil', 'Seguridad y privacidad']];
const marca = '<span class="marca-icono" aria-hidden="true">N</span><span>Nexo Eventos</span>';
const app = $('#aplicacion');

function acceso(token = '') {
  const rolesDemo = [['admin', 'Administrador'], ['organizador', 'Organizador'], ['asistente', 'Asistente'], ['proveedor', 'Proveedor']];
  app.innerHTML = '<main class="acceso" id="contenido"><section class="acceso-marca"><div class="marca">' + marca +
    '</div><div class="pila"><h1>Encuentra tu próximo evento</h1><p>Explora la programación, compra entradas o gestiona tus propios eventos desde tu cuenta.</p></div></section>' +
    '<section class="acceso-formulario pila"><h2>' + (token ? 'Configura tu contraseña' : 'Iniciar sesión') + '</h2>' +
    (token ? '<p>Elige una contraseña nueva de al menos 10 caracteres.</p>' +
      formulario('activar', campo('clave', 'Nueva contraseña', 'password', '', 'required minlength="10" maxlength="72" autocomplete="new-password"'),
        'Guardar contraseña', enlace('acceso', 'Volver al inicio de sesión')) :
      formulario('login', campo('correo', 'Correo electrónico', 'email', '', 'required maxlength="254" autocomplete="username"') +
        campo('clave', 'Contraseña', 'password', '', 'required maxlength="100" autocomplete="current-password"'), 'Ingresar') +
      '<div class="pila"><h3>Cuentas de demostración</h3><div class="lista-demo">' + rolesDemo.map(([r, etiqueta]) =>
        '<button type="button" data-demo="' + r + '">' + etiqueta + '</button>').join('') + '</div></div>') + '</section></main>';
  $$('[data-demo]').forEach(control => control.addEventListener('click', () => {
    $('#login [name=correo]').value = control.dataset.demo + '@nexo.test';
    $('#login [name=clave]').value = 'NexoDemo2026!';
    $('#login [type=submit]').focus();
  }));
  alEnviar('login', async datos => {
    Object.assign(sesion, await api('/sesion', 'POST', datos));
    if (location.hash === '#panel') await renderizar();
    else ir('panel');
  });
  alEnviar('activar', async datos => {
    await api('/sesion/activar', 'POST', { token, clave: datos.clave });
    mensaje('Contraseña configurada. Ya puedes iniciar sesión.');
    ir('acceso');
  });
}

function estructura(pantalla, q) {
  const u = sesion.usuario;
  const enlaces = [...navegacion[u.rol], ...ajustes];
  app.innerHTML = '<header class="cabecera"><div class="cabecera-interior"><a class="marca" href="#panel">' + marca +
    '</a><form class="buscador" role="search" id="busquedaRapida"><input name="q" aria-label="Búsqueda global" placeholder="Buscar en Nexo Eventos" maxlength="160" value="' +
    escapar(q) + '"><button type="submit" class="boton">Buscar</button></form><span>' +
    escapar(u.nombre) + '</span>' + boton('Cerrar sesión', 'salir', 'boton') + '</div></header>' +
    '<div class="estructura"><aside class="lateral"><nav aria-label="Navegación principal"><div class="rol">' +
    escapar(nombreEstado(u.rol)) + '</div><ul>' + enlaces.map(([ruta, etiqueta]) => '<li><a href="#' + ruta + '"' +
      (ruta === pantalla ? ' aria-current="page"' : '') + '>' + escapar(etiqueta) + '</a></li>').join('') +
    '</ul></nav></aside><main class="contenido" id="contenido" tabindex="-1"><p class="vacio" role="status">Cargando…</p></main></div>';
  alEnviar('busquedaRapida', datos => { ir('busqueda' + consulta({ q: datos.q })); });
  alClic('salir', async () => {
    await api('/sesion', 'DELETE');
    sesion.usuario = null;
    sesion.csrf = null;
    if (location.hash === '#acceso') acceso();
    else ir('acceso');
  });
}

async function panel() {
  const datos = await api('/panel');
  const u = sesion.usuario;
  const [introduccion, principales] = {
    ADMINISTRADOR: ['Revisa el estado del sistema y administra las cuentas.', [['usuarios', 'Gestionar usuarios'], ['gestion', 'Eventos'], ['reportes', 'Reportes']]],
    ORGANIZADOR: ['Gestiona tus eventos, programación y ventas.', [['nuevo-evento', 'Crear evento'], ['gestion', 'Mis eventos'], ['proveedores', 'Proveedores']]],
    ASISTENTE: ['Consulta tus entradas y descubre los próximos eventos.', [['eventos', 'Explorar eventos'], ['compras', 'Mis compras'], ['perfil', 'Mi perfil']]],
    PROVEEDOR: ['Revisa las solicitudes y mantén actualizado tu catálogo.', [['ofertas', 'Mis servicios'], ['solicitudes', 'Ver solicitudes']]]
  }[u.rol];
  mostrar('<section class="hero"><h1>Panel de ' + escapar(nombreEstado(u.rol).toLowerCase()) + '</h1><p>Hola, ' +
    escapar(u.nombre) + '. ' + introduccion + '</p><div class="flex">' + principales.map(([r, t]) => enlace(r, t, '')).join('') +
    '</div></section><div class="grilla">' + Object.entries(datos.indicadores).map(([n, v]) => indicador(n, v)).join('') + '</div>' +
    (datos.eventos ? '<h2>' + ({ASISTENTE: 'Próximos eventos', ORGANIZADOR: 'Próximos siete días'}[u.rol] ?? 'Resumen de eventos') + '</h2>' +
      (u.rol === 'ASISTENTE' ? tarjetas(datos.eventos) || caja(vacio('Aún no tienes entradas para próximos eventos')) :
        caja(tabla(['Evento', 'Fecha', 'Entradas vendidas', 'Estado'], datos.eventos.map(e => [
          enlace('evento/' + encodeURIComponent(e.id), e.nombre, ''), escapar(fecha(e.inicio)), e.vendidas, estado(e.estado)
        ])))) : '') +
    (datos.favoritos ? '<h2>Eventos guardados</h2>' + (tarjetas(datos.favoritos) || caja(vacio('Aún no tienes favoritos'))) : '') +
    (datos.recomendados ? '<h2>Recomendaciones</h2>' + (tarjetas(datos.recomendados) || caja(vacio('Aún no hay sugerencias'))) : '') +
    (datos.auditoria ? caja('<h2>Últimas actividades</h2>' + tabla(['Acción','Usuario','Fecha'], datos.auditoria.map(r =>
      [escapar(r.accion), escapar(r.usuario), escapar(fecha(r.fecha))]))) : '') +
    (datos.calendario ? caja('<h2>Calendario de eventos</h2>' + tabla(['Evento','Fecha','Servicio'], datos.calendario.map(s =>
      [escapar(s.evento), escapar(fecha(s.fechaEvento)), escapar(s.servicio)]))) : '') +
    (datos.solicitudes ? caja('<h2>Solicitudes recientes</h2>' + filasSolicitudes(datos.solicitudes)) : ''));
}

async function busqueda(filtros) {
  const tipos = [['TODOS', 'Todos'], ['EVENTOS', 'Eventos'], ['PROVEEDORES', 'Proveedores'], ['SERVICIOS', 'Servicios']];
  if (sesion.usuario.rol === 'ASISTENTE') tipos.push(['COMPRAS','Mis compras']);
  if (sesion.usuario.rol === 'ADMINISTRADOR') tipos.push(['USUARIOS','Usuarios']);
  mostrar(titulo('Búsqueda global', 'Busca eventos, proveedores y servicios según tu rol.') +
    caja(formulario('busquedaFiltros', campo('q', 'Buscar en la plataforma', 'search', filtros.q, 'maxlength="160"') +
      select('tipo', 'Tipo', tipos, filtros.tipo) + select('categoria', 'Categoría del evento', categorias, filtros.categoria) +
      campo('lugar', 'Ubicación del evento', 'text', filtros.lugar) + campo('desde', 'Desde', 'date', filtros.desde) +
      campo('hasta', 'Hasta', 'date', filtros.hasta), 'Aplicar filtros')) + '<div id="historialBusqueda"></div><div id="resultadosBusqueda"></div>');
  alEnviar('busquedaFiltros', datos => ir('busqueda' + consulta(datos)));
  if (filtros.q || filtros.tipo || filtros.categoria || filtros.lugar || filtros.desde || filtros.hasta) {
    const respuesta = await api('/busqueda' + consulta(filtros));
    if ($('#resultadosBusqueda')) $('#resultadosBusqueda').innerHTML = caja(tabla(['Resultado', 'Tipo', 'Detalle'],
      respuesta.resultados.map(r => [enlace(r.ruta, r.nombre, ''), escapar(r.tipo), escapar(r.detalle)]),
      filtros.q ? 'No hay resultados para «' + filtros.q + '»' : 'No hay resultados con estos filtros') +
      (respuesta.sugerencias.length ? '<h2>Búsquedas sugeridas</h2><div class="flex">' +
        respuesta.sugerencias.map(q => enlace('busqueda' + consulta({ q }), q)).join('') + '</div>' : ''));
  }
  const recientes = await api('/busqueda/recientes');
  if (recientes.length && $('#historialBusqueda')) $('#historialBusqueda').innerHTML =
    caja('<details><summary>Búsquedas recientes</summary><div class="acciones">' + recientes.map(({ fecha, ...r }) =>
      enlace('busqueda' + consulta(r), r.q)).join('') + '</div></details>');
}

const pantallas = {
  panel, gestion, 'nuevo-evento': () => editarEvento(), 'editar-evento': editarEvento, evento: detalleEvento,
  eventos: () => catalogo(), favoritos: () => catalogo(true), compras, compra: detalleCompra, reembolsos, asistencia, ventas,
  usuarios, 'nuevo-usuario': () => editarUsuario(), 'editar-usuario': editarUsuario, usuario: detalleUsuario, perfil,
  preferencias, avisos: () => avisos(), buzon: () => avisos(true), auditoria, reportes,
  ofertas, proveedores, solicitudes: (_id, filtros) => solicitudes(filtros), solicitud: detalleSolicitud,
  busqueda: (_id, filtros) => busqueda(filtros)
};

async function renderizar() {
  const ruta = actual();
  const [segmentos, parametros = ''] = ruta.split('?');
  const [pantalla, segmento] = segmentos.split('/');
  const filtros = Object.fromEntries(new URLSearchParams(parametros));
  if (pantalla === 'contenido') return;
  if (pantalla === 'activar' && segmento) { acceso(segmento); return; }
  if (!sesion.usuario) { acceso(); return; }
  const destino = pantalla === 'acceso' ? 'panel' : pantalla;
  estructura(destino, filtros.q ?? '');
  try {
    const vista = pantallas[destino];
    if (!vista) { mostrar(titulo('Pantalla no encontrada', '', enlace('panel', 'Volver al panel'))); return; }
    await vista(segmento ? decodeURIComponent(segmento) : undefined, filtros);
  } catch (error) {
    if (actual() !== ruta) return;
    if (error.estado === 401) {
      sesion.usuario = null; sesion.csrf = null; acceso();
      mensaje(error.message, true);
    } else mostrar(titulo('No se pudo abrir la pantalla', navegadorError(error), enlace('panel', 'Volver al panel')));
  }
}

conectarNavegacion(renderizar);
window.addEventListener('hashchange', () => { cerrarModal(); renderizar(); });
try { Object.assign(sesion, await api('/sesion')); }
catch (error) { if (error.estado !== 401) mensaje(navegadorError(error), true); }
await renderizar();
