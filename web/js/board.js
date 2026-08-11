// Representación mínima del tablero en el cliente. De momento renderiza la posición
// inicial fija; en cuanto el backend emita GameStateSyncMessage con el FEN real,
// renderBoard(fen) debe sustituir a renderInitialPosition().

const PIECE_UNICODE = {
    wK: '♔', wQ: '♕', wR: '♖', wB: '♗', wN: '♘', wP: '♙',
    bK: '♚', bQ: '♛', bR: '♜', bB: '♝', bN: '♞', bP: '♟',
};

const INITIAL_POSITION = [
    ['bR', 'bN', 'bB', 'bQ', 'bK', 'bB', 'bN', 'bR'],
    ['bP', 'bP', 'bP', 'bP', 'bP', 'bP', 'bP', 'bP'],
    [null, null, null, null, null, null, null, null],
    [null, null, null, null, null, null, null, null],
    [null, null, null, null, null, null, null, null],
    [null, null, null, null, null, null, null, null],
    ['wP', 'wP', 'wP', 'wP', 'wP', 'wP', 'wP', 'wP'],
    ['wR', 'wN', 'wB', 'wQ', 'wK', 'wB', 'wN', 'wR'],
];

let selectedSquare = null;

function renderInitialPosition() {
    const boardEl = document.getElementById('board');
    boardEl.innerHTML = '';

    for (let rank = 0; rank < 8; rank++) {
        for (let file = 0; file < 8; file++) {
            const square = document.createElement('div');
            const isLight = (rank + file) % 2 === 0;
            square.className = `square square--${isLight ? 'light' : 'dark'}`;
            square.dataset.file = file;
            square.dataset.rank = rank;

            const piece = INITIAL_POSITION[rank][file];
            if (piece) {
                square.textContent = PIECE_UNICODE[piece];
            }

            square.addEventListener('click', () => handleSquareClick(file, rank, square));
            boardEl.appendChild(square);
        }
    }
}

function handleSquareClick(file, rank, squareEl) {
    // TODO (Fase 1): al seleccionar origen y destino, construir un MoveMessage y
    // enviarlo por WebSocket a /app/game/{gameId}/move en vez de solo marcar visualmente.
    document.querySelectorAll('.square--selected').forEach(el => el.classList.remove('square--selected'));

    if (selectedSquare === null) {
        selectedSquare = { file, rank };
        squareEl.classList.add('square--selected');
    } else {
        console.log('Jugada (pendiente de enviar por WebSocket):', selectedSquare, '->', { file, rank });
        selectedSquare = null;
    }
}
