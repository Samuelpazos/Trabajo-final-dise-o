async page => {
  const errores = [];
  page.on('pageerror', error => errores.push(error.message));
  const boton = (nombre, raiz = page) => raiz.getByRole('button', { name: nombre, exact: true });
  const enlace = (nombre, raiz = page) => raiz.getByRole('link', { name: nombre, exact: true });
  const titulo = nombre => page.getByRole('heading', { name: nombre, exact: true, level: 1 });
  const nav = page.getByRole('navigation', { name: 'Navegación principal' });
  const dialogo = page.getByRole('dialog');
  const confirmar = nombre => boton(nombre, dialogo).click();
  await page.setDefaultTimeout(15000);

  async function ingresar(rol) {
    await boton(rol).click();
    await boton('Ingresar').click();
    await titulo('Panel de ' + rol.toLowerCase()).waitFor();
  }
  async function salir() {
    await boton('Cerrar sesión').click();
    await boton('Ingresar').waitFor();
  }

  await ingresar('Asistente');
  await enlace('Explorar eventos', nav).click();
  await page.getByLabel('Nombre del evento').fill('Horizonte');
  await boton('Aplicar filtros').click();
  await enlace('Festival Horizonte').click();
  await page.getByRole('heading', { name: 'Programación del evento' }).waitFor();
  await boton('Comprar entradas').click();
  await dialogo.getByLabel('Cantidad de entradas').fill('3');
  await confirmar('Continuar al pago');
  await titulo('Pagar pedido').waitFor();
  await boton('Confirmar pago').click();
  await titulo('Comprobante de compra').waitFor();
  const compra = page.url().split('#').at(-1);
  const entradas = page.locator('.entrada');
  if (await entradas.count() !== 3) throw new Error('La compra no muestra tres entradas');
  const qr = await entradas.nth(0).locator('.codigo').innerText();
  await page.waitForFunction(() => document.querySelector('.entrada img')?.naturalWidth > 0);
  const [comprobante] = await Promise.all([page.waitForEvent('download'), enlace('Descargar comprobante PDF').click()]);
  if (!comprobante.suggestedFilename().endsWith('.pdf')) throw new Error('No se descargó un PDF');
  await boton('Cancelar compra').click();
  await dialogo.getByRole('checkbox', { name: 'Seleccionar todas las entradas válidas' }).uncheck();
  await dialogo.getByRole('checkbox', { name: /^Entrada 2/ }).check();
  await dialogo.getByRole('checkbox', { name: 'Entiendo que la selección quedará invalidada' }).check();
  await confirmar('Confirmar cancelación');
  await page.getByRole('heading', { name: 'Seguimiento del reembolso' }).waitFor();
  await page.getByText('Cancelada parcial', { exact: true }).waitFor();

  await salir();
  await ingresar('Organizador');
  await enlace('Mis eventos', nav).click();
  await enlace('Festival Horizonte').click();
  await enlace('Control de asistencia').click();
  await page.setViewportSize({ width: 390, height: 844 });
  const codigo = page.getByLabel('Código de la entrada', { exact: true });
  await codigo.fill(qr);
  await boton('Validar entrada').click();
  await page.getByText(/Entrada válida. Asistencia registrada/).waitFor();
  await codigo.fill(qr);
  await boton('Validar entrada').click();
  await page.getByText(/Esta entrada ya fue utilizada/).waitFor();
  await page.setViewportSize({ width: 1280, height: 900 });
  await enlace('Volver al evento').click();
  await enlace('Ventas y estadísticas').click();
  await page.getByRole('heading', { name: 'Ingresos por día' }).waitFor();
  await enlace('Proveedores', nav).click();
  await boton('Solicitar servicio').click();
  await dialogo.getByRole('combobox', { name: 'Evento', exact: true }).selectOption('festival-horizonte');
  await dialogo.getByLabel('Detalles de la solicitud').fill('Montaje en sala principal');
  await confirmar('Enviar solicitud');
  await page.getByRole('heading', { name: 'Historial de estados' }).waitFor();
  const solicitud = page.url().split('#').at(-1);

  await salir();
  await ingresar('Proveedor');
  await enlace('Solicitudes recibidas', nav).click();
  await enlace('Sonido en directo').click();
  await boton('Presupuestar').click();
  await dialogo.getByLabel('Monto (EUR)').fill('350');
  await confirmar('Enviar respuesta');
  await page.getByRole('button', { name: 'Rechazar', exact: true }).waitFor();
  await page.getByText('Presupuestado', { exact: true }).first().waitFor();

  await salir();
  await ingresar('Organizador');
  await page.goto(page.url().split('#')[0] + '#' + solicitud);
  await boton('Confirmar servicio').click();
  await confirmar('Enviar respuesta');
  await enlace('Descargar acuerdo PDF').waitFor();

  await salir();
  await ingresar('Administrador');
  await enlace('Reembolsos', nav).click();
  await boton('Completar').click();
  await page.getByText('Completado', { exact: true }).waitFor();
  await enlace('Buzón de pruebas', nav).click();
  await page.getByText('Reembolso completado', { exact: true }).waitFor();
  await enlace('Auditoría', nav).click();
  await page.getByLabel('Acción', { exact: true }).fill('QR_DUPLICADO');
  await boton('Aplicar filtros').click();
  await page.getByText('QR_DUPLICADO', { exact: true }).waitFor();

  await salir();
  await ingresar('Asistente');
  await page.goto(page.url().split('#')[0] + '#' + compra);
  await page.getByText('Completado', { exact: true }).waitFor();
  if (errores.length) throw new Error(errores.join('\n'));
  return 'Verificados en Edge: cuatro roles, filtros, agenda, compra, descarga PDF, QR, reutilización, cancelación parcial, reembolso, negociación, buzón y auditoría.';
}
