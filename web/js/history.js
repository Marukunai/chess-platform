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
        meta.textContent = `${game.result} · ${game.timeControl} · ${date}`;

        item.append(players, meta);
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

function openReplay(game) {
    replayFenPositions = game.fenPositions;
    replayMoves = game.moves;
    replayIndex = 0;
    document.getElementById('replay-info').textContent =
        `${game.whiteUsername} vs ${game.blackUsername} — ${game.result} (${game.timeControl})`;
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