# Verificación de Nexo Eventos

La suite HTTP arranca Spring Boot con los controladores y servicios del ejecutable y usa un repositorio en memoria. El guion de Edge opera las pantallas contra ese servidor. La verificación de mapeo usa el conversor BSON de Spring Data.

Una historia se marca como **verificada** en el entorno indicado cuando se ejecutó el flujo descrito. La validación completa con persistencia en Atlas está pendiente para las 32 HU. El 25 de noviembre de 2026 se comprobó la conexión real a Atlas: el frontend, el inicio de sesión como asistente y la consulta de los tres eventos respondieron HTTP 200. Esto no sustituye la suite completa de integración.

| HU | Pantalla o flujo | Resultado |
| --- | --- | --- |
| HU-01 | Iniciar sesión y abrir el panel según el rol | Verificada en Edge para los cuatro roles. HTTP comprobó credenciales inválidas y acceso sin sesión. |
| HU-02 | Usuarios → Registrar usuario → Buzón → Configurar contraseña | Verificada por HTTP. Alta, correo ficticio, activación, inicio de sesión y rechazo de duplicados. |
| HU-03 | Usuarios → Ficha → Editar usuario | Verificada por HTTP. Edición y registro de auditoría. |
| HU-04 | Usuarios → Ficha → Activar o desactivar | Verificada por HTTP. Desactivación invalida el acceso y la reactivación lo habilita. |
| HU-05 | Usuarios → Ficha → Dar de baja | Verificada por HTTP. La cuenta conserva su registro y pierde el acceso. |
| HU-06 | Mis eventos → Crear evento → Activar | Verificada por HTTP. Borrador inicial y publicación como organizador. |
| HU-07 | Mis eventos → Ficha → Editar evento | Verificación parcial. Lista y ficha en Edge. El correo por cambio de horario quedó pendiente de prueba específica. |
| HU-08 | Ficha del evento → Cambiar estado | Verificada por HTTP. Oculta el evento del catálogo, conserva el favorito y vuelve a publicar. |
| HU-09 | Ficha del evento → Cancelar evento | Verificada por HTTP. Cancela con motivo, invalida entradas y crea el reembolso del saldo vigente. |
| HU-10 | Explorar eventos | Verificada en Edge y por HTTP. Búsqueda y combinación de nombre, categoría y ubicación. |
| HU-11 | Explorar eventos → Detalle | Verificada en Edge. Detalle, precio, disponibilidad y acceso a la compra. |
| HU-12 | Detalle → Seleccionar entradas → Reservar | Verificada en Edge y por HTTP. Siete peticiones simultáneas para un cupo dieron una reserva y seis conflictos en memoria. |
| HU-13 | Pagar pedido → Pasarela de demostración | Verificada en Edge y por HTTP. Aprobación, rechazo por fondos y confirmación repetida sin duplicar entradas. |
| HU-14 | Comprobante de compra → PDF y QR | Verificada en Edge y por HTTP. Descarga del PDF, PNG visible y decodificación del QR con los datos de la entrada. |
| HU-15 | Evento propio → Ventas y estadísticas | Verificada en Edge y por HTTP. Indicadores, datos de ventas y Excel con gráfico. |
| HU-16 | Evento propio → Nueva actividad | Verificada por HTTP. Persiste la actividad en el repositorio de prueba y rechaza a un proveedor. |
| HU-17 | Detalle → Programación del evento | Verificada en Edge y por HTTP. Agenda visible y actividad accesible para el asistente. |
| HU-18 | Proveedor → Mis servicios | Verificada por HTTP. Alta, edición, ocultación del catálogo y eliminación sin solicitudes. |
| HU-19 | Organizador → Buscar proveedores | Verificada en Edge y por HTTP. Catálogo, oferta y filtro por tipo y precio. |
| HU-20 | Catálogo → Solicitar servicio | Verificada en Edge y por HTTP. Solicitud para un evento propio y contrapropuesta. |
| HU-21 | Proveedor → Solicitudes recibidas | Verificada en Edge y por HTTP. Cotización con PDF, historial y confirmación. |
| HU-22 | Estado de servicios → Historial y acuerdo | Verificada en Edge y por HTTP. Descarga del acuerdo y transición a finalizado. |
| HU-23 | Perfil → Preferencias y notificaciones | Verificación parcial. HTTP verificó guardar y leer preferencias. La entrega programada de recordatorios quedó pendiente de prueba específica. |
| HU-24 | Evento → Control de asistencia | Verificación parcial. Edge validó el código manual en vista móvil y rechazó reutilizarlo. HTTP comprobó el contenido de la imagen QR, el permiso y el código cancelado. Cámara física y reconexión quedaron pendientes. |
| HU-25 | Panel personalizado por rol | Verificada en Edge. Cada rol ve su panel y tiene acceso a sus operaciones. |
| HU-26 | Búsqueda global → Historial | Verificada por HTTP. Búsqueda de servicios, historial por cuenta y exclusión de usuarios privados. |
| HU-27 | Generar reportes | Verificación parcial. HTTP abrió el Excel de ventas y comprobó su gráfico. Los reportes de ingresos, asistencia y servicios quedaron pendientes de prueba específica. |
| HU-28 | Auditoría → Filtrar → Exportar | Verificada en Edge y por HTTP. Duplicados de QR, filtrado y exportación CSV. El generador PDF también se ejecutó para comprobantes. |
| HU-29 | Mis compras → Cancelar compra | Verificada en Edge y por HTTP. Cancelación parcial de entradas, recálculo exacto, devolución de cupos y repetición sin reembolso adicional. |
| HU-30 | Reembolsos → Simular confirmación | Verificada en Edge y por HTTP para iniciado, completado, fallido y confirmación duplicada. La espera de 30 días quedó pendiente de prueba específica. |
| HU-31 | Seguridad y privacidad → Solicitar eliminación → Procesar | Verificada por HTTP. Reautenticación, anonimización y auditoría sin los datos originales. Por decisión del usuario, el intento posterior muestra el error genérico de credenciales. |
| HU-32 | Seguridad y privacidad → TLS, roles y copias | Verificación parcial. Se probaron permisos, sesión y mapeo BSON de datos eliminados. Certificado de confianza y copias de Atlas quedaron bloqueados por falta de infraestructura configurada. |

## Preparación de Render y revisión de dependencias (25 de noviembre de 2026)

- Se añadió un Blueprint gratuito de Render con Docker, perfil `demo,cloud`, URL HTTPS automática y la base separada `nexo_eventos_demo` en Atlas. La creación del servicio y la comprobación de su URL pública están pendientes del inicio de sesión en Render.
- Se aplicaron parches compatibles a Jackson (2.21.9), Tomcat (10.1.62), Log4j (2.25.5) y Commons Lang (3.18.0). Se mantuvo Spring Boot 3.5.16: es la última versión pública disponible de esa rama al realizar la comprobación en Maven Central.
- La nueva ejecución de `FlujosTest` y `MapeoMongoTest` pasó: 11 casos contabilizados, 10 aprobados, 1 omitido, sin fallos ni errores. Estas pruebas no sustituyen la integración completa con Atlas.
- El análisis de dependencias pasó de 22 a 2 alertas conocidas. **No se declara libre de CVE**: siguen presentes CVE-2026-47884 y CVE-2026-47890 en Spring MVC 6.2.19. Los escáneres las clasifican como críticas, pero los avisos oficiales de Spring indican severidad [media para XsltView](https://spring.io/security/cve-2026-47884/) y [baja para fragmentos enviados por SSE](https://spring.io/security/cve-2026-47890/). No se encontraron vistas XSLT ni fragmentos SSE en el código actual; esta revisión de aplicabilidad no equivale a corregir la dependencia. Requieren actualizar a Spring 7.0.9 o superior, o contratar el parche empresarial de Spring 6.2.20. Esa migración mayor se deja pendiente, sin introducirla silenciosamente en el despliegue de demostración.

## Verificaciones pendientes y bloqueadas

- **Atlas.** La suite real queda disponible como `FlujosAtlasTest`. Las transacciones concurrentes, los índices y la persistencia después de reiniciar deben verificarse contra un clúster de pruebas.
- **Cámara.** La imagen QR se decodificó automáticamente en la suite y se pasó al servidor. Falta una prueba con cámara física y pérdida de conexión en un dispositivo.
- **Infraestructura.** El perfil TLS requiere un certificado de confianza para el dominio de despliegue. La política de backup cifrado y su restauración dependen del plan y permisos de Atlas.
- **Escenarios temporales.** Los recordatorios y el paso de reembolso a pendiente de confirmación tienen tareas programadas. Falta ejecutarlas con un reloj de prueba o esperar su ventana real.

Comandos, usuarios ficticios y recorrido manual en [README.md](README.md).
