# Nexo Eventos

Aplicación web en español para gestionar eventos, entradas y contratación de servicios. Spring Boot sirve la interfaz HTML, CSS y JavaScript y expone la API REST bajo `/api` en el mismo origen. MongoDB Atlas guarda los datos.

## Iniciar en Windows

Necesitas un JDK 25 y un clúster de MongoDB Atlas. El proyecto incluye Maven Wrapper. Al iniciar con el perfil `demo`, los datos de demostración se crean una sola vez en la base `nexo_eventos`. Puedes cambiarla mediante `MONGODB_DATABASE`.

1. En Atlas, abre el clúster y selecciona **Connect → Drivers → Java**. Crea un usuario de base de datos con permiso de lectura y escritura sobre `nexo_eventos` y agrega tu IP en **Network Access**. [Guía de conexión de Atlas](https://www.mongodb.com/docs/atlas/driver-connection/)
2. Abre PowerShell en la carpeta del proyecto y prepara `.env`.

   ```powershell
   if (!(Test-Path -LiteralPath '.env')) { Copy-Item -LiteralPath '.env.example' -Destination '.env' }
   notepad.exe .env
   ```

3. Sustituye el valor de `MONGODB_URI` por la URI de Atlas con las credenciales del usuario de base de datos. Escribe `nexo_eventos` entre la barra del servidor y el signo `?`. Codifica los caracteres especiales de la contraseña para la URI. Guarda el archivo.

   ```properties
   MONGODB_URI=mongodb+srv://usuario_demo:clave_ficticia@cluster-ejemplo.mongodb.net/nexo_eventos?retryWrites=true&w=majority
   ```

   El ejemplo usa datos ficticios. `.env` está ignorado por Git. Puedes definir `MONGODB_URI` como variable de entorno en lugar de usar el archivo. Si existe en ambos sitios, la variable de entorno tiene prioridad.

4. Compila e inicia la aplicación desde la misma carpeta.

   ```powershell
   .\mvnw.cmd clean package
   java -jar .\target\nexo-eventos-1.0.0.jar
   ```

5. Abre [http://localhost:8080](http://localhost:8080). Detén la aplicación con `Ctrl+C`.

En macOS o Linux usa `sh ./mvnw clean package` y `java -jar target/nexo-eventos-1.0.0.jar`.

## Despliegue en la nube

El [Dockerfile](Dockerfile) compila con Java 25 y ejecuta la aplicación con un usuario sin privilegios. El archivo [.dockerignore](.dockerignore) excluye credenciales, cachés y archivos de trabajo de la imagen.

En un servicio con HTTPS administrado por el proveedor, configura:

| Variable | Valor |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `demo,cloud` |
| `MONGODB_URI` | Conexión de Atlas, guardada como secreto en el proveedor, nunca en Git |
| `MONGODB_DATABASE` | Base separada para la demostración, por ejemplo `nexo_eventos_demo` |
| `NEXO_BASE_URL` | URL pública completa, por ejemplo `https://tu-app.ejemplo.com` |
| `PORT` | Puerto indicado por el proveedor; si no existe, se usa `SERVER_PORT` o `8080` |

El perfil `cloud` escucha en `0.0.0.0`, reconoce el HTTPS del proxy y marca la cookie de sesión como segura. No actives el perfil `tls` cuando el proveedor termina HTTPS en su proxy. Restringe el acceso de Atlas a las direcciones de salida del servicio. La ruta `/` permite comprobar que el servidor responde.

Esta entrega usa cuentas públicas de demostración, pagos simulados y un buzón de pruebas; utiliza únicamente datos ficticios en el despliegue. Las sesiones se guardan en memoria y se pierden cuando la instancia reinicia. MongoDB conserva los datos de la aplicación.

## Cuentas de demostración

La contraseña de las cuatro cuentas es `NexoDemo2026!`. Los botones de la pantalla de acceso completan las credenciales ficticias.

| Rol | Correo |
| --- | --- |
| Administrador | `admin@nexo.test` |
| Organizador | `organizador@nexo.test` |
| Asistente | `asistente@nexo.test` |
| Proveedor | `proveedor@nexo.test` |

## Demostrar compra, QR y reembolso

1. Ingresa como **Asistente**. Ve a **Explorar eventos → Festival Horizonte → Comprar entradas**. Selecciona tres entradas y elige **Continuar al pago**. Se reservan los cupos por diez minutos.
2. En **Pagar pedido**, elige **Pago aprobado** y confirma. En el comprobante descarga el PDF, abre los QR y guarda el código manual de la primera entrada. Puedes repetir el flujo con **Pago rechazado por fondos**, **Datos inválidos** o **Sesión expirada**.
3. Ingresa como **Organizador**. Abre **Mis eventos → Festival Horizonte → Control de asistencia**. Escanea el primer QR con la cámara o pega su código manual y valida. Repite el código para ver el rechazo de reutilización. La vista permite probar la asistencia de un evento de demostración antes de la fecha.
4. Vuelve a **Asistente → Mis compras**, abre la compra y elige **Cancelar compra**. Selecciona una entrada todavía válida y confirma. El reembolso aparece como **Iniciado** por **35,50 EUR**, y las otras entradas conservan su estado.
5. Ingresa como **Administrador → Reembolsos**. Pulsa **Completar** para simular la acreditación. Para probar **Fallido**, genera otro reembolso y pulsa **Marcar fallido**. Desde **Buzón de pruebas** puedes consultar destinatario, asunto, contenido y enlaces de cada mensaje.
6. Para probar el reembolso colectivo, usa un evento futuro con compras de demostración. En **Organizador → Mis eventos → evento → Cancelar evento**, registra el motivo y confirma. Los asistentes reciben el aviso, se invalidan las entradas vigentes y aparecen los reembolsos correspondientes.

Para probar contratación, desde **Organizador → Proveedores** solicita **Sonido en directo** para un evento futuro. Desde **Proveedor → Solicitudes recibidas** envía una cotización, con PDF opcional. El organizador puede confirmar o enviar una contrapropuesta. Ambas cuentas ven el historial y pueden descargar el acuerdo.

Para probar el alta de una cuenta, regístrala como administrador y copia el enlace de activación desde el **Buzón de pruebas**. Ábrelo en una ventana privada y define una contraseña. El enlace sirve una vez y vence a las 24 horas.

## Verificaciones

Ejecuta la suite HTTP y las pruebas de mapeo BSON.

```powershell
.\mvnw.cmd test
```

Las pruebas HTTP reutilizan controladores, servicios y configuración de sesión de la aplicación. Su repositorio de pruebas usa memoria y se ejecuta solo desde Maven. La suite verifica permisos, compra y pago, reserva concurrente, reutilización de QR, reembolsos, descargas y otras reglas de negocio.

Para ejecutar la misma suite con Atlas, crea una base separada con nombre terminado en `_pruebas`, por ejemplo `nexo_eventos_pruebas`, y configura a su usuario de base de datos. Guarda una línea `MONGODB_URI=...` con la URI de esa base en `.env.pruebas`. La suite agrega únicamente datos ficticios de verificación a esa base.

```powershell
$lineaNexo = Get-Content -LiteralPath '.env.pruebas' | Where-Object { $_ -match '^MONGODB_URI=' } | Select-Object -First 1
$env:NEXO_ATLAS_URI_PRUEBAS = ($lineaNexo -split '=', 2)[1].Trim()
.\mvnw.cmd '-Dtest=FlujosAtlasTest' test
Remove-Item Env:NEXO_ATLAS_URI_PRUEBAS
```

Para repetir el guion de Edge con el servidor de prueba, necesitas Playwright CLI. Ejecuta en una primera ventana de PowerShell, desde `nexo-eventos`:

```powershell
$env:NEXO_CONTROL_NAVEGADOR = Join-Path $env:TEMP 'nexo-verificacion-browser'
.\mvnw.cmd '-Dtest=FlujosTest#servidorParaNavegador' test
```

Después de que arranque, ejecuta en una segunda ventana desde la misma carpeta:

```powershell
$controlNexo = Join-Path $env:TEMP 'nexo-verificacion-browser'
$puertoNexo = (Get-Content -LiteralPath $controlNexo -Raw).Trim()
playwright-cli -s=nexo-verificacion open "http://127.0.0.1:$puertoNexo/" --browser=msedge
playwright-cli -s=nexo-verificacion run-code --filename=./verificacion/navegador.js
playwright-cli -s=nexo-verificacion close
Remove-Item -LiteralPath $controlNexo
```

Consulta [VERIFICACION.md](VERIFICACION.md) para ver los resultados por historia.

## Estructura y opciones

- `controlador` recibe peticiones REST, valida su formato y mantiene la sesión.
- `servicio` aplica permisos y reglas de negocio. `repositorio` ejecuta operaciones MongoDB. `dominio` contiene los estados y documentos BSON.
- `adaptador` contiene la pasarela y el buzón del perfil `demo`. Los webhooks verifican la firma y los cambios de estado duplicados no vuelven a emitir entradas, reembolsos o correos.
- `src/main/resources/static` contiene las pantallas. El escáner decodifica QR en el navegador mediante jsQR y valida el resultado en la API. Con la pantalla abierta, los códigos pendientes se reenvían al volver la conexión.
- La reserva, el pago, el canje del QR, la cancelación y los reembolsos usan transacciones MongoDB. El precio y el aforo se verifican en el servidor. La moneda es EUR y las fechas se muestran en hora de Bogotá.
- `NEXO_PLAZO_CANCELACION_HORAS` fija el plazo general, 48 horas por defecto. El organizador puede asignar otro plazo por evento.
- `SERVER_PORT` cambia el puerto y `NEXO_BASE_URL` fija el origen de los enlaces de correo. El servidor escucha en la interfaz local por defecto.

### HTTPS y copias de seguridad

Para ejecutar con HTTPS, activa `SPRING_PROFILES_ACTIVE=demo,tls` y configura `NEXO_TLS_KEYSTORE` con la ruta de un PKCS12 que contenga la cadena del certificado, `NEXO_TLS_PASSWORD` con su contraseña y `NEXO_BASE_URL` con tu origen HTTPS. El perfil usa TLS 1.2 y 1.3 y marca la cookie de sesión como segura. Usa un certificado de confianza para el dominio donde se desplegará.

Las copias se administran desde Atlas. En un plan con Cloud Backup, activa las copias administradas y autoriza su restauración únicamente a las cuentas con rol de gestión de backups que correspondan. Gestiona las claves de cifrado con el proveedor de claves de Atlas o con tu KMS, según el plan y la política del clúster. [Copias y permisos de MongoDB Atlas](https://www.mongodb.com/docs/atlas/backup/cloud-backup/overview/)

## Fuentes del proyecto

Las HU y sus criterios de aceptación determinan las acciones y permisos. Las pantallas del HTML se vincularon con las HU mediante sus anclas y funciones. Las cinco vistas del archivo Draw.io local orientaron los módulos, las relaciones de dominio y el despliegue. El PDF se usó como referencia técnica de REST y de la separación entre controladores, servicios y repositorios.
