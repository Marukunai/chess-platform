// Tablero interactivo: renderiza desde FEN (llega del servidor en cada
// GameStateSyncMessage) y construye jugadas en notación algebraica para enviar por
// WebSocket. Ya no hay posición fija — todo sale de lo que manda el backend.

const PIECE_UNICODE = {
    wK: '♔',
    wQ: '♕',
    wR: '♖',
    wB: '♗',
    wN: '♘',
    wP: '♙',

    bK: '♚',
    bQ: '♛',
    bR: '♜',
    bB: '♝',
    bN: '♞',
    bP: '♟',
};

let selectedSquareEl = null;
let selectedAlgebraic = null;
let currentLegalMovesUci = [];
let myColor = null;
let onMoveAttempt = null;


/**
 * Convierte la posición visual de una casilla en coordenadas algebraicas.
 *
 * displayRow 0 = fila superior = rank 8
 * displayRow 7 = fila inferior = rank 1
 */
function algebraicFromDisplay(displayRow, file) {
    const fileChar = String.fromCharCode(97 + file);
    const rankNumber = 8 - displayRow;

    return `${fileChar}${rankNumber}`;
}


/**
 * Convierte la parte de colocación de piezas de un FEN
 * en una matriz 8x8.
 */
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
                const color =
                    char === char.toUpperCase()
                        ? 'w'
                        : 'b';

                cells.push(
                    color + char.toUpperCase()
                );
            }
        }

        return cells;
    });
}


/**
 * Crea el elemento visual de una pieza.
 *
 * El color NO se establece aquí.
 * Se controla mediante CSS variables:
 *
 * --piece-white
 * --piece-black
 * --piece-white-outline
 * --piece-black-outline
 *
 * Esto permite cambiar el set visual desde las preferencias sin tocar
 * la lógica del tablero.
 */
function createPieceElement(piece) {
    const pieceEl = document.createElement('span');

    const pieceColor =
        piece.startsWith('w')
            ? 'white'
            : 'black';

    pieceEl.className =
        `piece piece--${pieceColor}`;

    pieceEl.textContent =
        PIECE_UNICODE[piece];

    pieceEl.setAttribute(
        'aria-label',
        `${pieceColor === 'white'
            ? 'Pieza blanca'
            : 'Pieza negra'} de ajedrez`
    );

    return pieceEl;
}


/**
 * Renderiza el tablero a partir de un FEN.
 *
 * boardElementId permite utilizar la misma función tanto para:
 * - partida en vivo
 * - reproducción del historial
 */
function renderBoard(
    fen,
    legalMovesUci,
    boardElementId = 'board'
) {
    currentLegalMovesUci =
        legalMovesUci || [];

    const rows = parseFen(fen);

    const boardEl =
        document.getElementById(boardElementId);

    if (!boardEl) {
        return;
    }

    boardEl.innerHTML = '';

    selectedSquareEl = null;
    selectedAlgebraic = null;

    for (
        let displayRow = 0;
        displayRow < 8;
        displayRow++
    ) {
        for (
            let file = 0;
            file < 8;
            file++
        ) {
            const square =
                document.createElement('div');

            const isLight =
                (file + displayRow) % 2 === 0;

            square.className =
                `square square--${isLight
                    ? 'light'
                    : 'dark'
                }`;

            const algebraic =
                algebraicFromDisplay(
                    displayRow,
                    file
                );

            square.dataset.square =
                algebraic;

            const piece =
                rows[displayRow][file];

            if (piece) {
                const pieceEl =
                    createPieceElement(piece);

                square.appendChild(pieceEl);
            }

            square.addEventListener(
                'click',
                () =>
                    handleSquareClick(
                        algebraic,
                        square
                    )
            );

            boardEl.appendChild(square);
        }
    }
}


function handleSquareClick(
    algebraic,
    squareEl
) {
    if (!selectedAlgebraic) {

        const hasLegalMoveFromHere =
            currentLegalMovesUci.some(
                uci =>
                    uci.startsWith(algebraic)
            );

        if (!hasLegalMoveFromHere) {
            return;
        }

        selectedAlgebraic =
            algebraic;

        selectedSquareEl =
            squareEl;

        squareEl.classList.add(
            'square--selected'
        );

        return;
    }

    if (algebraic === selectedAlgebraic) {
        clearSelection();
        return;
    }

    const from =
        selectedAlgebraic;

    const to =
        algebraic;

    clearSelection();

    const isPromotion =
        currentLegalMovesUci.some(
            uci =>
                uci.startsWith(
                    from + to
                ) &&
                uci.length === 5
        );

    let promotionType = null;

    if (isPromotion) {
        promotionType =
            askPromotionChoice();

        if (!promotionType) {
            return;
        }
    }

    if (onMoveAttempt) {
        onMoveAttempt({
            from,
            to,
            promotionType
        });
    }
}


function clearSelection() {
    if (selectedSquareEl) {
        selectedSquareEl.classList.remove(
            'square--selected'
        );
    }

    selectedSquareEl = null;
    selectedAlgebraic = null;
}


function askPromotionChoice() {
    const choice =
        prompt(
            '¿A qué pieza coronas? (Q = dama, R = torre, B = alfil, N = caballo)',
            'Q'
        );

    if (!choice) {
        return null;
    }

    const map = {
        Q: 'QUEEN',
        R: 'ROOK',
        B: 'BISHOP',
        N: 'KNIGHT'
    };

    return (
        map[
        choice
            .trim()
            .toUpperCase()
        ] || 'QUEEN'
    );
}


/**
 * Renderiza la planilla de jugadas.
 */
function renderScoresheet(
    tbodyId,
    movesUci
) {
    const tbody =
        document.getElementById(tbodyId);

    if (!tbody) {
        return;
    }

    tbody.innerHTML = '';

    for (
        let i = 0;
        i < movesUci.length;
        i += 2
    ) {
        const row =
            document.createElement('tr');

        const numCell =
            document.createElement('td');

        numCell.textContent =
            i / 2 + 1;

        const whiteCell =
            document.createElement('td');

        whiteCell.textContent =
            movesUci[i] || '';

        whiteCell.dataset.moveIndex =
            i;

        const blackCell =
            document.createElement('td');

        if (movesUci[i + 1]) {
            blackCell.textContent =
                movesUci[i + 1];

            blackCell.dataset.moveIndex =
                i + 1;
        }

        row.append(
            numCell,
            whiteCell,
            blackCell
        );

        tbody.appendChild(row);
    }

    const wrap =
        tbody.closest(
            '.scoresheet-wrap'
        );

    if (wrap) {
        wrap.scrollTop =
            wrap.scrollHeight;
    }
}


/**
 * Resalta la jugada actual en la planilla.
 */
function highlightScoresheetMove(
    tbodyId,
    moveIndex
) {
    const tbody =
        document.getElementById(tbodyId);

    if (!tbody) {
        return;
    }

    tbody
        .querySelectorAll(
            '[data-move-index]'
        )
        .forEach(cell => {
            cell.classList.toggle(
                'scoresheet__move--current',
                Number(
                    cell.dataset.moveIndex
                ) === moveIndex
            );
        });
}