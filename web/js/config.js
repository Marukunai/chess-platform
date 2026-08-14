// Único sitio con la URL del backend — cambia sola entre desarrollo local y el backend
// desplegado según desde dónde se sirva el propio cliente, así no hace falta editar
// varios archivos ni mantener dos copias del cliente.

const IS_LOCAL = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

const BACKEND_HTTP_URL = IS_LOCAL
    ? 'http://localhost:8080'
    // TODO: sustituir por la URL real tras el primer despliegue en Render
    // (algo como https://chess-platform-backend.onrender.com).
    : 'https://chess-platform-backend.onrender.com';

const BACKEND_WS_URL = `${BACKEND_HTTP_URL}/ws`;