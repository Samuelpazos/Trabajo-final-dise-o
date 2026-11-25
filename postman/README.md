# Pruebas con Postman y MongoDB

## 1. Configurar MongoDB

1. Crea una base de datos `nexo_eventos` en MongoDB Atlas o usa una instancia local.
2. Copia `.env.example` como `.env`.
3. Reemplaza `MONGODB_URI` con tu conexión real. No guardes credenciales reales en Git.

Ejemplo local:

```properties
MONGODB_URI=mongodb://localhost:27017/nexo_eventos
SPRING_PROFILES_ACTIVE=demo
SERVER_PORT=8080
NEXO_BASE_URL=http://localhost:8080
```

La aplicación crea los índices y los datos de demostración al iniciar con el perfil `demo`.

## 2. Iniciar la API

Desde la raíz del proyecto:

```powershell
& 'C:\Users\Hp\.maven\3.9.15\maven-3.9.15\bin\mvn.cmd' spring-boot:run
```

También puedes iniciar el JAR generado con `java -jar target/nexo-eventos-1.0.0.jar`.

## 3. Importar en Postman

Importa estos archivos:

- `Nexo Eventos.postman_collection.json`
- `Nexo Eventos - Local.postman_environment.json`

Selecciona el entorno `Nexo Eventos - Local` y ejecuta las carpetas en orden. Postman conserva la cookie `NEXO_SESION` y el script de login guarda el token CSRF.

El flujo cubre las vistas de acceso, eventos, compras, pagos, QR, reembolsos, panel, auditoría y buzón.
