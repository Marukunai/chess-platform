// Orquesta las tres pantallas (auth -> lobby -> partida) y conecta los mensajes de
// WebSocket con el tablero. Punto de entrada del cliente web.

let currentGameId = null;
let currentTurn = null;
let gameSubscription = null;
let matchmakingSubscription = null;

// El servidor solo manda el reloj EXACTO cuando algo cambia (una jugada, unirse a la
// partida) — entre medias, si no avanzamos algo en el propio navegador, el reloj se ve
// congelado aunque el de verdad (server-authoritative) siga corriendo por detrás. Este
// estado guarda el último valor conocido + cuándo se recibió, y clockTickInterval lo
// interpola en pantalla cada 250ms. Nunca decide nada del juego — es puramente visual;
// la próxima sincronización real siempre corrige cualquier desviación.
let clockState = null; // { whiteMs, blackMs, turn, syncedAt }
let clockTickInterval = null;

const GAME_OVER_REASON_LABELS = {
    checkmate: 'Jaque mate',
    resignation: 'Rendición',
    timeout: 'Tiempo agotado',
    stalemate: 'Ahogado',
    'fifty-move-rule': 'Regla de 50 movimientos',
    'threefold-repetition': 'Triple repetición',
    abandonment: 'Abandono',
    agreement: 'Acuerdo mutuo',
};

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

// Los cuatro tipos de mensaje que puede mandar el backend por /topic/game/{gameId}
// (GameStateSyncMessage, GameOverMessage, DrawOfferMessage, ErrorMessage) tienen formas
// distintas — los distinguimos por un campo que solo tiene cada uno. "offerStatus" (no
// "status", que ya usa GameStateSyncMessage para jaque) es justo por esto.
function handleGameMessage(message) {
    if ('boardFen' in message) {
        handleStateSync(message);
    } else if ('offerStatus' in message) {
        handleDrawOfferUpdate(message);
    } else if ('result' in message) {
        handleGameOver(message);
    } else if ('code' in message) {
        document.getElementById('game-message').textContent = message.message;
    }
}

function handleStateSync(state) {
    clockState = {
        whiteMs: state.whiteTimeRemainingMs,
        blackMs: state.blackTimeRemainingMs,
        turn: state.turn,
        syncedAt: Date.now(),
    };
    renderClockDisplay(); // pinta ya mismo, no esperar al primer tick del intervalo

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
    showGameOverModal(gameOver, myRatingChange);

    currentGameId = null;
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

    document.getElementById('game-over-modal').hidden = false;
}

function hideGameOverModal() {
    document.getElementById('game-over-modal').hidden = true;
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

function connectAndGoToLobby(token) {
    connect(token, () => {
        const userId = getUserIdFromToken(token);
        showScreen('lobby-screen');

        matchmakingSubscription = subscribeToMatchmaking(userId, (message) => {
            if (message.gameId) {
                onMatchFound(message);
            } else if (message.code) {
                document.getElementById('matchmaking-status').textContent = message.message;
            }
        });
    }, () => {
        clearStoredToken();
        showScreen('auth-screen');
        document.getElementById('auth-error').textContent = 'Sesión caducada o token inválido, vuelve a entrar.';
    });
}

function onMatchFound(match) {
    currentGameId = match.gameId;
    myColor = match.color;

    if (matchmakingSubscription) {
        matchmakingSubscription.unsubscribe();
        matchmakingSubscription = null;
    }

    hideGameOverModal();
    hideDrawOfferBanner();
    document.getElementById('game-message').textContent = '';

    gameSubscription = subscribeToGame(currentGameId, handleGameMessage);
    joinGame(currentGameId); // pide el estado inicial de la partida
    showScreen('game-screen');
    startClockTicking();
}

function leaveFinishedGameToLobby() {
    if (gameSubscription) {
        gameSubscription.unsubscribe();
        gameSubscription = null;
    }
    stopClockTicking();
    clockState = null;
    hideGameOverModal();
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
        disconnect();
        showScreen('auth-screen');
    });

    document.getElementById('find-match-btn').addEventListener('click', () => {
        const timeControl = document.getElementById('time-control-select').value;
        document.getElementById('matchmaking-status').textContent = 'Buscando rival...';
        joinMatchmakingQueue(timeControl);
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
            renderProfile(await fetchUserProfile(userId));
            showScreen('profile-screen');
        } catch (error) {
            alert(error.message);
        }
    });

    document.getElementById('profile-back-btn').addEventListener('click', () => {
        showScreen('lobby-screen');
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