// Historial de partidas: lista las partidas del usuario y las reproduce movimiento a
// movimiento. Reutiliza el mismo renderBoard() del tablero en vivo pero apuntando a
// #replay-board, siempre con legalMovesUci=[] (de solo lectura) — el servidor ya manda
// las posiciones FEN reconstruidas, aquí no hay ninguna regla de ajedrez que aplicar.

let replayFenPositions = [];
let replayMoves = [];
let replayIndex = 0;

async function fetchUserHistory(userId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/games/user/${userId}`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar el historial`);
    }
    return response.json();
}

async function fetchGameDetail(gameId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/games/${gameId}`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar la partida`);
    }
    return response.json();
}

/**
 * viewerUserId: quién está mirando el historial — para colorear cada fila según si esa
 * partida la ganó (verdigris), la perdió (granate) o quedó neutra (tablas, o no se pudo
 * determinar). whiteUserId/blackUserId vienen del backend justo para esto — el cliente
 * solo conoce su propio userId (del JWT), no los nombres de usuario de cada partida.
 */
function renderHistoryList(games, viewerUserId, onSelect) {
    const container = document.getElementById('history-list');
    container.innerHTML = '';

    if (games.length === 0) {
        container.textContent = 'Todavía no has jugado ninguna partida.';
        return;
    }

    for (const game of games) {
        const item = document.createElement('div');
        item.className = `history-item ${outcomeClassFor(game, viewerUserId)}`;

        const players = document.createElement('div');
        players.className = 'history-item__players';
        players.textContent = `${game.whiteUsername} vs ${game.blackUsername}`;

        const meta = document.createElement('div');
        meta.className = 'history-item__meta';
        const date = new Date(game.playedAt).toLocaleString();
        const reasonLabel = GAME_OVER_REASON_LABELS[game.reason] || game.reason;
        meta.textContent = reasonLabel
            ? `${game.result} · ${reasonLabel} · ${game.timeControl} · ${date}`
            : `${game.result} · ${game.timeControl} · ${date}`;

        const rightSide = document.createElement('div');
        rightSide.className = 'history-item__right';
        rightSide.appendChild(meta);

        const ratingChange = ratingChangeForViewer(game, viewerUserId);
        const changeText = formatRatingChange(ratingChange);
        if (changeText) {
            const changeBadge = document.createElement('span');
            changeBadge.className = `rating-change ${ratingChangeClass(ratingChange)}`;
            changeBadge.textContent = changeText;
            rightSide.appendChild(changeBadge);
        }

        item.append(players, rightSide);
        item.addEventListener('click', () => onSelect(game.id));
        container.appendChild(item);
    }
}

function outcomeClassFor(game, viewerUserId) {
    const viewerIsWhite = game.whiteUserId === viewerUserId;
    const viewerIsBlack = game.blackUserId === viewerUserId;
    if (!viewerIsWhite && !viewerIsBlack) {
        return '';
    }
    if (game.result === '1/2-1/2') {
        return '';
    }
    const whiteWon = game.result === '1-0';
    const viewerWon = (viewerIsWhite && whiteWon) || (viewerIsBlack && !whiteWon);
    return viewerWon ? 'history-item--win' : 'history-item--loss';
}

/**
 * "+18" en verdigris, "-15" en granate — compartida entre el historial (aquí) y el
 * mensaje de fin de partida en vivo (main.js), que carga después y puede usarlas.
 * null/undefined cuando no se pudo calcular (p. ej. partidas de antes de este campo, o
 * un fallo al guardar) — en ese caso no se muestra nada, no un "+0" engañoso.
 */
function formatRatingChange(change) {
    if (change === null || change === undefined) {
        return '';
    }
    const rounded = Math.round(change);
    return rounded > 0 ? `+${rounded}` : `${rounded}`;
}

function ratingChangeClass(change) {
    if (change === null || change === undefined || Math.round(change) === 0) {
        return 'rating-change--neutral';
    }
    return change > 0 ? 'rating-change--positive' : 'rating-change--negative';
}

/** viewerUserId decide si se muestra whiteRatingChange o blackRatingChange. */
function ratingChangeForViewer(game, viewerUserId) {
    if (game.whiteUserId === viewerUserId) {
        return game.whiteRatingChange;
    }
    if (game.blackUserId === viewerUserId) {
        return game.blackRatingChange;
    }
    return null;
}

function openReplay(game) {
    replayFenPositions = game.fenPositions;
    replayMoves = game.movesNotation;
    replayIndex = 0;
    const reasonLabel = GAME_OVER_REASON_LABELS[game.reason] || game.reason;
    const resultText = reasonLabel ? `${game.result} (${reasonLabel})` : game.result;
    document.getElementById('replay-info').textContent =
        `${game.whiteUsername} vs ${game.blackUsername} — ${resultText} (${game.timeControl})`;
    renderScoresheet('replay-move-list', replayMoves);
    renderReplayPosition();
}

function renderReplayPosition() {
    renderBoard(replayFenPositions[replayIndex], [], 'replay-board');
    highlightScoresheetMove('replay-move-list', replayIndex - 1); // -1: la posición inicial no viene de ninguna jugada
    const label = replayIndex === 0 ? 'Posición inicial' : `Jugada ${replayIndex}`;
    document.getElementById('replay-move-counter').textContent =
        `${label} / ${replayFenPositions.length - 1}`;
}

function replayGoToPrevious() {
    if (replayIndex > 0) {
        replayIndex--;
        renderReplayPosition();
    }
}

function replayGoToNext() {
    if (replayIndex < replayFenPositions.length - 1) {
        replayIndex++;
        renderReplayPosition();
    }
}