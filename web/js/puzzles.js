/**
 * Puzzles — a diferencia del resto de la plataforma, esto habla con el backend por
 * REST normal, no por STOMP (ver PuzzleController): no hace falta tiempo real, nadie
 * más está resolviendo el mismo puzzle a la vez.
 *
 * Reutiliza renderBoard() de board.js tal cual, pintando en #puzzle-board (un tercer
 * tablero que convive con #board y #replay-board, cada uno con su propio elemento) —
 * y reutiliza también myColor/onMoveAttempt, las mismas variables globales que ya usa
 * la partida en vivo, en vez de inventar un mecanismo de clic/arrastre aparte.
 */

let currentPuzzleId = null;

const PROMOTION_TYPE_TO_UCI_LETTER = { QUEEN: 'q', ROOK: 'r', BISHOP: 'b', KNIGHT: 'n' };

async function fetchNextPuzzle() {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/puzzles/next`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (response.status === 404) {
        return null; // sin puzzles disponibles todavía — no es un error, solo no hay nada que resolver
    }
    if (!response.ok) {
        throw new Error('No se pudo obtener el siguiente puzzle');
    }
    return response.json();
}

async function submitPuzzleAttempt(puzzleId, moveUci) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/puzzles/${puzzleId}/attempt`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ moveUci }),
    });
    if (!response.ok) {
        throw new Error('No se pudo enviar el intento');
    }
    return response.json();
}

function openPuzzleScreen() {
    showScreen('puzzle-screen');
    loadNextPuzzle();
}

async function loadNextPuzzle() {
    document.getElementById('puzzle-feedback').hidden = true;
    document.getElementById('puzzle-next-btn').hidden = true;
    document.getElementById('puzzle-turn-label').textContent = 'Buscando el siguiente puzzle...';
    document.getElementById('puzzle-rating-label').textContent = '';
    currentPuzzleId = null;

    let puzzle;
    try {
        puzzle = await fetchNextPuzzle();
    } catch (error) {
        showTransientNotice('No se pudo cargar el puzzle — inténtalo de nuevo');
        return;
    }

    if (!puzzle) {
        document.getElementById('puzzle-turn-label').textContent =
            'Todavía no hay puzzles disponibles — se generan solos tras cada partida jugada, vuelve más tarde';
        return;
    }

    currentPuzzleId = puzzle.puzzleId;
    myColor = puzzle.sideToMove; // para que board.js sepa de quién son las piezas que se pueden mover
    renderBoard(puzzle.fen, puzzle.legalMovesUci, 'puzzle-board', null, null, puzzle.sideToMove);

    document.getElementById('puzzle-turn-label').textContent =
        puzzle.sideToMove === 'white' ? 'Juegan blancas — encuentra la mejor jugada' : 'Juegan negras — encuentra la mejor jugada';
    document.getElementById('puzzle-rating-label').textContent = `Dificultad de este puzzle: ${puzzle.rating}`;
}

async function handlePuzzleMoveAttempt(move) {
    if (!currentPuzzleId) {
        return;
    }
    // Solo un intento por puzzle (ver PuzzleController) — se guarda el id en una
    // variable local antes de vaciar currentPuzzleId, para no poder mandar un segundo
    // intento mientras la petición todavía está en vuelo.
    const puzzleId = currentPuzzleId;
    currentPuzzleId = null;

    const promotionLetter = move.promotionType ? (PROMOTION_TYPE_TO_UCI_LETTER[move.promotionType] || '') : '';
    const moveUci = move.from + move.to + promotionLetter;

    let result;
    try {
        result = await submitPuzzleAttempt(puzzleId, moveUci);
    } catch (error) {
        showTransientNotice('No se pudo enviar tu jugada — inténtalo de nuevo');
        currentPuzzleId = puzzleId; // se restaura, todavía no se ha consumido el intento de verdad
        return;
    }

    showPuzzleFeedback(result);
}

function showPuzzleFeedback(result) {
    const feedback = document.getElementById('puzzle-feedback');
    const resultEl = document.getElementById('puzzle-feedback-result');
    const solutionEl = document.getElementById('puzzle-feedback-solution');
    const ratingChangeEl = document.getElementById('puzzle-feedback-rating-change');

    resultEl.textContent = result.correct ? '¡Correcto!' : 'No era esa...';
    resultEl.className = `puzzle-feedback__result ${result.correct ? 'puzzle-feedback__result--correct' : 'puzzle-feedback__result--incorrect'}`;
    solutionEl.textContent = `La jugada correcta era: ${result.solutionUci}`;
    const changeSign = result.ratingChange >= 0 ? '+' : '';
    ratingChangeEl.textContent = `Tu rating de puzzles ahora: ${result.newRating} (${changeSign}${result.ratingChange})`;

    feedback.hidden = false;
    document.getElementById('puzzle-next-btn').hidden = false;
}