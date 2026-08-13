// Historial de partidas: lista las partidas del usuario y las reproduce movimiento a
// movimiento. Reutiliza el mismo renderBoard() del tablero en vivo pero apuntando a
// #replay-board, siempre con legalMovesUci=[] (de solo lectura) — el servidor ya manda
// las posiciones FEN reconstruidas, aquí no hay ninguna regla de ajedrez que aplicar.

let replayFenPositions = [];
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

function renderHistoryList(games, onSelect) {
    const container = document.getElementById('history-list');
    container.innerHTML = '';

    if (games.length === 0) {
        container.textContent = 'Todavía no has jugado ninguna partida.';
        return;
    }

    for (const game of games) {
        const item = document.createElement('div');
        item.className = 'history-item';
        const date = new Date(game.playedAt).toLocaleString();
        item.textContent = `${game.whiteUsername} vs ${game.blackUsername} — ${game.result} (${game.timeControl}) — ${date}`;
        item.addEventListener('click', () => onSelect(game.id));
        container.appendChild(item);
    }
}

function openReplay(game) {
    replayFenPositions = game.fenPositions;
    replayIndex = 0;
    document.getElementById('replay-info').textContent =
        `${game.whiteUsername} vs ${game.blackUsername} — ${game.result} (${game.timeControl})`;
    renderReplayPosition();
}

function renderReplayPosition() {
    renderBoard(replayFenPositions[replayIndex], [], 'replay-board');
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