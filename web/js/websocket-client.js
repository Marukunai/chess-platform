// Cliente STOMP sobre WebSocket (SockJS). Conecta contra /ws en el backend.
// BACKEND_WS_URL viene de config.js (cargado antes que este archivo en index.html).
//
// stomp.js 2.3.3 (la versión que usamos, vía CDN) no trae reconexión automática — a
// diferencia de versiones más recientes de @stomp/stompjs, aquí un CONNECT fallido o
// una desconexión es definitiva salvo que alguien vuelva a llamar a connect() a mano.
// Por eso este módulo reintenta él solo con un temporizador, en vez de delegárselo a la
// librería.

let stompClient = null;
let reconnectTimer = null;
let activeToken = null;
let connectionCallbacks = null; // { onConnected, onConnectionLost }
let currentReconnectDelayMs = 0;

// Backoff creciente en vez de un intervalo fijo: empieza rápido (un bache de red suele
// resolverse en segundos) y va espaciándose hasta un tope, para no machacar al backend
// a peticiones si de verdad está caído un buen rato (p. ej. un despliegue).
const INITIAL_RECONNECT_DELAY_MS = 2000;
const MAX_RECONNECT_DELAY_MS = 15000;

/**
 * El JWT va como cabecera Authorization dentro del propio CONNECT de STOMP, no en el
 * handshake HTTP (los navegadores no dejan poner cabeceras propias ahí) — lo valida
 * StompAuthChannelInterceptor en el backend.
 *
 * Reintenta INDEFINIDAMENTE mientras no se llame a disconnect() — a propósito: un
 * backend reiniciándose (o el propio Render "despertando" tras dormir, ver README)
 * puede tardar bastante más de lo que parece razonable esperar como límite fijo, y
 * darse por vencido solo y cerrar la sesión del usuario a media espera es peor que
 * seguir intentándolo. Si de verdad hace falta una salida manual, el propio indicador
 * "Reconectando..." es clicable (ver main.js) para cerrar sesión a mano.
 *
 * onConnected: se llama tras CADA conexión lograda, tanto la primera como cualquier
 * reconexión — quien lo implemente (ver main.js) debe ser capaz de recuperar dónde
 * estaba (por eso existe getStoredActiveGame()), no solo arrancar desde cero.
 * onConnectionLost: se llama cada vez que se pierde la conexión, para mostrar
 * "Reconectando..." sin navegar a ningún sitio ni tocar el token.
 */
function connect(token, onConnected, onConnectionLost) {
    activeToken = token;
    connectionCallbacks = { onConnected, onConnectionLost };
    currentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS;
    openSocket();
}

function openSocket() {
    const socket = new SockJS(BACKEND_WS_URL);
    stompClient = Stomp.over(socket);
    stompClient.debug = null; // silencia el log muy verboso de stomp.js en consola

    const headers = { Authorization: `Bearer ${activeToken}` };

    stompClient.connect(headers, () => {
        currentReconnectDelayMs = INITIAL_RECONNECT_DELAY_MS; // resetea el backoff tras un éxito
        setConnectionStatus('connected');
        connectionCallbacks?.onConnected?.();
    }, (error) => {
        console.error('Conexión WebSocket perdida, reintentando en segundo plano:', error);
        setConnectionStatus('reconnecting');
        connectionCallbacks?.onConnectionLost?.();
        scheduleReconnect();
    });
}

function scheduleReconnect() {
    if (reconnectTimer || !activeToken) {
        return; // ya hay un intento programado, o ya nos desconectamos a propósito
    }
    reconnectTimer = setTimeout(() => {
        reconnectTimer = null;
        if (activeToken) {
            openSocket();
        }
    }, currentReconnectDelayMs);
    currentReconnectDelayMs = Math.min(currentReconnectDelayMs * 1.5, MAX_RECONNECT_DELAY_MS);
}

function disconnect() {
    activeToken = null;
    connectionCallbacks = null;
    if (reconnectTimer) {
        clearTimeout(reconnectTimer);
        reconnectTimer = null;
    }
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
    stompClient = null;
    setConnectionStatus('disconnected');
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
    // Un único callback: los cuatro tipos de mensaje posibles (GameStateSyncMessage,
    // GameOverMessage, DrawOfferMessage, ErrorMessage) se distinguen por su forma en
    // quien los procese (ver handleGameMessage en main.js).
    return stompClient.subscribe(`/topic/game/${gameId}`, (message) => {
        onMessage(JSON.parse(message.body));
    });
}

/**
 * Canal por-usuario, distinto de /topic/game/{gameId} — este NO depende de estar
 * dentro de ninguna partida ni pantalla concreta, se suscribe una sola vez al conectar
 * y se mantiene mientras dure la sesión (ver connectAndGoToLobby en main.js). Hace
 * falta para poder avisar de una revancha a alguien que ya volvió al lobby, está viendo
 * su perfil, o donde sea — /topic/game/{gameId} ya no sirve para eso porque esa partida
 * ya terminó y nadie sigue suscrito a su topic.
 */
function subscribeToUserChannel(userId, onMessage) {
    return stompClient.subscribe(`/topic/user/${userId}`, (message) => {
        onMessage(JSON.parse(message.body));
    });
}

function proposeRematch(opponentUserId, timeControlPreset, myColorInPreviousGame) {
    stompClient.send('/app/rematch/propose', {}, JSON.stringify({
        opponentUserId, timeControlPreset, myColorInPreviousGame,
    }));
}

function respondToRematch(accept) {
    stompClient.send('/app/rematch/respond', {}, JSON.stringify({ accept }));
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

function offerDraw(gameId) {
    stompClient.send(`/app/game/${gameId}/offer-draw`, {}, JSON.stringify({}));
}

function respondToDraw(gameId, accept) {
    stompClient.send(`/app/game/${gameId}/respond-draw`, {}, JSON.stringify({ accept }));
}

function setConnectionStatus(state) {
    const statusEl = document.getElementById('connection-status');
    const labels = { connected: 'Conectado', reconnecting: 'Reconectando...', disconnected: 'Desconectado' };
    statusEl.textContent = labels[state];
    statusEl.className = `status status--${state}`;
    // Clicable mientras no esté conectado, por si alguien no quiere esperar más al
    // reintento automático — ver el listener en main.js.
    statusEl.classList.toggle('status--clickable', state !== 'connected');
}