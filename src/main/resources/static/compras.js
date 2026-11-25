import { $, $$, api, escapar, moneda, fecha, estado, nombreEstado, consulta, enlace, boton, vacio,
  titulo, caja, acciones, indicador, campo, select, check, formulario, tabla, definiciones, ir, codigo,
  alEnviar, alClic, mostrar, modal, mensaje, descarga, actual, rutaActiva } from './comun.js';

export async function compras() {
  const lista = await api('/compras');
  mostrar(titulo('Mis compras', 'Consulta comprobantes, entradas y reembolsos.', enlace('eventos', 'Explorar eventos')) +
    caja(tabla(['Evento', 'Compra', 'Entradas', 'Total', 'Estado'], lista.map(c => [
      enlace('compra/' + codigo(c.id), c.evento, ''), escapar(fecha(c.creada)), c.cantidad, moneda(c.total), estado(c.estado)
    ]))));
}

export async function detalleCompra(id) {
  const ruta = '/compras/' + codigo(id);
  const c = await api(ruta);
  const pagada = c.pago?.resultado === 'APROBADO';
  const reservada = c.estado === 'RESERVADA';
  const tituloVista = reservada ? 'Pagar pedido' : pagada ? 'Comprobante de compra' : 'Detalle de la compra';
  const entradaHtml = (t, n) => {
    const qr = '/api' + ruta + '/qr/' + codigo(t.codigo);
    return caja('<div class="flex espacio"><h3>Entrada ' + (n + 1) + '</h3>' + estado(t.estado) + '</div>' +
      (t.estado !== 'CANCELADA' ? '<img alt="Código QR de la entrada ' + (n + 1) + '" src="' + escapar(qr) +
        '" width="180" height="180"><p class="codigo">' + escapar(t.codigo) + '</p><div class="acciones"><a class="boton" download href="' +
        escapar(qr) + '">Descargar QR</a></div>' : '') + (t.usada ? '<p>Validada ' + escapar(fecha(t.usada)) + '</p>' : ''), 'pila entrada');
  };
  mostrar(titulo(tituloVista, c.evento, enlace('compras', 'Mis compras') + enlace('evento/' + codigo(c.eventoId), 'Ver evento')) +
    '<div class="dos">' + caja('<div class="flex espacio"><h2>Resumen</h2>' + estado(c.estado) + '</div>' +
      definiciones([['Referencia', c.id], ['Fecha de compra', fecha(c.creada)], ['Unidades', c.cantidad], ['Precio unitario', moneda(c.precio)],
        ['Total', moneda(c.total)], ['Método', c.pago?.metodo ?? 'Tarjeta simulada']]) +
      acciones((pagada ? descarga('/api' + ruta + '/comprobante', 'Descargar comprobante PDF') : '') +
        (c.cancelable ? boton('Cancelar compra', 'cancelarCompra', 'peligro') : '')) +
      (pagada && c.entradas.some(t => t.estado === 'VALIDA') && !c.cancelable
        ? '<p class="secundario">Fecha límite de cancelación: ' + escapar(fecha(c.limite)) +
          '. Contacta al organizador para solicitudes especiales.</p>' : '')) +
    (reservada ? caja('<h2>Pasarela de demostración</h2><p>Elige un resultado simulado. La reserva vence el ' + escapar(fecha(c.vence)) +
      '.</p>' + formulario('formPago', select('resultado', 'Resultado de la pasarela', [
        ['APROBADO', 'Pago aprobado'], ['SIN_FONDOS', 'Pago rechazado por fondos'],
        ['DATOS_INVALIDOS', 'Datos inválidos'], ['TIMEOUT', 'Sesión expirada']
      ]), 'Confirmar pago', boton('Cancelar reserva', 'abandonar', 'peligro')))
      : ['RECHAZADA', 'EXPIRADA', 'ABANDONADA'].includes(c.estado)
        ? caja('<h2>Pago pendiente</h2><p class="error">' + escapar(c.estado === 'EXPIRADA' ? nombreEstado('TIMEOUT') :
          c.pago?.resultado ? nombreEstado(c.pago.resultado) : 'La reserva fue cancelada') + '</p>' +
          acciones(enlace('evento/' + codigo(c.eventoId), 'Intentar nuevamente', 'boton primario'))) : '') + '</div>' +
    (c.entradas.length ? '<h2>Mis entradas</h2><div class="grilla">' + c.entradas.map(entradaHtml).join('') + '</div>' : '') +
    (c.reembolsos.length ? caja('<h2>Seguimiento del reembolso</h2>' +
      tabla(['Referencia', 'Entradas', 'Monto', 'Estado', 'Inicio', 'Acreditación'], c.reembolsos.map(r => [
        '<span class="codigo">' + escapar(r.id) + '</span>', r.cantidad, moneda(r.monto), estado(r.estado), escapar(fecha(r.iniciado)),
        escapar(r.acreditado ? fecha(r.acreditado) : r.estado === 'FALLIDO' ? 'Contacta al soporte' : '3 a 10 días bancarios')
      ]))) : ''));
  alEnviar('formPago', async datos => {
    const resultado = await api('/demo' + ruta + '/pago', 'POST', datos);
    mensaje(resultado.estado === 'PAGADA' ? 'Pago aprobado. Tus entradas están listas.' :
      nombreEstado(resultado.pago?.resultado ?? 'TIMEOUT'), resultado.estado !== 'PAGADA');
    await rutaActiva();
  });
  alClic('abandonar', () => modal('Cancelar reserva', '<p class="campo-ancho">Los cupos volverán al catálogo.</p>',
    'Liberar cupos', async () => {
      await api(ruta + '/reserva', 'DELETE');
      ir('evento/' + codigo(c.eventoId));
    }, 'peligro'));
  alClic('cancelarCompra', () => {
    const validas = c.entradas.map((t, i) => ({ ...t, i })).filter(t => t.estado === 'VALIDA');
    const restante = Math.max(0, new Date(c.limite).getTime() - Date.now());
    const horas = Math.floor(restante / 3_600_000);
    modal('Cancelar compra', '<div class="campo-ancho">' +
      definiciones([['Evento', c.evento], ['Fecha del evento', fecha(c.inicio)], ['Plazo', c.plazoHoras + ' horas antes del inicio'],
        ['Fecha límite', fecha(c.limite)], ['Tiempo restante', Math.floor(horas / 24) + ' días · ' + (horas % 24) + ' horas']]) + '</div>' +
      '<div class="campo-ancho">' + check('todas', 'Seleccionar todas las entradas válidas', true) +
      '<div class="pila">' + validas.map(t => check('entradas', 'Entrada ' + (t.i + 1) + ' · ' + t.codigo.slice(0, 8),
        true, '', t.codigo)).join('') + '</div></div><p class="campo-ancho" id="montoCancelacion"></p>' +
      '<div class="campo-ancho">' + check('confirmar', 'Entiendo que la selección quedará invalidada', false, 'required') + '</div>',
    'Confirmar cancelación', async (datos, form) => {
      const entradas = new FormData(form).getAll('entradas');
      if (!entradas.length) throw new Error('Selecciona al menos una entrada');
      await api(ruta + '/cancelaciones', 'POST', { entradas, confirmar: Boolean(datos.confirmar) });
      mensaje('Compra cancelada. Reembolso iniciado.');
      await rutaActiva();
    }, 'peligro');
    const formulario = $('#formDialogo');
    const todas = $('[name=todas]', formulario);
    const entradas = $$('[name=entradas]', formulario);
    const calcular = () => {
      const cantidad = entradas.filter(x => x.checked).length;
      todas.checked = cantidad === validas.length;
      todas.indeterminate = cantidad > 0 && cantidad < validas.length;
      $('#montoCancelacion').textContent = 'Reembolso estimado: ' + moneda(cantidad * c.precio);
    };
    todas.addEventListener('change', () => { entradas.forEach(x => { x.checked = todas.checked; }); calcular(); });
    entradas.forEach(x => x.addEventListener('change', calcular));
    calcular();
  });
  if (reservada) {
    const demora = Math.max(0, new Date(c.vence).getTime() - Date.now());
    setTimeout(() => { if (actual() === 'compra/' + codigo(id)) rutaActiva(); }, demora + 300);
  }
}

export async function reembolsos() {
  const lista = await api('/reembolsos');
  mostrar(titulo('Reembolsos', 'Revisa el estado y confirma resultados de la pasarela de demostración.') +
    caja(tabla(['Evento', 'Referencia', 'Monto', 'Estado', 'Simulación'], lista.flatMap(c => c.reembolsos.map(r => [
      escapar(c.evento), '<span class="codigo">' + escapar(r.id) + '</span>', moneda(r.monto), estado(r.estado),
      ['INICIADO', 'PENDIENTE_CONFIRMACION'].includes(r.estado) ? '<div class="flex">' +
        boton('Completar', 'completar-' + r.id) + boton('Marcar fallido', 'fallar-' + r.id, 'peligro') + '</div>' : escapar(fecha(r.acreditado))
    ])), 'Todavía no hay reembolsos')));
  lista.forEach(c => c.reembolsos.forEach(r => [
    ['completar-', 'COMPLETADO'], ['fallar-', 'FALLIDO']
  ].forEach(([prefijo, resultado]) => alClic(prefijo + r.id, async () => {
    await api('/demo/compras/' + codigo(c.id) + '/reembolsos/' + codigo(r.id), 'POST', { resultado });
    mensaje('Confirmación registrada');
    await rutaActiva();
  }))));
}

export async function asistencia(id) {
  const e = await api('/eventos/' + codigo(id));
  const ruta = '/eventos/' + codigo(id) + '/asistencia';
  mostrar(titulo('Control de asistencia', e.nombre, enlace('evento/' + codigo(id), 'Volver al evento')) +
    '<div id="estadisticas" class="grilla"></div><div class="dos">' +
    caja('<h2>Validar entrada</h2>' + acciones(boton('Activar cámara', 'camara')) +
      '<video id="video" autoplay muted playsinline hidden></video><p id="lectorEstado" role="status"></p>' +
      formulario('codigoManual', campo('codigo', 'Código de la entrada', 'text', '', 'required maxlength="260" autocomplete="off"'),
        'Validar entrada') + '<div id="resultadoQR" role="status"></div>') +
    caja('<h2>Últimos ingresos</h2><div id="ultimosIngresos"></div>') + '</div>');
  const rutaVista = actual();
  const cola = [];
  async function actualizar() {
    const datos = await api(ruta);
    if (actual() !== rutaVista || !$('#estadisticas')) return;
    $('#estadisticas').innerHTML = [indicador('Aforo', datos.capacidad), indicador('Total esperado', datos.esperados),
      indicador('Asistentes presentes', datos.presentes), indicador('Ocupación', datos.ocupacion + ' %')].join('');
    $('#ultimosIngresos').innerHTML = tabla(['Asistente', 'Ingreso'],
      datos.ultimos.map(u => [escapar(u.asistente), escapar(fecha(u.fecha))]), 'Aún no hay ingresos registrados');
  }
  const resultado = (texto, error = false) => {
    const bloque = $('#resultadoQR');
    if (bloque && actual() === rutaVista) bloque.innerHTML = '<p class="' + (error ? 'error' : 'aviso') + '">' + escapar(texto) + '</p>';
  };
  async function validar(codigoLeido, encolar = true) {
    if (!navigator.onLine) {
      if (encolar && !cola.includes(codigoLeido)) cola.push(codigoLeido);
      resultado('Sin conexión. La entrada queda pendiente de validar cuando se restablezca el servicio.', true);
      return false;
    }
    try {
      const salida = await api(ruta, 'POST', { codigo: codigoLeido });
      resultado(salida.mensaje + ' ' + salida.asistente);
    } catch (error) {
      if (error.estado === 409 || error.estado === 400) {
        resultado(error.message + (error.datos.usada ? ' · ' + fecha(error.datos.usada) : ''), true);
      } else if (error instanceof TypeError) {
        if (encolar && !cola.includes(codigoLeido)) cola.push(codigoLeido);
        resultado('Conexión interrumpida. Validación pendiente.', true);
        return false;
      } else throw error;
    }
    actualizar().catch(() => {});
    return true;
  }
  alEnviar('codigoManual', async datos => {
    await validar(datos.codigo.trim());
    $('#codigoManual')?.reset();
  });
  let flujo;
  let pausa = false;
  let sincronizando = false;
  let ultimo = '';
  let instanteUltimo = 0;
  const video = $('#video');
  const lienzo = document.createElement('canvas');
  const pixeles = lienzo.getContext('2d', { willReadFrequently: true });
  const camara = $('#camara');
  const textoCamara = $('#lectorEstado');
  function apagar() {
    flujo?.getTracks().forEach(t => t.stop()); flujo = null;
    video.hidden = true; video.srcObject = null; camara.textContent = 'Activar cámara';
  }
  async function leer() {
    if (!flujo || actual() !== rutaVista) { apagar(); return; }
    if (video.readyState >= 2 && !pausa && typeof window.jsQR === 'function') {
      lienzo.width = Math.min(video.videoWidth, 640);
      lienzo.height = Math.round(lienzo.width * video.videoHeight / video.videoWidth);
      pixeles.drawImage(video, 0, 0, lienzo.width, lienzo.height);
      const datos = pixeles.getImageData(0, 0, lienzo.width, lienzo.height);
      const qr = window.jsQR(datos.data, datos.width, datos.height, { inversionAttempts: 'dontInvert' });
      if (qr && (qr.data !== ultimo || Date.now() - instanteUltimo > 12_000)) {
        ultimo = qr.data; instanteUltimo = Date.now(); pausa = true;
        try { await validar(qr.data); } catch (error) { resultado(error.message, true); } finally { pausa = false; }
      }
    }
    if (flujo) setTimeout(leer, 180);
  }
  alClic('camara', async () => {
    if (flujo) { apagar(); return; }
    if (!navigator.mediaDevices?.getUserMedia || typeof window.jsQR !== 'function') {
      textoCamara.textContent = 'La cámara no está disponible. Puedes ingresar el código de forma manual.';
      return;
    }
    try {
      flujo = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } } });
      if (actual() !== rutaVista) { apagar(); return; }
      video.srcObject = flujo; video.hidden = false; camara.textContent = 'Apagar cámara';
      textoCamara.textContent = 'Coloca el código QR frente a la cámara.';
      leer();
    } catch { textoCamara.textContent = 'No se pudo acceder a la cámara. Usa el código manual.'; }
  });
  async function sincronizar() {
    if (sincronizando) return;
    sincronizando = true;
    try {
      while (cola.length && navigator.onLine) {
        if (!await validar(cola[0], false)) return;
        cola.shift();
      }
    } catch (error) { resultado(error.message, true); } finally { sincronizando = false; }
  }
  const intervalo = setInterval(() => {
    if (actual() !== rutaVista || !video.isConnected) {
      clearInterval(intervalo); window.removeEventListener('online', sincronizar); apagar(); return;
    }
    actualizar().catch(() => {});
    if (cola.length && navigator.onLine) sincronizar();
  }, 10_000);
  window.addEventListener('online', sincronizar);
  await actualizar();
}
