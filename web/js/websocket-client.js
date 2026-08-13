// Cliente STOMP sobre WebSocket (SockJS). Conecta contra /ws en el backend.
// TODO (Fase 1): sustituir la URL fija por configuración de entorno cuando haya build step.

const BACKEND_WS_URL = 'http://localhost:8080/ws';

let stompClient = null;

// El JWT va como cabecera Authorization dentro del propio CONNECT de STOMP, no en el
// handshake HTTP (los navegadores no dejan poner cabeceras propias ahí) — lo valida
// StompAuthChannelInterceptor en el backend. Sin token válido, el servidor cierra la
// conexión durante el CONNECT.
function connect(token, onConnected) {
    const socket = new SockJS(BACKEND_WS_URL);
    stompClient = Stomp.over(socket);

    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    stompClient.connect(headers, () => {
        setConnectionStatus(true);
        if (onConnected) onConnected();
    }, (error) => {
        console.error('Error de conexión WebSocket (¿token inválido o caducado?):', error);
        setConnectionStatus(false);
    });
}

function subscribeToGame(gameId, onMessage) {
    // TODO (Fase 1): manejar los distintos tipos de mensaje (GameStateSyncMessage,
    // GameOverMessage, ErrorMessage) según su forma en vez de un único callback genérico.
    return stompClient.subscribe(`/topic/game/${gameId}`, (message) => {
        onMessage(JSON.parse(message.body));
    });
}

function sendMove(gameId, move) {
    stompClient.send(`/app/game/${gameId}/move`, {}, JSON.stringify(move));
}

function sendResign(gameId) {
    stompClient.send(`/app/game/${gameId}/resign`, {}, JSON.stringify({ gameId }));
}

function setConnectionStatus(connected) {
    const statusEl = document.getElementById('connection-status');
    statusEl.textContent = connected ? 'Conectado' : 'Desconectado';
    statusEl.className = `status status--${connected ? 'connected' : 'disconnected'}`;
}