// Orquesta las tres pantallas (auth -> lobby -> partida) y conecta los mensajes de
// WebSocket con el tablero. Punto de entrada del cliente web.

let currentGameId = null;
let currentTurn = null;
let gameSubscription = null;
let matchmakingSubscription = null;
let userChannelSubscription = null;
let isSearchingForMatch = false;

// Nombre del rival silenciado en la partida ACTUAL — null si no hay nadie silenciado.
// mutedInGameId guarda a qué partida pertenece ese silencio: enterGameScreen() se llama
// tanto al emparejar de cero como al reconectar (F5) a una partida YA en curso, y en
// ese segundo caso el silencio debe seguir en pie — solo se resetea cuando el gameId
// que llega es distinto del que tenía el silencio guardado, es decir, cuando de verdad
// es una partida nueva.
let mutedInGameUsername = null;
let mutedInGameId = null;

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
// presentación (de qué lado se pinta el turno, qué jugadas se resaltan, y ahora también
// qué bando se pinta abajo en el tablero — ver renderBoard() en board.js) — la única
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
    updateReturnToGameButton();
}

/**
 * Se recalcula solo (sin depender de que cada sitio que cambia de pantalla o termina
 * una partida se acuerde de avisar) mirando dos cosas: si hay una partida en curso
 * (currentGameId) y cuál es la pantalla visible ahora mismo. Cubre el hueco real: antes
 * de esto, entrar a "Ver perfil" o "Editar perfil" desde una partida en marcha te
 * dejaba sin ninguna forma de volver salvo recargar la página — la suscripción y el
 * reloj seguían vivos de fondo (nunca se cortan solo por cambiar de pantalla), así que
 * solo hacía falta un camino de vuelta, no reconectar nada.
 */
function updateReturnToGameButton() {
    const btn = document.getElementById('return-to-game-btn');
    const activeScreen = document.querySelector('.screen:not([hidden])');
    const activeScreenId = activeScreen ? activeScreen.id : null;
    btn.hidden = !(currentGameId && activeScreenId !== 'game-screen');
}

function returnToActiveGame() {
    if (currentGameId) {
        showScreen('game-screen');
    }
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
/**
 * chat.senderUsername === mutedInGameUsername -> ni se pinta. Silenciar es puramente
 * del cliente y de esta partida concreta — no hay nada que avisar al servidor ni que
 * guardar en ningún sitio: el rival de una partida emparejada al azar normalmente no
 * vuelve a cruzarse contigo, así que no tiene sentido un "bloqueo" persistente como el
 * que sí tendría sentido entre amigos. Los mensajes ya pintados ANTES de silenciar se
 * quedan donde están — silenciar oculta lo que llega después, no reescribe lo ya visto.
 */
function appendChatMessage(chat) {
    if (chat.senderUsername === mutedInGameUsername) {
        return;
    }
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

/** null mientras no se conozca el color propio o el nombre del rival todavía no haya llegado en ningún GameStateSyncMessage. */
function getOpponentUsername() {
    if (!myColor) {
        return null;
    }
    const opponentNameId = myColor === 'white' ? 'player-name-black' : 'player-name-white';
    return document.getElementById(opponentNameId).textContent || null;
}

function toggleMuteOpponent() {
    const opponentUsername = getOpponentUsername();
    if (!opponentUsername) {
        return;
    }
    if (mutedInGameUsername === opponentUsername) {
        mutedInGameUsername = null;
        mutedInGameId = null;
        showTransientNotice(`Ya no silencias a ${opponentUsername}.`);
    } else {
        mutedInGameUsername = opponentUsername;
        mutedInGameId = currentGameId;
        showTransientNotice(`Has silenciado a ${opponentUsername} en esta partida.`);
    }
    updateMuteButtonLabel();
}

function updateMuteButtonLabel() {
    const btn = document.getElementById('mute-opponent-btn');
    const opponentUsername = getOpponentUsername();
    if (mutedInGameUsername) {
        btn.textContent = `🔊 Dejar de silenciar a ${opponentUsername || 'el rival'}`;
        btn.classList.add('chat__mute-btn--active');
    } else {
        btn.textContent = `🔇 Silenciar a ${opponentUsername || 'el rival'}`;
        btn.classList.remove('chat__mute-btn--active');
    }
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

// IDs de los dos jugadores de la partida en curso — para poder pedir el perfil del
// rival al hacer clic en su nombre (ver showProfileQuickView). Se renuevan en cada
// sincronización de estado, no solo al empezar la partida.
let currentWhitePlayerId = null;
let currentBlackPlayerId = null;

function handleStateSync(state) {
    clockState = {
        whiteMs: state.whiteTimeRemainingMs,
        blackMs: state.blackTimeRemainingMs,
        turn: state.turn,
        syncedAt: Date.now(),
    };
    renderClockDisplay(); // pinta ya mismo, no esperar al primer tick del intervalo

    currentWhitePlayerId = state.whitePlayerId;
    currentBlackPlayerId = state.blackPlayerId;
    document.getElementById('player-name-white').textContent = state.whiteUsername || 'Blancas';
    document.getElementById('player-name-black').textContent = state.blackUsername || 'Negras';
    updateMuteButtonLabel(); // depende del nombre del rival, que puede que se acabe de conocer justo ahora
    setClockAvatar('player-avatar-white', state.whiteAvatarUrl);
    setClockAvatar('player-avatar-black', state.blackAvatarUrl);
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
    renderBoard(state.boardFen, isMyTurn ? state.legalMovesUci : [], 'board', checkedColor, lastMove, myColor || 'white');
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

function setClockAvatar(elementId, avatarUrl) {
    const el = document.getElementById(elementId);
    if (avatarUrl) {
        el.src = avatarUrl;
        el.hidden = false;
    } else {
        el.hidden = true;
    }
}

/**
 * Vista rápida del perfil de cualquiera — al hacer clic en un nombre en partida o en
 * una fila del ranking (ver profile.js). Reutiliza /api/users/{userId}, el mismo
 * endpoint público que ya usa la pantalla de perfil completa.
 */
async function showProfileQuickView(userId) {
    if (!userId) {
        return;
    }
    try {
        const profile = await fetchUserProfile(userId);
        renderProfileQuickView(profile);
        document.getElementById('profile-quickview-modal').hidden = false;
    } catch (error) {
        showTransientNotice(error.message);
    }
}

function renderProfileQuickView(profile) {
    const avatarEl = document.getElementById('quickview-avatar');
    if (profile.avatarUrl) {
        avatarEl.src = profile.avatarUrl;
        avatarEl.alt = `Avatar de ${profile.username}`;
        avatarEl.hidden = false;
    } else {
        avatarEl.hidden = true;
    }

    document.getElementById('quickview-username').textContent = profile.username;

    const countryEl = document.getElementById('quickview-country');
    if (profile.country) {
        countryEl.textContent = profile.country;
        countryEl.hidden = false;
    } else {
        countryEl.hidden = true;
    }

    document.getElementById('quickview-rating').textContent = profile.rating;
    document.getElementById('quickview-winrate').textContent = `${profile.winRatePercent}% de victorias`;
    document.getElementById('quickview-games').textContent = profile.gamesPlayed;
    document.getElementById('quickview-wins').textContent = profile.wins;
    document.getElementById('quickview-losses').textContent = profile.losses;
    document.getElementById('quickview-draws').textContent = profile.draws;
}

function hideProfileQuickView() {
    document.getElementById('profile-quickview-modal').hidden = true;
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
    updateReturnToGameButton(); // por si la partida termina mientras estás en otra pantalla
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
 * hoy los usan revancha, amistad, presencia y chat directo, así que hace falta
 * distinguir NUEVE formas. Tres grupos comparten campos entre sí:
 *   - RematchOfferMessage / FriendRequestNotification / DirectMessageNotification
 *     comparten fromUserId+fromUsername (solo el primero tiene timeControlPreset, solo
 *     el tercero tiene messageId+text) — por eso esos dos campos se comprueban ANTES
 *     que el fromUserId genérico, que queda como última opción del grupo.
 *   - RematchDeclinedMessage / FriendRequestAcceptedNotification comparten byUsername
 *     (solo el segundo tiene además byUserId) — mismo motivo, byUserId primero.
 * MessagesReadNotification (readByUserId) no comparte campo con nada más, va aparte.
 */
function handleUserChannelMessage(message) {
    try {
        if ('gameId' in message && 'color' in message) {
            // Revancha aceptada — funciona exactamente igual que un emparejamiento normal.
            onMatchFound(message);
        } else if ('messageId' in message) {
            handleDirectMessageNotification(message);
        } else if ('readByUserId' in message) {
            handleMessagesReadNotification(message);
        } else if ('timeControlPreset' in message) {
            showRematchOfferToast(message);
        } else if ('byUserId' in message) {
            showTransientNotice(`${message.byUsername} ha aceptado tu solicitud de amistad.`);
            refreshFriendsScreenIfVisible();
        } else if ('byUsername' in message) {
            showTransientNotice(`${message.byUsername} ha rechazado la revancha.`);
            resetRematchButton();
        } else if ('status' in message) {
            handlePresenceUpdate(message);
        } else if ('fromUserId' in message) {
            showTransientNotice(`${message.fromUsername} te ha enviado una solicitud de amistad.`);
            refreshFriendsScreenIfVisible();
        } else if ('code' in message) {
            showTransientNotice(message.message);
            resetRematchButton();
        }
    } catch (error) {
        // Mismo motivo que en enterLobby: mejor un error visible en consola que un
        // fallo mudo que deja a alguien con la partida ya empezada de verdad pero sin
        // ninguna señal de ello en su propia pantalla.
        console.error('Fallo al procesar un aviso de /topic/user:', error, message);
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

// Nombre y avatar propios, cacheados tras consultarlos una vez — para el desplegable
// persistente junto a "Conectado" (ver ensureWhoAmIDisplayed). Se piden a
// /api/users/{userId} (ya existía para el perfil) en vez de sacarlos del JWT, porque el
// JWT solo lleva el userId.
let myUsername = null;
let myAvatarUrl = null;

async function ensureWhoAmIDisplayed(userId) {
    if (!myUsername) {
        try {
            const profile = await fetchUserProfile(userId);
            myUsername = profile.username;
            myAvatarUrl = profile.avatarUrl;
        } catch {
            return; // no es crítico — un adorno de cabecera, no bloqueamos nada por esto
        }
    }
    document.getElementById('whoami-name').textContent = myUsername;
    setClockAvatar('whoami-avatar', myAvatarUrl); // mismo helper que los avatares del reloj, mismo comportamiento
    document.getElementById('whoami-dropdown').hidden = false;
    document.getElementById('chat-dropdown').hidden = false;
    refreshChatUnreadBadge(); // para saber ya desde el primer momento si hay algo sin leer, sin esperar a abrir el desplegable
}

function hideWhoAmI() {
    myUsername = null;
    myAvatarUrl = null;
    document.getElementById('whoami-dropdown').hidden = true;
    document.getElementById('whoami-menu').hidden = true;
    document.getElementById('chat-dropdown').hidden = true;
    closeChatDropdown();
}

/** Reutilizada tanto por el botón del lobby como por "Ver mi perfil" del desplegable. */
async function goToProfileScreen() {
    const userId = getUserIdFromToken(getStoredToken());
    try {
        currentProfile = await fetchUserProfile(userId);
        renderProfile(currentProfile);
        showScreen('profile-screen');
    } catch (error) {
        alert(error.message);
    }
}

/**
 * A diferencia del botón "Editar perfil" de dentro de la propia pantalla de perfil
 * (que ya tiene currentProfile cargado a la fuerza), esta versión puede llamarse desde
 * cualquier sitio a través del desplegable — así que primero se asegura de tener el
 * perfil, en vez de asumirlo.
 */
/**
 * El nombre y el avatar quedan fijados dentro de GameSession en cuanto empieza una
 * partida (ver setUsernames()/setAvatars() en el backend) — cambiarlos a media partida
 * dejaría al rival viendo la versión vieja hasta que termine, una inconsistencia
 * confusa sin ningún beneficio real. Borrar la cuenta a media partida también se
 * bloquea (dejaría al rival con un GameSession apuntando a un usuario que ya no existe
 * de verdad). El país no se captura en ningún sitio por partida, así que ese sí se
 * puede cambiar en cualquier momento — y cambiar la contraseña tampoco se bloquea,
 * porque no se muestra ni se usa en ningún sitio de la partida en curso.
 */
function updateEditProfileLockState() {
    const locked = Boolean(currentGameId);
    document.getElementById('edit-profile-locked-notice').hidden = !locked;
    document.getElementById('edit-username').disabled = locked;
    document.getElementById('edit-avatar-url').disabled = locked;
    document.getElementById('open-delete-account-btn').disabled = locked;
}

async function goToEditProfileScreen() {
    if (!currentProfile) {
        await goToProfileScreen();
    }
    if (currentProfile) {
        fillEditProfileForm(currentProfile);
        updateEditProfileLockState();
        showScreen('edit-profile-screen');
    }
}

function performLogout() {
    clearStoredToken();
    clearActiveGame();
    disconnect();
    hideWhoAmI();
    showScreen('auth-screen');
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
        try {
            if (message.gameId) {
                onMatchFound(message);
            } else if (message.code) {
                document.getElementById('matchmaking-status').textContent = message.message;
            }
        } catch (error) {
            // Antes, un fallo aquí dejaba a la persona atascada mirando "Buscando
            // rival..." mientras la partida ya existía de verdad al otro lado (el
            // rival sí entraba) — silencioso y sin ninguna pista de qué había pasado.
            // Como mínimo ahora queda constancia en la consola, y sobre todo: como
            // enterGameScreen ya hace lo imprescindible (guardar la partida, cambiar
            // de pantalla) ANTES de lo que podría fallar, aunque esto se dispare la
            // persona ya está viendo el tablero — ver el reordenamiento de
            // enterGameScreen más abajo.
            console.error('Fallo al procesar el emparejamiento:', error);
        }
    });
}

/**
 * Punto único para entrar en la pantalla de partida — tanto al emparejar de cero
 * (onMatchFound) como al recuperar una partida en curso (connectAndGoToLobby, tras
 * F5 o una reconexión). Volver a llamarla sobre una partida ya en marcha es seguro:
 * vuelve a suscribirse (la suscripción vieja murió con la conexión anterior) y vuelve
 * a pedir el estado, sin duplicar nada.
 *
 * Orden deliberado: lo imprescindible (guardar el estado, cambiar a la pantalla de
 * partida) va primero, y lo demás (limpiar restos de una partida anterior, suscribirse
 * al chat, pedir el estado) va después — si alguno de esos pasos de "limpieza" fallara
 * por lo que sea, la persona ya estaría viendo el tablero en vez de quedarse atascada en
 * el lobby sin ninguna pista de qué pasó.
 */
function enterGameScreen(gameId, color) {
    currentGameId = gameId;
    myColor = color;
    storeActiveGame(gameId, color);
    showScreen('game-screen');

    if (gameId !== mutedInGameId) {
        // Partida distinta de aquella en la que se silenció a alguien (o nunca hubo
        // ningún silencio) — de verdad es una partida nueva, toca resetear.
        mutedInGameUsername = null;
        mutedInGameId = null;
    }

    hideGameOverModal();
    hideDrawOfferBanner();
    hideRematchOfferToast();
    document.getElementById('game-message').textContent = '';
    document.getElementById('chat-log').innerHTML = '';
    updateMuteButtonLabel(); // refleja de inmediato si el silencio sobrevivió (reconexión) o se reseteó (partida nueva)
    setClockAvatar('player-avatar-white', null);
    setClockAvatar('player-avatar-black', null);

    gameSubscription = subscribeToGame(gameId, handleGameMessage);
    joinGame(gameId);
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

/**
 * matchmakingSubscription se cierra y se pone a null en cuanto se encuentra partida
 * (ver onMatchFound) — no vuelve a abrirse sola. Por eso aquí se llama a enterLobby(),
 * no a showScreen('lobby-screen') a secas: sin volver a suscribirse, la próxima vez
 * que se pulsara "Buscar partida" el servidor SÍ emparejaría (y crearía la partida de
 * verdad, jugable desde el otro lado), pero este cliente nunca se enteraría — se
 * quedaría mirando "Buscando rival..." para siempre, sin ningún fallo visible, porque
 * nadie estaría escuchando ya el canal por el que llega el aviso. Es justo el fallo que
 * hacía que recargar la página (F5) "arreglara" el problema: un F5 fuerza a pasar de
 * nuevo por connectAndGoToLobby(), que sí vuelve a suscribirse desde cero.
 */
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
    enterLobby(getUserIdFromToken(getStoredToken()));
}

/**
 * Pinta las dos rejillas de muestras con la selección actual ya marcada, y cablea cada
 * una para aplicar + guardar + repintar (así la muestra seleccionada se actualiza al
 * instante, sin esperar a nada) en cuanto se elige otra opción.
 */
function openBoardSettings() {
    const prefs = getBoardPreferences();

    renderBoardThemeOptions(prefs.boardTheme, (themeId) => {
        applyBoardTheme(themeId);
        saveBoardPreferences({ ...getBoardPreferences(), boardTheme: themeId });
        openBoardSettings(); // repinta las dos rejillas para mover el marco de "seleccionado"
    });

    renderPieceStyleOptions(prefs.pieceStyle, (styleId) => {
        applyPieceStyle(styleId);
        saveBoardPreferences({ ...getBoardPreferences(), pieceStyle: styleId });
        openBoardSettings();
    });

    showScreen('board-settings-screen');
}

document.addEventListener('DOMContentLoaded', () => {
    // Antes que nada — así el primer tablero que se pinte ya sale con el tema guardado,
    // no con el de fábrica un instante antes de "saltar" al correcto.
    applyStoredBoardPreferences();

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

    document.getElementById('logout-btn').addEventListener('click', performLogout);

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
            performLogout();
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

    document.getElementById('chat-emoji-btn').addEventListener('click', (event) => {
        event.stopPropagation();
        toggleEmojiPicker(event.currentTarget, document.getElementById('chat-input'));
    });

    document.getElementById('mute-opponent-btn').addEventListener('click', toggleMuteOpponent);

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

    document.getElementById('return-to-game-btn').addEventListener('click', returnToActiveGame);

    document.getElementById('profile-btn').addEventListener('click', goToProfileScreen);

    document.getElementById('profile-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
    });

    // Desplegable "Conectado como..." — accesible desde cualquier pantalla, no solo el lobby.
    document.getElementById('whoami-toggle').addEventListener('click', (event) => {
        event.stopPropagation(); // si no, el listener de "clic fuera" de abajo lo cerraría en el mismo clic
        const menu = document.getElementById('whoami-menu');
        menu.hidden = !menu.hidden;
    });

    document.getElementById('chat-dropdown-toggle').addEventListener('click', (event) => {
        event.stopPropagation();
        toggleChatDropdown();
    });

    document.getElementById('chat-dropdown-search').addEventListener('input', (event) => {
        renderChatDropdownList(lastFetchedConversations, event.target.value);
    });

    document.addEventListener('click', (event) => {
        const whoamiDropdown = document.getElementById('whoami-dropdown');
        if (!whoamiDropdown.contains(event.target)) {
            document.getElementById('whoami-menu').hidden = true;
        }
        const chatDropdown = document.getElementById('chat-dropdown');
        if (!chatDropdown.contains(event.target)) {
            closeChatDropdown();
        }
        const emojiPanel = document.getElementById('emoji-picker-panel');
        if (!emojiPanel.hidden && !emojiPanel.contains(event.target)) {
            closeEmojiPicker();
        }
    });

    document.getElementById('whoami-view-profile').addEventListener('click', () => {
        document.getElementById('whoami-menu').hidden = true;
        goToProfileScreen();
    });

    document.getElementById('whoami-edit-profile').addEventListener('click', () => {
        document.getElementById('whoami-menu').hidden = true;
        goToEditProfileScreen();
    });

    document.getElementById('whoami-logout').addEventListener('click', () => {
        document.getElementById('whoami-menu').hidden = true;
        performLogout();
    });

    // Vista rápida del perfil del rival — clic en su nombre durante la partida.
    document.getElementById('player-name-white-btn').addEventListener('click', () => {
        showProfileQuickView(currentWhitePlayerId);
    });
    document.getElementById('player-name-black-btn').addEventListener('click', () => {
        showProfileQuickView(currentBlackPlayerId);
    });
    document.getElementById('quickview-close-btn').addEventListener('click', hideProfileQuickView);

    document.getElementById('edit-profile-btn').addEventListener('click', () => {
        if (currentProfile) {
            fillEditProfileForm(currentProfile);
        }
        updateEditProfileLockState();
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
            // El desplegable "Conectado como..." también debe reflejar el nombre/avatar
            // nuevos — los fijamos primero para que ensureWhoAmIDisplayed los reutilice
            // sin volver a pedirlos al servidor (ver su propia lógica: solo pide si
            // myUsername está vacío).
            myUsername = currentProfile.username;
            myAvatarUrl = currentProfile.avatarUrl;
            ensureWhoAmIDisplayed(userId);
            showScreen('profile-screen');
        } catch (error) {
            document.getElementById('edit-profile-error').textContent = error.message;
        }
    });

    document.getElementById('go-to-change-password-btn').addEventListener('click', () => {
        document.getElementById('change-password-form').reset();
        document.getElementById('change-password-error').textContent = '';
        document.getElementById('change-password-success').hidden = true;
        showScreen('change-password-screen');
    });

    document.getElementById('change-password-cancel-btn').addEventListener('click', () => {
        showScreen('edit-profile-screen');
    });

    document.getElementById('change-password-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const errorEl = document.getElementById('change-password-error');
        errorEl.textContent = '';

        const current = document.getElementById('change-password-current').value;
        const next = document.getElementById('change-password-new').value;
        const confirmNext = document.getElementById('change-password-confirm').value;

        if (next !== confirmNext) {
            errorEl.textContent = 'Las dos contraseñas nuevas no coinciden.';
            return;
        }

        try {
            const userId = getUserIdFromToken(getStoredToken());
            await changePassword(userId, current, next);
            document.getElementById('change-password-form').reset();
            document.getElementById('change-password-success').hidden = false;
        } catch (error) {
            errorEl.textContent = error.message;
        }
    });

    // El botón ya viene deshabilitado por updateEditProfileLockState() mientras hay una
    // partida en curso — este listener ni se dispara en ese caso (un <button disabled>
    // no genera eventos de clic), así que no hace falta repetir la comprobación aquí.
    document.getElementById('open-delete-account-btn').addEventListener('click', () => {
        document.getElementById('delete-account-password').value = '';
        document.getElementById('delete-account-error').textContent = '';
        document.getElementById('delete-account-modal').hidden = false;
    });

    document.getElementById('delete-account-cancel-btn').addEventListener('click', () => {
        document.getElementById('delete-account-modal').hidden = true;
    });

    document.getElementById('delete-account-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const errorEl = document.getElementById('delete-account-error');
        errorEl.textContent = '';
        const password = document.getElementById('delete-account-password').value;

        try {
            const userId = getUserIdFromToken(getStoredToken());
            await deleteAccount(userId, password);
            document.getElementById('delete-account-modal').hidden = true;
            performLogout();
            alert('Tu cuenta se ha borrado correctamente.');
        } catch (error) {
            errorEl.textContent = error.message;
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

    document.getElementById('friends-btn').addEventListener('click', async () => {
        document.getElementById('friend-search-input').value = '';
        document.getElementById('friend-search-results').innerHTML = '';
        showScreen('friends-screen');
        await refreshFriendsScreen();
    });

    document.getElementById('friends-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
    });

    // Apariencia del tablero — se llega aquí desde la partida o desde editar perfil,
    // siempre a la misma pantalla (ver board-theme.js). "Volver" siempre al lobby, sin
    // importar de dónde se vino — si había una partida en curso, el propio botón
    // "Volver a la partida" (persistente en la cabecera) ya se encarga de eso.
    document.getElementById('open-board-settings-btn').addEventListener('click', openBoardSettings);
    document.getElementById('go-to-board-settings-from-profile-btn').addEventListener('click', openBoardSettings);

    document.getElementById('board-settings-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
    });

    document.getElementById('friend-search-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const query = document.getElementById('friend-search-input').value.trim();
        const resultsEl = document.getElementById('friend-search-results');
        if (query.length < 2) {
            resultsEl.textContent = 'Escribe al menos 2 caracteres.';
            return;
        }
        try {
            renderSearchResults(await searchUsers(query));
        } catch (error) {
            resultsEl.textContent = '';
            showTransientNotice(error.message);
        }
    });

    document.getElementById('dnd-toggle-input').addEventListener('change', (event) => {
        setDoNotDisturb(event.target.checked);
    });

    document.getElementById('dm-chat-form').addEventListener('submit', async (event) => {
        event.preventDefault();
        const input = document.getElementById('dm-chat-input');
        const text = input.value.trim();
        if (!text || !currentDmFriendId) {
            return;
        }
        try {
            await sendDirectMessage(currentDmFriendId, text);
            appendDirectMessageToLog(myUsername, text, false);
            hideReadReceipt(); // mensaje recién mandado — nadie lo ha podido leer todavía
            input.value = '';
        } catch (error) {
            showTransientNotice(error.message);
        }
    });

    document.getElementById('dm-chat-close-btn').addEventListener('click', hideDirectMessageChat);

    document.getElementById('dm-chat-emoji-btn').addEventListener('click', (event) => {
        event.stopPropagation();
        toggleEmojiPicker(event.currentTarget, document.getElementById('dm-chat-input'));
    });
});