// Historial de partidas: lista las partidas del usuario y las reproduce movimiento a
// movimiento. Reutiliza el mismo renderBoard() del tablero en vivo pero apuntando a
// #replay-board, siempre con legalMovesUci=[] (de solo lectura) — el servidor ya manda
// las posiciones FEN reconstruidas, aquí no hay ninguna regla de ajedrez que aplicar.

let replayFenPositions = [];
let replayMoves = [];
let replayIndex = 0;
let replayGameId = null;
let replayAnalysis = null; // null hasta que se pide el análisis (ver analyzeCurrentReplay) — array de MoveAnalysisResponse después

// Valores estándar de las piezas — igual que en el ajedrez real, el rey no cuenta (no
// se puede capturar, no tiene sentido darle un valor material).
const PIECE_VALUES = { p: 1, n: 3, b: 3, r: 5, q: 9 };

/**
 * Cuenta material a partir del campo de colocación de piezas del FEN — pura
 * aritmética de contar letras, no una regla de ajedrez (no decide qué jugadas son
 * legales ni nada parecido), así que hacerlo en el cliente no choca con que el
 * cliente no tenga motor de reglas propio (ver ADR-011). Devuelve la diferencia
 * blancas-menos-negras: positivo == blancas por delante en material.
 */
function materialDifferenceFromFen(fen) {
    const placement = fen.split(' ')[0];
    let difference = 0;
    for (const char of placement) {
        const value = PIECE_VALUES[char.toLowerCase()];
        if (!value) {
            continue; // número (casillas vacías), '/', o el rey — ninguno cuenta
        }
        difference += char === char.toUpperCase() ? value : -value;
    }
    return difference;
}

function renderReplayMaterial(fen) {
    const diff = materialDifferenceFromFen(fen);
    const el = document.getElementById('replay-material');
    if (diff === 0) {
        el.textContent = 'Material igualado';
    } else {
        const leader = diff > 0 ? 'Blancas' : 'Negras';
        el.textContent = `${leader} +${Math.abs(diff)} de material`;
    }
}

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

async function fetchGameAnalysis(gameId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/games/${gameId}/analysis`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al analizar la partida`);
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
    replayGameId = game.id; // hacía falta para poder pedir el análisis con motor de esta partida en concreto, ver analyzeCurrentReplay()
    replayAnalysis = null; // partida nueva, cualquier análisis anterior ya no aplica
    document.getElementById('replay-eval-bar').hidden = true;
    document.getElementById('replay-analyze-btn').hidden = false;
    document.getElementById('replay-analyze-btn').disabled = false;
    document.getElementById('replay-analyze-btn').textContent = 'Analizar partida con el motor';
    const reasonLabel = GAME_OVER_REASON_LABELS[game.reason] || game.reason;
    const resultText = reasonLabel ? `${game.result} (${reasonLabel})` : game.result;
    document.getElementById('replay-info').textContent =
        `${game.whiteUsername} vs ${game.blackUsername} — ${resultText} (${game.timeControl})`;
    renderScoresheet('replay-move-list', replayMoves);
    renderReplayPosition();
}

async function analyzeCurrentReplay() {
    if (!replayGameId || replayAnalysis) {
        return; // ya analizada, o no hay partida cargada — no hace falta pedirlo otra vez
    }
    const btn = document.getElementById('replay-analyze-btn');
    btn.disabled = true;
    btn.textContent = 'Analizando... puede tardar unos segundos';

    try {
        const result = await fetchGameAnalysis(replayGameId);
        replayAnalysis = result.moves;
        renderScoresheet('replay-move-list', replayMoves, replayAnalysis.map(m => m.classification));
        highlightScoresheetMove('replay-move-list', replayIndex - 1);
        document.getElementById('replay-eval-bar').hidden = false;
        btn.hidden = true; // ya está analizada, no tiene sentido volver a pedirlo
        renderReplayPosition(); // para que la barra de evaluación se rellene con la posición actual
    } catch (error) {
        showTransientNotice('No se pudo analizar la partida — inténtalo de nuevo');
        btn.disabled = false;
        btn.textContent = 'Analizar partida con el motor';
    }
}

/** eval: {evalCentipawns, evalMate} de MoveAnalysisResponse, o null si no hay análisis (posición inicial, o todavía sin pedir el análisis) — ambos en perspectiva de blancas siempre, ver GameAnalysisService. */
function renderReplayEvalBar(evalCentipawns, evalMate) {
    const fill = document.getElementById('replay-eval-bar-fill');
    const label = document.getElementById('replay-eval-bar-label');

    if (evalMate !== null && evalMate !== undefined) {
        fill.style.height = evalMate > 0 ? '95%' : '5%';
        label.textContent = evalMate > 0 ? `M${evalMate}` : `M${Math.abs(evalMate)}`;
        return;
    }
    const cp = evalCentipawns ?? 0;
    // 50% == igualada; cada 20 centésimas de peón mueve la barra un 1%, con margen de
    // 5%-95% para que nunca desaparezca del todo ni en posiciones muy desequilibradas
    // — perder ese margen visual (barra completamente llena/vacía) no aporta nada,
    // solo confunde sobre si sigue habiendo alguna evaluación real detrás.
    const percent = Math.max(5, Math.min(95, 50 + cp / 20));
    fill.style.height = `${percent}%`;
    label.textContent = (cp / 100).toFixed(1);
}

function renderReplayPosition() {
    renderBoard(replayFenPositions[replayIndex], [], 'replay-board');
    highlightScoresheetMove('replay-move-list', replayIndex - 1); // -1: la posición inicial no viene de ninguna jugada
    const label = replayIndex === 0 ? 'Posición inicial' : `Jugada ${replayIndex}`;
    document.getElementById('replay-move-counter').textContent =
        `${label} / ${replayFenPositions.length - 1}`;
    renderReplayMaterial(replayFenPositions[replayIndex]);

    if (replayAnalysis) {
        // replayAnalysis[i] es la evaluación TRAS la jugada i+1 (1-indexada en el
        // propio MoveAnalysisResponse) — la posición 0 (inicial, antes de cualquier
        // jugada) no tiene ninguna entrada correspondiente, se muestra como igualada.
        const moveAnalysis = replayIndex > 0 ? replayAnalysis[replayIndex - 1] : null;
        renderReplayEvalBar(moveAnalysis ? moveAnalysis.evalCentipawns : 0, moveAnalysis ? moveAnalysis.evalMate : null);
    }
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