// Cliente STOMP sobre WebSocket (SockJS). Conecta contra /ws en el backend.
// TODO (Fase 1): sustituir la URL fija por configuración de entorno cuando haya build step.

const BACKEND_WS_URL = 'http://localhost:8080/ws';

let stompClient = null;

// El JWT va como cabecera Authorization dentro del propio CONNECT de STOMP, no en el
// handshake HTTP (los navegadores no dejan poner cabeceras propias ahí) — lo valida
// StompAuthChannelInterceptor en el backend. Sin token válido, el servidor cierra la
// conexión durante el CONNECT (por eso existe onError aquí).
function connect(token, onConnected, onError) {
    const socket = new SockJS(BACKEND_WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silencia el log muy verboso de stomp.js en consola

    const headers = { Authorization: `Bearer ${token}` };

    stompClient.connect(headers, () => {
        setConnectionStatus(true);
        if (onConnected) onConnected();
    }, (error) => {
        console.error('Error de conexión WebSocket (¿token inválido o caducado?):', error);
        setConnectionStatus(false);
        if (onError) onError(error);
    });
}

function disconnect() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
    stompClient = null;
    setConnectionStatus(false);
}

function subscribeToMatchmaking(userId, onMessage) {
    return stompClient.subscribe(`/topic/matchmaking/${userId}`, (message) => {
        onMessage(JSON.parse(message.body));
    });
}

function joinMatchmakingQueue(timeControl) {
    stompClient.send('/app/matchmaking/join', {}, JSON.stringify({ timeControl }));
}

function leaveMatchmakingQueue() {
    stompClient.send('/app/matchmaking/leave', {}, JSON.stringify({}));
}

function subscribeToGame(gameId, onMessage) {
    // Un único callback: los tres tipos de mensaje posibles (GameStateSyncMessage,
    // GameOverMessage, ErrorMessage) se distinguen por su forma en quien los procese
    // (ver handleGameMessage en main.js).
    return stompClient.subscribe(`/topic/game/${gameId}`, (message) => {
        onMessage(JSON.parse(message.body));
    });
}

function joinGame(gameId) {
    stompClient.send(`/app/game/${gameId}/join`, {}, JSON.stringify({}));
}

function sendMove(gameId, move) {
    stompClient.send(`/app/game/${gameId}/move`, {}, JSON.stringify({
        gameId,
        from: move.from,
        to: move.to,
        promotionType: move.promotionType || null,
    }));
}

function sendResign(gameId) {
    stompClient.send(`/app/game/${gameId}/resign`, {}, JSON.stringify({ gameId }));
}

function setConnectionStatus(connected) {
    const statusEl = document.getElementById('connection-status');
    statusEl.textContent = connected ? 'Conectado' : 'Desconectado';
    statusEl.className = `status status--${connected ? 'connected' : 'disconnected'}`;
}