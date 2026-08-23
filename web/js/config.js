// Único sitio con la URL del backend — cambia sola entre desarrollo local y el backend
// desplegado según desde dónde se sirva el propio cliente, así no hace falta editar
// varios archivos ni mantener dos copias del cliente.

const IS_LOCAL = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';

const BACKEND_HTTP_URL = IS_LOCAL
    ? 'http://localhost:8080'
    : 'https://chess-platform-backend-7ju8.onrender.com';

const BACKEND_WS_URL = `${BACKEND_HTTP_URL}/ws`;