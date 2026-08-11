// Cliente STOMP sobre WebSocket (SockJS). Conecta contra /ws en el backend.
// TODO (Fase 1): sustituir la URL fija por configuración de entorno cuando haya build step.

const BACKEND_WS_URL = 'http://localhost:8080/ws';

let stompClient = null;

function connect(onConnected) {
    const socket = new SockJS(BACKEND_WS_URL);
    stompClient = Stomp.over(socket);

    stompClient.connect({}, () => {
        setConnectionStatus(true);
        if (onConnected) onConnected();
    }, (error) => {
        console.error('Error de conexión WebSocket:', error);
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

function setConnectionStatus(connected) {
    const statusEl = document.getElementById('connection-status');
    statusEl.textContent = connected ? 'Conectado' : 'Desconectado';
    statusEl.className = `status status--${connected ? 'connected' : 'disconnected'}`;
}
