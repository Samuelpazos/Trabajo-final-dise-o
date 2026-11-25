export const sesion = { usuario: null, csrf: null, https: false };
export const $ = (selector, raiz = document) => raiz.querySelector(selector);
export const $$ = (selector, raiz = document) => [...raiz.querySelectorAll(selector)];
const equivalentes = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const escapar = valor => String(valor ?? '').replace(/[&<>"']/g, signo => equivalentes[signo]);
export const codigo = encodeURIComponent;
export const moneda = valor => new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'EUR' }).format(valor ?? 0);
const formatoFecha = new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'America/Bogota' });
export const fecha = valor => valor ? formatoFecha.format(new Date(valor)) : '—';
export const nombreEstado = valor => {
  const traducciones = { VALIDA: 'Válida', BAJA_LOGICA: 'Baja lógica', CONTRAPROPUESTA: 'Contrapropuesta',
    PENDIENTE_CONFIRMACION: 'Pendiente de confirmación', SIN_FONDOS: 'Tu banco rechazó la transacción',
    DATOS_INVALIDOS: 'Los datos de tu tarjeta son inválidos', TIMEOUT: 'La sesión expiró. Reinicia el proceso.',
    PRESUPUESTOS: 'Cambios en presupuestos', PROGRAMACION: 'Cambios en la programación',
    PROMOCIONES: 'Novedades y promociones', RECORDATORIOS: 'Recordatorio 24 h antes del evento', RECORDATORIOS_ACTIVIDAD: 'Recordatorio 1 h antes de una actividad', CORREO: 'Correo electrónico',
    PLATAFORMA: 'En la plataforma', AMBOS: 'Correo y plataforma' };
  const s = String(valor ?? '').replaceAll('_', ' ').toLowerCase();
  return traducciones[valor] ?? (s ? s[0].toUpperCase() + s.slice(1) : '—');
};
export const estado = valor => '<span class="etiqueta" data-estado="' + escapar(valor) + '">' + escapar(nombreEstado(valor)) + '</span>';
export const esRol = (...roles) => roles.includes(sesion.usuario?.rol);
export const consulta = valores => {
  const qs = new URLSearchParams();
  for (const [clave, valor] of Object.entries(valores)) if (valor !== '' && valor != null) qs.set(clave, valor);
  return qs.size ? '?' + qs : '';
};
export const ir = ruta => { location.hash = ruta; };
export const enlace = (ruta, etiqueta, clase = 'boton') => '<a class="' + clase + '" href="#' + escapar(ruta) + '">' + escapar(etiqueta) + '</a>';
export const descarga = (ruta, etiqueta) => '<a class="boton" href="' + escapar(ruta) + '">' + escapar(etiqueta) + '</a>';
export const boton = (etiqueta, id, clase = '') => '<button type="button" id="' + id + '" class="' + clase + '">' + escapar(etiqueta) + '</button>';
export const vacio = mensaje => '<p class="vacio">' + escapar(mensaje) + '</p>';
export const titulo = (nombre, detalle = '', acciones = '') => '<div class="encabezado"><div class="titulo"><h1>' + escapar(nombre) + '</h1>' +
  (detalle ? '<p>' + escapar(detalle) + '</p>' : '') + '</div>' + (acciones ? '<div class="acciones">' + acciones + '</div>' : '') + '</div>';
export const caja = (contenido, clase = 'pila') => '<section class="superficie ' + clase + '">' + contenido + '</section>';
export const acciones = contenido => '<div class="acciones">' + contenido + '</div>';
export const indicador = (nombre, valor) => caja('<span class="secundario">' + escapar(nombre) + '</span><strong>' + escapar(valor) + '</strong>', 'indicador');
export const campo = (nombre, etiqueta, tipo = 'text', valor = '', atributos = '') => '<label>' + escapar(etiqueta) +
  '<input name="' + nombre + '" type="' + tipo + '" value="' + escapar(valor) + '" ' + atributos + '></label>';
export const area = (nombre, etiqueta, valor = '', atributos = '') => '<label class="campo-ancho">' + escapar(etiqueta) +
  '<textarea name="' + nombre + '" ' + atributos + '>' + escapar(valor) + '</textarea></label>';
export const select = (nombre, etiqueta, opciones, actual = '', atributos = '') => '<label>' + escapar(etiqueta) +
  '<select name="' + nombre + '" ' + atributos + '>' + opciones.map(op => {
    const [valor, texto] = Array.isArray(op) ? op : [op, nombreEstado(op)];
    return '<option value="' + escapar(valor) + '"' + (String(valor) === String(actual) ? ' selected' : '') + '>' + escapar(texto) + '</option>';
  }).join('') + '</select></label>';
export const check = (nombre, texto, marcado = false, atributos = '', valor = 'on') => '<label><input type="checkbox" name="' +
  nombre + '" value="' + escapar(valor) + '"' + (marcado ? ' checked' : '') + ' ' + atributos + '>' + escapar(texto) + '</label>';
export const envio = texto => '<button class="boton primario" type="submit">' + escapar(texto) + '</button>';
export const formulario = (id, contenido, texto, resto = '') => '<form id="' + id + '" class="campos">' + contenido +
  '<div class="acciones campo-ancho">' + envio(texto) + resto + '</div><div class="campo-ancho" data-resultado role="status"></div></form>';
export const campos = form => Object.fromEntries(new FormData(form));
export const tabla = (columnas, filas, mensaje = 'Todavía no hay registros') => filas.length ? '<div class="tabla"><table><thead><tr>' +
  columnas.map(c => '<th scope="col">' + escapar(c) + '</th>').join('') + '</tr></thead><tbody>' +
  filas.map(f => '<tr>' + f.map(c => '<td>' + c + '</td>').join('') + '</tr>').join('') + '</tbody></table></div>' : vacio(mensaje);
export const definiciones = pares => '<dl>' + pares.map(([titulo, valor]) => '<dt>' + escapar(titulo) + '</dt><dd>' + escapar(valor) + '</dd>').join('') + '</dl>';
export const localFecha = valor => {
  if (!valor) return '';
  const partes = new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Bogota', dateStyle: 'short', timeStyle: 'short' })
    .format(new Date(valor));
  return partes.replace(' ', 'T');
};
export const aInstante = valor => new Date(valor + ':00-05:00').toISOString();
export const actual = () => location.hash.slice(1) || 'panel';

let temporizador;
export function mensaje(texto, fallo = false) {
  $('#mensajes').innerHTML = '<p class="' + (fallo ? 'error' : 'aviso') + '">' + escapar(texto) + '</p>';
  clearTimeout(temporizador);
  temporizador = setTimeout(() => { $('#mensajes').replaceChildren(); }, 6500);
}

export async function api(ruta, metodo = 'GET', cuerpo) {
  const cabeceras = { Accept: 'application/json', 'X-Nexo-Solicitud': 'web' };
  if (sesion.csrf) cabeceras['X-Nexo-CSRF'] = sesion.csrf;
  let datos = cuerpo;
  if (cuerpo != null && !(cuerpo instanceof FormData)) {
    cabeceras['Content-Type'] = 'application/json';
    datos = JSON.stringify(cuerpo);
  }
  const respuesta = await fetch('/api' + ruta, { method: metodo, body: datos, headers: cabeceras, credentials: 'same-origin' });
  if (respuesta.status === 204) return;
  const json = await respuesta.json().catch(() => ({}));
  if (!respuesta.ok) {
    const detalle = Object.entries(json.campos ?? {}).map(([k, v]) => k + ' ' + v).join(' · ');
    const error = new Error((json.mensaje ?? 'No se pudo completar la solicitud') + (detalle ? '. ' + detalle : ''));
    error.estado = respuesta.status;
    error.datos = json;
    throw error;
  }
  return json;
}

export function alEnviar(id, accion) {
  const form = typeof id === 'string' ? $('#' + id) : id;
  if (!form) return;
  form.addEventListener('submit', async evento => {
    evento.preventDefault();
    const resultado = $('[data-resultado]', form);
    const enviar = $('[type=submit]', form);
    if (enviar?.disabled) return;
    if (resultado) resultado.replaceChildren();
    if (enviar) enviar.disabled = true;
    try { await accion(campos(form), form); }
    catch (error) {
      const texto = navegadorError(error);
      if (resultado) resultado.innerHTML = '<p class="error">' + escapar(texto) + '</p>';
      else mensaje(texto, true);
    } finally { if (enviar) enviar.disabled = false; }
  });
}

export function alClic(id, accion) {
  const control = $('#' + id);
  if (!control) return;
  control.addEventListener('click', async () => {
    control.disabled = true;
    try { await accion(); }
    catch (error) { mensaje(navegadorError(error), true); }
    finally { control.disabled = false; }
  });
}

export function navegadorError(error) {
  return error instanceof TypeError && !error.estado ? 'No se pudo conectar con Nexo Eventos. Revisa tu conexión.' : error.message;
}

export function mostrar(contenido) { $('#contenido').innerHTML = contenido; }
export function cerrarModal() { if ($('#dialogo').open) $('#dialogo').close(); }
export function modal(titulo, contenido, texto, accion, clase = '') {
  const dialogo = $('#dialogo');
  cerrarModal();
  dialogo.innerHTML = '<div class="pila"><h2 id="tituloDialogo">' + escapar(titulo) + '</h2>' +
    formulario('formDialogo', contenido, texto, boton('Volver', 'cerrarDialogo')) + '</div>';
  if (clase) $('#formDialogo [type=submit]').classList.add(clase);
  alClic('cerrarDialogo', cerrarModal);
  alEnviar('formDialogo', async (datos, form) => { await accion(datos, form); cerrarModal(); });
  dialogo.showModal();
}

export let rutaActiva = () => {};
export function conectarNavegacion(recargar) { rutaActiva = recargar; }
