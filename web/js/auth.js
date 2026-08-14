// Login/registro contra la API REST (/api/auth/**) — HTTP normal, no pasa por
// WebSocket. El JWT que devuelve se guarda en localStorage para no tener que volver a
// hacer login en cada recarga de página.
// BACKEND_HTTP_URL viene de config.js (cargado antes que este archivo en index.html).

const TOKEN_STORAGE_KEY = 'chess-platform-token';

async function registerUser(username, password) {
    return authRequest('/api/auth/register', username, password);
}

async function loginUser(username, password) {
    return authRequest('/api/auth/login', username, password);
}

async function authRequest(path, username, password) {
    const response = await fetch(`${BACKEND_HTTP_URL}${path}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
        const problem = await response.json().catch(() => null);
        throw new Error(problem?.message || problem?.error || `Error ${response.status}`);
    }

    const data = await response.json();
    saveToken(data.token);
    return data.token;
}

function saveToken(token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

function getStoredToken() {
    return localStorage.getItem(TOKEN_STORAGE_KEY);
}

function clearStoredToken() {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
}

/**
 * Decodifica el payload de un JWT SIN verificar la firma. Es seguro hacerlo aquí porque
 * solo leemos nuestras propias claims (el servidor ya verificó el token de verdad al
 * conectar por WebSocket) — nunca uses esto para decidir algo que necesite confianza
 * real, eso es trabajo del servidor.
 */
function decodeJwtPayload(token) {
    const payloadBase64 = token.split('.')[1];
    const normalized = payloadBase64.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized));
}

function getUserIdFromToken(token) {
    return decodeJwtPayload(token).sub;
}