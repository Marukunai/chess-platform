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

// Los tres tipos de mensaje que puede mandar el backend por /topic/game/{gameId}
// (GameStateSyncMessage, GameOverMessage, ErrorMessage) tienen formas distintas — los
// distinguimos por un campo que solo tiene cada uno, en vez de un campo "type" aparte.
function handleGameMessage(message) {
    if ('boardFen' in message) {
        handleStateSync(message);
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

    // Solo se pasan las jugadas legales cuando es tu turno — así el tablero queda de
    // solo lectura mientras mueve el rival, sin necesidad de otra comprobación.
    renderBoard(state.boardFen, isMyTurn ? state.legalMovesUci : []);
    renderScoresheet('move-list', state.movesNotation);

    document.getElementById('game-message').textContent = state.status === 'CHECK' ? '¡Jaque!' : '';
}

function handleGameOver(gameOver) {
    document.getElementById('game-message').textContent =
        `Partida terminada: ${gameOver.result} (${gameOver.reason})`;
    currentGameId = null;
    stopClockTicking();

    document.getElementById('resign-btn').hidden = true;
    document.getElementById('back-to-lobby-btn').hidden = false;
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

    document.getElementById('resign-btn').hidden = false;
    document.getElementById('back-to-lobby-btn').hidden = true;
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

    document.getElementById('back-to-lobby-btn').addEventListener('click', leaveFinishedGameToLobby);

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