// Tablero interactivo: renderiza desde FEN (llega del servidor en cada
// GameStateSyncMessage) y construye jugadas en notación algebraica para enviar por
// WebSocket. Ya no hay posición fija — todo sale de lo que manda el backend.

const PIECE_UNICODE = {
    wK: '♔', wQ: '♕', wR: '♖', wB: '♗', wN: '♘', wP: '♙',
    bK: '♚', bQ: '♛', bR: '♜', bB: '♝', bN: '♞', bP: '♟',
};

let selectedSquareEl = null;
let selectedAlgebraic = null;
let currentLegalMovesUci = [];
let myColor = null; // 'white' | 'black', fijado al emparejar (ver main.js)
let onMoveAttempt = null; // callback inyectado por main.js cuando el jugador elige una jugada

// displayRow 0 = fila de arriba del tablero (rank 8), displayRow 7 = fila de abajo (rank 1)
// — así el tablero se ve con las blancas abajo, como es convención.
function algebraicFromDisplay(displayRow, file) {
    const fileChar = String.fromCharCode(97 + file); // 0 -> 'a', ..., 7 -> 'h'
    const rankNumber = 8 - displayRow;
    return `${fileChar}${rankNumber}`;
}

function parseFen(fen) {
    const placement = fen.split(' ')[0];
    return placement.split('/').map(row => {
        const cells = [];
        for (const char of row) {
            if (/\d/.test(char)) {
                for (let i = 0; i < Number(char); i++) {
                    cells.push(null);
                }
            } else {
                const color = char === char.toUpperCase() ? 'w' : 'b';
                cells.push(color + char.toUpperCase());
            }
        }
        return cells;
    });
}

/**
 * legalMovesUci determina qué se puede seleccionar/mover — pasa la lista real solo
 * cuando es tu turno (ver main.js), y una lista vacía cuando no lo es, así el tablero
 * queda "de solo lectura" sin necesidad de una comprobación de turno aparte aquí. Lo
 * mismo se aprovecha para el tablero de reproducción del historial (ver history.js):
 * pasando siempre [] queda de solo lectura sin necesitar ningún camino especial aquí.
 *
 * boardElementId: a qué <div> pintar — 'board' (partida en vivo) por defecto, o
 * 'replay-board' al reproducir una partida del historial. Ambos tableros nunca están
 * visibles a la vez (pantallas mutuamente excluyentes), así que compartir el estado de
 * selección global es seguro.
 */
function renderBoard(fen, legalMovesUci, boardElementId = 'board') {
    currentLegalMovesUci = legalMovesUci || [];
    const rows = parseFen(fen);
    const boardEl = document.getElementById(boardElementId);
    boardEl.innerHTML = '';
    selectedSquareEl = null;
    selectedAlgebraic = null;

    for (let displayRow = 0; displayRow < 8; displayRow++) {
        for (let file = 0; file < 8; file++) {
            const square = document.createElement('div');
            const isLight = (file + displayRow) % 2 === 0; // a1 oscura, h1 clara
            square.className = `square square--${isLight ? 'light' : 'dark'}`;
            const algebraic = algebraicFromDisplay(displayRow, file);
            square.dataset.square = algebraic;

            const piece = rows[displayRow][file];
            if (piece) {
                square.textContent = PIECE_UNICODE[piece];
            }

            square.addEventListener('click', () => handleSquareClick(algebraic, square));
            boardEl.appendChild(square);
        }
    }
}

function handleSquareClick(algebraic, squareEl) {
    if (!selectedAlgebraic) {
        // Solo se puede empezar una selección si hay alguna jugada legal desde ahí —
        // cubre a la vez "no es tu turno" (lista vacía) y "esa casilla no tiene una
        // pieza tuya con jugadas posibles".
        const hasLegalMoveFromHere = currentLegalMovesUci.some(uci => uci.startsWith(algebraic));
        if (!hasLegalMoveFromHere) {
            return;
        }
        selectedAlgebraic = algebraic;
        selectedSquareEl = squareEl;
        squareEl.classList.add('square--selected');
        return;
    }

    if (algebraic === selectedAlgebraic) {
        clearSelection();
        return;
    }

    const from = selectedAlgebraic;
    const to = algebraic;
    clearSelection();

    // Si existe una jugada legal from+to con una letra de coronación al final (5
    // caracteres en vez de 4), es una coronación y hay que preguntar qué pieza.
    const isPromotion = currentLegalMovesUci.some(
        uci => uci.startsWith(from + to) && uci.length === 5
    );

    let promotionType = null;
    if (isPromotion) {
        promotionType = askPromotionChoice();
        if (!promotionType) {
            return; // canceló el prompt
        }
    }

    if (onMoveAttempt) {
        onMoveAttempt({ from, to, promotionType });
    }
}

function clearSelection() {
    if (selectedSquareEl) {
        selectedSquareEl.classList.remove('square--selected');
    }
    selectedSquareEl = null;
    selectedAlgebraic = null;
}

function askPromotionChoice() {
    const choice = prompt('¿A qué pieza coronas? (Q = dama, R = torre, B = alfil, N = caballo)', 'Q');
    if (!choice) {
        return null;
    }
    const map = { Q: 'QUEEN', R: 'ROOK', B: 'BISHOP', N: 'KNIGHT' };
    return map[choice.trim().toUpperCase()] || 'QUEEN';
}

/**
 * Pinta una lista de jugadas en UCI ("e2e4", "e7e5"...) como una planilla de dos
 * columnas (blancas/negras) en el <tbody> indicado. Compartida entre la partida en vivo
 * (main.js) y la reproducción del historial (history.js).
 *
 * En UCI, no notación algebraica real (SAN) — eso necesitaría desambiguación entre
 * piezas y símbolos de jaque/mate, que queda para cuando se aborde PGN de verdad
 * (Fase 2, ver docs/architecture-decisions.md). Es una simplificación consciente, no un
 * descuido.
 */
function renderScoresheet(tbodyId, movesUci) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) {
        return;
    }
    tbody.innerHTML = '';

    for (let i = 0; i < movesUci.length; i += 2) {
        const row = document.createElement('tr');

        const numCell = document.createElement('td');
        numCell.textContent = i / 2 + 1;

        const whiteCell = document.createElement('td');
        whiteCell.textContent = movesUci[i] || '';
        whiteCell.dataset.moveIndex = i;

        const blackCell = document.createElement('td');
        if (movesUci[i + 1]) {
            blackCell.textContent = movesUci[i + 1];
            blackCell.dataset.moveIndex = i + 1;
        }

        row.append(numCell, whiteCell, blackCell);
        tbody.appendChild(row);
    }

    const wrap = tbody.closest('.scoresheet-wrap');
    if (wrap) {
        wrap.scrollTop = wrap.scrollHeight;
    }
}

/** Resalta la celda de la jugada en moveIndex (0-based) — usado al recorrer una reproducción. */
function highlightScoresheetMove(tbodyId, moveIndex) {
    const tbody = document.getElementById(tbodyId);
    if (!tbody) {
        return;
    }
    tbody.querySelectorAll('[data-move-index]').forEach((cell) => {
        cell.classList.toggle('scoresheet__move--current', Number(cell.dataset.moveIndex) === moveIndex);
    });
}