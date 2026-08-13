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
 * queda "de solo lectura" sin necesidad de una comprobación de turno aparte aquí.
 */
function renderBoard(fen, legalMovesUci) {
    currentLegalMovesUci = legalMovesUci || [];
    const rows = parseFen(fen);
    const boardEl = document.getElementById('board');
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