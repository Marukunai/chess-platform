// Orquesta las tres pantallas (auth -> lobby -> partida) y conecta los mensajes de
// WebSocket con el tablero. Punto de entrada del cliente web.

let currentGameId = null;
let currentTurn = null;
let gameSubscription = null;
let matchmakingSubscription = null;
let userChannelSubscription = null;
let isSearchingForMatch = false;

// Lo justo de la última partida terminada para poder ofrecer la revancha sin depender
// de una GameSession que ya no existe (se elimina del registro nada más terminar, ver
// GameEndNotifier) — a quién retar, con qué modalidad, y qué color tenía yo (para que
// el backend pueda intercambiarlos). null si nunca hubo una partida terminada en esta
// sesión, o si el motivo de fin no viene con todo lo necesario (ver showGameOverModal).
let lastFinishedGame = null;

// Oferta de revancha que nos acaban de proponer (a la espera de que aceptemos o
// rechacemos) — null si no hay ninguna pendiente ahora mismo.
let pendingRematchOffer = null;

// El último perfil que se pidió al servidor — se reutiliza para rellenar el formulario
// de edición sin tener que volver a pedirlo, y se sustituye por la respuesta del PUT
// en cuanto se guarda un cambio.
let currentProfile = null;

const TIME_CONTROL_LABELS = { BULLET: 'bullet', BLITZ: 'blitz', RAPID: 'rápidas', CLASSICAL: 'clásicas' };

// El servidor solo manda el reloj EXACTO cuando algo cambia (una jugada, unirse a la
// partida) — entre medias, si no avanzamos algo en el propio navegador, el reloj se ve
// congelado aunque el de verdad (server-authoritative) siga corriendo por detrás. Este
// estado guarda el último valor conocido + cuándo se recibió, y clockTickInterval lo
// interpola en pantalla cada 250ms. Nunca decide nada del juego — es puramente visual;
// la próxima sincronización real siempre corrige cualquier desviación.
let clockState = null; // { whiteMs, blackMs, turn, syncedAt }
let clockTickInterval = null;

// Recordar en qué partida estábamos (y de qué color) sobrevive a un F5 o a cerrar y
// reabrir la pestaña — ver connectAndGoToLobby(), que la consulta nada más conectar
// (tanto la primera vez como tras cualquier reconexión) para decidir si hay que volver
// derecho a una partida en curso en vez de al lobby. myColor es puramente de
// presentación (de qué lado se pinta el turno, qué jugadas se resaltan) — la única
// autoridad real sobre quién puede mover es el propio backend, así que guardarlo tal
// cual en localStorage no abre ningún hueco de seguridad.
const ACTIVE_GAME_STORAGE_KEY = 'chess-platform-active-game';

function storeActiveGame(gameId, color) {
    localStorage.setItem(ACTIVE_GAME_STORAGE_KEY, JSON.stringify({ gameId, color }));
}

function clearActiveGame() {
    localStorage.removeItem(ACTIVE_GAME_STORAGE_KEY);
}

function getStoredActiveGame() {
    const raw = localStorage.getItem(ACTIVE_GAME_STORAGE_KEY);
    if (!raw) {
        return null;
    }
    try {
        return JSON.parse(raw);
    } catch {
        return null; // localStorage corrupto o de un formato viejo — mejor ignorarlo que romper el arranque
    }
}

function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(el => el.setAttribute('hidden', ''));
    document.getElementById(screenId).removeAttribute('hidden');
}

function formatClock(ms) {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000));
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    return `${minutes}:${String(seconds).padStart(2, '0')}`;
}

function startClockTicking() {
    stopClockTicking();
    clockTickInterval = setInterval(renderClockDisplay, 250);
}

function stopClockTicking() {
    if (clockTickInterval) {
        clearInterval(clockTickInterval);
        clockTickInterval = null;
    }
}

function renderClockDisplay() {
    if (!clockState) {
        return;
    }
    const elapsedMs = Date.now() - clockState.syncedAt;
    const whiteMs = clockState.turn === 'white' ? clockState.whiteMs - elapsedMs : clockState.whiteMs;
    const blackMs = clockState.turn === 'black' ? clockState.blackMs - elapsedMs : clockState.blackMs;
    document.getElementById('clock-white').textContent = formatClock(whiteMs);
    document.getElementById('clock-black').textContent = formatClock(blackMs);
}

// Los cinco tipos de mensaje que puede mandar el backend por /topic/game/{gameId}
// (GameStateSyncMessage, GameOverMessage, DrawOfferMessage, ChatMessage, ErrorMessage)
// tienen formas distintas — los distinguimos por un campo que solo tiene cada uno.
// "offerStatus" (no "status", que ya usa GameStateSyncMessage para jaque) es justo por
// esto, y "senderUsername" es exclusivo de ChatMessage.
function handleGameMessage(message) {
    if ('boardFen' in message) {
        handleStateSync(message);
    } else if ('offerStatus' in message) {
        handleDrawOfferUpdate(message);
    } else if ('senderUsername' in message) {
        appendChatMessage(message);
    } else if ('result' in message) {
        handleGameOver(message);
    } else if ('code' in message) {
        if (message.code === 'GAME_NOT_FOUND') {
            // Pasa al reconectar (F5, o tras perder la conexión un rato) si la partida ya
            // había terminado mientras tanto — no tiene sentido quedarse en la pantalla
            // de partida esperando algo que ya no existe.
            handleGameNoLongerExists();
        } else {
            document.getElementById('game-message').textContent = message.message;
        }
    }
}

/** myUsername viene de ensureWhoAmIDisplayed() — decide si el mensaje es "mío" para colorear el nombre distinto. */
function appendChatMessage(chat) {
    const log = document.getElementById('chat-log');
    const entry = document.createElement('p');
    entry.className = `chat__message ${chat.senderUsername === myUsername ? 'chat__message--mine' : ''}`;

    const senderEl = document.createElement('strong');
    senderEl.textContent = `${chat.senderUsername}: `;
    entry.appendChild(senderEl);
    entry.append(chat.text);

    log.appendChild(entry);
    log.scrollTop = log.scrollHeight;
}

function handleGameNoLongerExists() {
    clearActiveGame();
    if (gameSubscription) {
        gameSubscription.unsubscribe();
        gameSubscription = null;
    }
    stopClockTicking();
    currentGameId = null;
    enterLobby(getUserIdFromToken(getStoredToken()));
}

function handleStateSync(state) {
    clockState = {
        whiteMs: state.whiteTimeRemainingMs,
        blackMs: state.blackTimeRemainingMs,
        turn: state.turn,
        syncedAt: Date.now(),
    };
    renderClockDisplay(); // pinta ya mismo, no esperar al primer tick del intervalo

    document.getElementById('player-name-white').textContent = state.whiteUsername || 'Blancas';
    document.getElementById('player-name-black').textContent = state.blackUsername || 'Negras';
    document.getElementById('clock-row-white').classList.toggle('clock__row--active', state.turn === 'white');
    document.getElementById('clock-row-black').classList.toggle('clock__row--active', state.turn === 'black');

    currentTurn = state.turn;
    const isMyTurn = state.turn === myColor;
    const turnLabel = state.turn === 'white' ? 'blancas' : 'negras';
    document.getElementById('turn-indicator').textContent =
        isMyTurn ? 'Te toca mover' : `Esperando a ${turnLabel}`;

    // Cualquier jugada real retira una oferta de tablas pendiente (el backend ya la
    // limpia también, ver GameSession.applyMove) — así que cualquier sincronización de
    // estado nueva es motivo suficiente para ocultar el aviso, sin esperar un mensaje
    // de tablas aparte.
    hideDrawOfferBanner();

    const checkedColor = state.status === 'CHECK' ? state.turn : null;
    const lastMove = buildLastMoveForAnimation(state);
    // Solo se pasan las jugadas legales cuando es tu turno — así el tablero queda de
    // solo lectura mientras mueve el rival, sin necesidad de otra comprobación.
    renderBoard(state.boardFen, isMyTurn ? state.legalMovesUci : [], 'board', checkedColor, lastMove);
    renderScoresheet('move-list', state.movesNotation);

    document.getElementById('game-message').textContent = state.status === 'CHECK' ? '¡Jaque!' : '';
}

/** { to, wasCapture } para animar en board.js, o null si todavía no se ha jugado nada. */
function buildLastMoveForAnimation(state) {
    if (!state.lastMoveUci) {
        return null;
    }
    const lastNotation = state.movesNotation[state.movesNotation.length - 1] || '';
    return {
        to: state.lastMoveUci.substring(2, 4),
        wasCapture: lastNotation.includes('x'),
    };
}

function handleGameOver(gameOver) {
    const myRatingChange = myColor === 'white' ? gameOver.whiteRatingChange : gameOver.blackRatingChange;

    const iWasWhite = myColor === 'white';
    lastFinishedGame = gameOver.timeControlPreset ? {
        opponentUserId: iWasWhite ? gameOver.blackPlayerId : gameOver.whitePlayerId,
        opponentUsername: iWasWhite ? gameOver.blackUsername : gameOver.whiteUsername,
        timeControlPreset: gameOver.timeControlPreset,
        myColorInThatGame: myColor,
    } : null; // sin preset conocido no se puede proponer "la misma modalidad" con garantías

    showGameOverModal(gameOver, myRatingChange);

    currentGameId = null;
    clearActiveGame();
    stopClockTicking();
    hideDrawOfferBanner();
}

function showGameOverModal(gameOver, myRatingChange) {
    const iWon = (gameOver.result === '1-0' && myColor === 'white')
        || (gameOver.result === '0-1' && myColor === 'black');
    const isDraw = gameOver.result === '1/2-1/2';

    const title = isDraw ? 'Tablas' : (iWon ? '¡Has ganado!' : 'Has perdido');
    const subtitle = GAME_OVER_REASON_LABELS[gameOver.reason] || gameOver.reason;

    document.getElementById('game-over-title').textContent = title;
    document.getElementById('game-over-subtitle').textContent = subtitle;

    const changeEl = document.getElementById('game-over-rating-change');
    const changeText = formatRatingChange(myRatingChange);
    if (changeText) {
        changeEl.textContent = changeText;
        changeEl.className = `rating-change modal-card__rating ${ratingChangeClass(myRatingChange)}`;
        changeEl.hidden = false;
    } else {
        changeEl.hidden = true;
    }

    const card = document.getElementById('game-over-card');
    card.classList.remove('modal-card--win', 'modal-card--loss', 'modal-card--draw');
    card.classList.add(isDraw ? 'modal-card--draw' : (iWon ? 'modal-card--win' : 'modal-card--loss'));

    const rematchBtn = document.getElementById('rematch-btn');
    rematchBtn.hidden = !lastFinishedGame;
    rematchBtn.disabled = false;
    rematchBtn.textContent = 'Revancha';

    document.getElementById('game-over-modal').hidden = false;
}

function hideGameOverModal() {
    document.getElementById('game-over-modal').hidden = true;
}

/**
 * Los mensajes por /topic/user/{userId} (canal persistente, ver websocket-client.js) —
 * hoy solo lo usa la revancha, pero está pensado para cualquier aviso que tenga que
 * llegar sin importar en qué pantalla esté quien lo recibe.
 */
function handleUserChannelMessage(message) {
    if ('gameId' in message && 'color' in message) {
        // Revancha aceptada — funciona exactamente igual que un emparejamiento normal.
        onMatchFound(message);
    } else if ('fromUserId' in message) {
        showRematchOfferToast(message);
    } else if ('byUsername' in message) {
        showTransientNotice(`${message.byUsername} ha rechazado la revancha.`);
        resetRematchButton();
    } else if ('code' in message) {
        showTransientNotice(message.message);
        resetRematchButton();
    }
}

function showRematchOfferToast(offer) {
    pendingRematchOffer = offer;
    const presetLabel = TIME_CONTROL_LABELS[offer.timeControlPreset] || offer.timeControlPreset;
    document.getElementById('rematch-offer-text').textContent =
        `${offer.fromUsername} te propone la revancha (${presetLabel}).`;
    document.getElementById('rematch-offer-toast').hidden = false;
}

function hideRematchOfferToast() {
    pendingRematchOffer = null;
    document.getElementById('rematch-offer-toast').hidden = true;
}

function resetRematchButton() {
    const btn = document.getElementById('rematch-btn');
    if (!btn.hidden) {
        btn.disabled = false;
        btn.textContent = 'Revancha';
    }
}

let transientNoticeTimeout = null;

function showTransientNotice(text) {
    const el = document.getElementById('transient-notice');
    el.textContent = text;
    el.hidden = false;
    clearTimeout(transientNoticeTimeout);
    transientNoticeTimeout = setTimeout(() => {
        el.hidden = true;
    }, 4000);
}

/**
 * offerStatus: "offered_by_white" | "offered_by_black" | "none". Si la oferta es la mía
 * propia, solo cambia el botón (deshabilitado, "esperando..."); si es del rival, se
 * muestra el aviso con Aceptar/Rechazar.
 */
function handleDrawOfferUpdate(message) {
    const offerBtn = document.getElementById('offer-draw-btn');

    if (message.offerStatus === 'none') {
        hideDrawOfferBanner();
        return;
    }

    const offeredByMe = (message.offerStatus === 'offered_by_white' && myColor === 'white')
        || (message.offerStatus === 'offered_by_black' && myColor === 'black');

    if (offeredByMe) {
        document.getElementById('draw-offer-banner').hidden = true;
        offerBtn.disabled = true;
        offerBtn.textContent = 'Tablas ofrecidas, esperando...';
    } else {
        offerBtn.disabled = true;
        document.getElementById('draw-offer-text').textContent = 'Tu rival ofrece tablas.';
        document.getElementById('draw-offer-banner').hidden = false;
    }
}

function hideDrawOfferBanner() {
    document.getElementById('draw-offer-banner').hidden = true;
    const offerBtn = document.getElementById('offer-draw-btn');
    offerBtn.disabled = false;
    offerBtn.textContent = 'Ofrecer tablas';
}

/**
 * El mismo botón hace de "Buscar partida" / "Cancelar búsqueda" según toque — evita un
 * segundo botón aparte, y dejar isSearchingForMatch en false (el estado por defecto,
 * ver arriba) es justo lo que hace que el mensaje "Buscando rival..." no se quede
 * pegado al volver al lobby tras una partida: setSearchingState(false) se llama
 * explícitamente ahí (ver leaveFinishedGameToLobby) para limpiarlo.
 */
function setSearchingState(searching) {
    isSearchingForMatch = searching;
    const btn = document.getElementById('find-match-btn');
    const statusEl = document.getElementById('matchmaking-status');
    const select = document.getElementById('time-control-select');

    btn.textContent = searching ? 'Cancelar búsqueda' : 'Buscar partida';
    btn.classList.toggle('btn--primary', !searching);
    btn.classList.toggle('btn--danger', searching);
    select.disabled = searching;
    statusEl.textContent = searching ? 'Buscando rival...' : '';
}

// Nombre propio, cacheado tras consultarlo una vez — para el indicador persistente
// junto a "Conectado" (ver ensureWhoAmIDisplayed). Se pide a /api/users/{userId} (ya
// existía para el perfil) en vez de sacarlo del JWT, porque el JWT solo lleva el
// userId, no el nombre de usuario.
let myUsername = null;

async function ensureWhoAmIDisplayed(userId) {
    if (!myUsername) {
        try {
            const profile = await fetchUserProfile(userId);
            myUsername = profile.username;
        } catch {
            return; // no es crítico — un adorno de cabecera, no bloqueamos nada por esto
        }
    }
    const el = document.getElementById('whoami');
    el.textContent = '';
    el.append('Conectado como ');
    const strong = document.createElement('strong');
    strong.textContent = myUsername;
    el.appendChild(strong);
    el.hidden = false;
}

function hideWhoAmI() {
    myUsername = null;
    document.getElementById('whoami').hidden = true;
}

function connectAndGoToLobby(token) {
    connect(token, () => {
        // Se llama tras CADA conexión lograda, no solo la primera — así una reconexión
        // en mitad de una partida (o simplemente recargar la página) nos devuelve
        // exactamente donde estábamos en vez de mandarnos siempre al lobby.
        const userId = getUserIdFromToken(token);
        ensureWhoAmIDisplayed(userId);
        // Canal persistente para avisos que no dependen de ninguna pantalla concreta
        // (hoy: ofertas de revancha) — la suscripción vieja, si la había, ya murió con
        // la conexión anterior, así que volver a suscribirse aquí es lo correcto tanto
        // la primera vez como tras cualquier reconexión.
        userChannelSubscription = subscribeToUserChannel(userId, handleUserChannelMessage);
        const stored = getStoredActiveGame();
        if (stored) {
            enterGameScreen(stored.gameId, stored.color);
        } else {
            enterLobby(userId);
        }
    }, () => {
        // Conexión perdida pero seguimos reintentando en segundo plano, indefinidamente
        // (ver websocket-client.js) — no navegamos a ningún sitio ni tocamos el token
        // solo por esto. El indicador "Reconectando..." ya lo deja claro por sí solo, y
        // es clicable si alguien no quiere esperar (ver el listener de
        // connection-status más abajo).
    });
}

function enterLobby(userId) {
    showScreen('lobby-screen');
    matchmakingSubscription = subscribeToMatchmaking(userId, (message) => {
        if (message.gameId) {
            onMatchFound(message);
        } else if (message.code) {
            document.getElementById('matchmaking-status').textContent = message.message;
        }
    });
}

/**
 * Punto único para entrar en la pantalla de partida — tanto al emparejar de cero
 * (onMatchFound) como al recuperar una partida en curso (connectAndGoToLobby, tras
 * F5 o una reconexión). Volver a llamarla sobre una partida ya en marcha es seguro:
 * vuelve a suscribirse (la suscripción vieja murió con la conexión anterior) y vuelve
 * a pedir el estado, sin duplicar nada.
 */
function enterGameScreen(gameId, color) {
    currentGameId = gameId;
    myColor = color;
    storeActiveGame(gameId, color);

    hideGameOverModal();
    hideDrawOfferBanner();
    hideRematchOfferToast();
    document.getElementById('game-message').textContent = '';
    document.getElementById('chat-log').innerHTML = '';

    gameSubscription = subscribeToGame(gameId, handleGameMessage);
    joinGame(gameId);
    showScreen('game-screen');
    startClockTicking();
}

function onMatchFound(match) {
    setSearchingState(false);

    if (matchmakingSubscription) {
        matchmakingSubscription.unsubscribe();
        matchmakingSubscription = null;
    }

    enterGameScreen(match.gameId, match.color);
}

function leaveFinishedGameToLobby() {
    if (gameSubscription) {
        gameSubscription.unsubscribe();
        gameSubscription = null;
    }
    stopClockTicking();
    clockState = null;
    clearActiveGame();
    hideGameOverModal();
    setSearchingState(false); // por si venía pegado "Buscando rival..." de antes de esta partida
    showScreen('lobby-screen');
}

document.addEventListener('DOMContentLoaded', () => {
    // El tablero necesita saber a quién avisar cuando el jugador elige una jugada.
    onMoveAttempt = (move) => {
        if (currentGameId) {
            sendMove(currentGameId, move);
        }
    };

    // Si ya había un token guardado de una visita anterior, saltamos directo al lobby
    // en vez de pedir login otra vez.
    const storedToken = getStoredToken();
    if (storedToken) {
        connectAndGoToLobby(storedToken);
    }

    document.getElementById('auth-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const username = document.getElementById('auth-username').value;
        const password = document.getElementById('auth-password').value;
        try {
            const token = await loginUser(username, password);
            connectAndGoToLobby(token);
        } catch (error) {
            document.getElementById('auth-error').textContent = error.message;
        }
    });

    document.getElementById('register-btn').addEventListener('click', async () => {
        const username = document.getElementById('auth-username').value;
        const password = document.getElementById('auth-password').value;
        try {
            const token = await registerUser(username, password);
            connectAndGoToLobby(token);
        } catch (error) {
            document.getElementById('auth-error').textContent = error.message;
        }
    });

    document.getElementById('logout-btn').addEventListener('click', () => {
        clearStoredToken();
        clearActiveGame();
        disconnect();
        hideWhoAmI();
        showScreen('auth-screen');
    });

    // Salida manual por si alguien no quiere esperar al reintento automático (que ahora
    // no tiene límite, ver websocket-client.js) — p. ej. si sabe que su token está
    // caducado de verdad y prefiere volver a entrar ya. Solo hace algo mientras el
    // indicador NO está en verde (tiene la clase status--clickable, ver setConnectionStatus).
    document.getElementById('connection-status').addEventListener('click', (event) => {
        if (!event.target.classList.contains('status--clickable')) {
            return;
        }
        const giveUp = confirm('Seguimos intentando reconectar. ¿Prefieres cerrar sesión y volver a entrar tú mismo?');
        if (giveUp) {
            clearStoredToken();
            clearActiveGame();
            disconnect();
            hideWhoAmI();
            showScreen('auth-screen');
        }
    });

    document.getElementById('find-match-btn').addEventListener('click', () => {
        if (isSearchingForMatch) {
            leaveMatchmakingQueue();
            setSearchingState(false);
        } else {
            const timeControl = document.getElementById('time-control-select').value;
            joinMatchmakingQueue(timeControl);
            setSearchingState(true);
        }
    });

    document.getElementById('resign-btn').addEventListener('click', () => {
        if (currentGameId) {
            sendResign(currentGameId);
        }
    });

    document.getElementById('offer-draw-btn').addEventListener('click', () => {
        if (currentGameId) {
            offerDraw(currentGameId);
        }
    });

    document.getElementById('draw-accept-btn').addEventListener('click', () => {
        if (currentGameId) {
            respondToDraw(currentGameId, true);
        }
    });

    document.getElementById('draw-decline-btn').addEventListener('click', () => {
        if (currentGameId) {
            respondToDraw(currentGameId, false);
        }
    });

    document.getElementById('modal-back-to-lobby-btn').addEventListener('click', leaveFinishedGameToLobby);

    document.getElementById('chat-form').addEventListener('submit', (event) => {
        event.preventDefault();
        const input = document.getElementById('chat-input');
        const text = input.value.trim();
        if (text && currentGameId) {
            sendChatMessage(currentGameId, text);
            input.value = '';
        }
    });

    document.getElementById('rematch-btn').addEventListener('click', () => {
        if (!lastFinishedGame) {
            return;
        }
        proposeRematch(
            lastFinishedGame.opponentUserId,
            lastFinishedGame.timeControlPreset,
            lastFinishedGame.myColorInThatGame
        );
        const btn = document.getElementById('rematch-btn');
        btn.disabled = true;
        btn.textContent = 'Esperando respuesta...';
    });

    document.getElementById('rematch-accept-btn').addEventListener('click', () => {
        respondToRematch(true);
        hideRematchOfferToast();
    });

    document.getElementById('rematch-decline-btn').addEventListener('click', () => {
        respondToRematch(false);
        hideRematchOfferToast();
    });

    document.getElementById('history-btn').addEventListener('click', async () => {
        const userId = getUserIdFromToken(getStoredToken());
        try {
            const games = await fetchUserHistory(userId);
            renderHistoryList(games, userId, async (gameId) => {
                try {
                    const game = await fetchGameDetail(gameId);
                    openReplay(game);
                    showScreen('replay-screen');
                } catch (error) {
                    alert(error.message);
                }
            });
            showScreen('history-screen');
        } catch (error) {
            alert(error.message);
        }
    });

    document.getElementById('history-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
    });

    document.getElementById('replay-back-btn').addEventListener('click', () => {
        showScreen('history-screen');
    });

    document.getElementById('replay-prev-btn').addEventListener('click', replayGoToPrevious);
    document.getElementById('replay-next-btn').addEventListener('click', replayGoToNext);

    document.getElementById('profile-btn').addEventListener('click', async () => {
        const userId = getUserIdFromToken(getStoredToken());
        try {
            currentProfile = await fetchUserProfile(userId);
            renderProfile(currentProfile);
            showScreen('profile-screen');
        } catch (error) {
            alert(error.message);
        }
    });

    document.getElementById('profile-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
    });

    document.getElementById('edit-profile-btn').addEventListener('click', () => {
        if (currentProfile) {
            fillEditProfileForm(currentProfile);
        }
        showScreen('edit-profile-screen');
    });

    document.getElementById('edit-profile-cancel-btn').addEventListener('click', () => {
        showScreen('profile-screen');
    });

    document.getElementById('edit-profile-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const userId = getUserIdFromToken(getStoredToken());
        const username = document.getElementById('edit-username').value.trim();
        const country = document.getElementById('edit-country').value.trim();
        const avatarUrl = document.getElementById('edit-avatar-url').value.trim();

        try {
            currentProfile = await updateUserProfile(userId, { username, country, avatarUrl });
            renderProfile(currentProfile);
            // El indicador "Conectado como..." también debe reflejar el nombre nuevo —
            // fijamos myUsername primero para que ensureWhoAmIDisplayed lo reutilice sin
            // volver a pedirlo al servidor (ver su propia lógica: solo pide si está vacío).
            myUsername = currentProfile.username;
            ensureWhoAmIDisplayed(userId);
            showScreen('profile-screen');
        } catch (error) {
            document.getElementById('edit-profile-error').textContent = error.message;
        }
    });

    document.getElementById('leaderboard-btn').addEventListener('click', async () => {
        const userId = getUserIdFromToken(getStoredToken());
        try {
            renderLeaderboard(await fetchLeaderboard(), userId);
            showScreen('leaderboard-screen');
        } catch (error) {
            alert(error.message);
        }
    });

    document.getElementById('leaderboard-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
    });
});