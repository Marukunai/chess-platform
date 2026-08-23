/**
 * Puzzles — a diferencia del resto de la plataforma, esto habla con el backend por
 * REST normal, no por STOMP (ver PuzzleController): no hace falta tiempo real, nadie
 * más está resolviendo el mismo puzzle a la vez.
 *
 * Reutiliza renderBoard() de board.js tal cual, pintando en #puzzle-board (un tercer
 * tablero que convive con #board y #replay-board, cada uno con su propio elemento) —
 * y reutiliza también myColor/onMoveAttempt, las mismas variables globales que ya usa
 * la partida en vivo, en vez de inventar un mecanismo de clic/arrastre aparte.
 *
 * Los puzzles pueden ser de una jugada o de varias (ver PuzzleGenerationService en el
 * backend) — el envío es siempre paso a paso: currentStepIndex lleva la cuenta de en
 * qué paso vamos, y solo se cierra el intento (done=true en la respuesta) al fallar
 * algún paso o al acertar el último. Un puzzle de una sola jugada es, simplemente, uno
 * donde el primer paso ya es el último — mismo código para los dos casos.
 */

let currentPuzzleId = null;
let currentPuzzleOrientation = 'white'; // se conserva durante todo el intento, para que el tablero no gire entre pasos ni al enseñar la solución
let currentStepIndex = 0;
let hintUsedThisAttempt = false; // acumulativo — una vez pedida una pista, se manda true en el resto de pasos de este mismo intento
let reviewPositions = []; // la línea de solución completa, jugada a jugada — solo se rellena al cerrar el intento, para poder navegarla con las flechas
let reviewSolutionNotation = []; // notación legible ("Nf3+") de cada jugada de la línea, para mostrar junto al contador al navegar
let reviewIndex = 0;
let currentPuzzlePreviousFen = null; // guardado aparte de reviewPositions porque se conoce ANTES de cerrar el intento (viene con el propio puzzle), pero no se usa hasta entonces
let currentPuzzlePreviousMoveUci = null;

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

async function submitPuzzleAttempt(puzzleId, moveUci, stepIndex, hintUsed) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/puzzles/${puzzleId}/attempt`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ moveUci, stepIndex, hintUsed }),
    });
    if (!response.ok) {
        throw new Error('No se pudo enviar el intento');
    }
    return response.json();
}

async function fetchPuzzleHint(puzzleId, stepIndex) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/puzzles/${puzzleId}/hint?stepIndex=${stepIndex}`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        throw new Error('No se pudo pedir la pista');
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
    document.getElementById('puzzle-hint-btn').hidden = true;
    document.getElementById('puzzle-hint-text').hidden = true;
    document.getElementById('puzzle-review-nav').hidden = true;
    document.getElementById('puzzle-turn-label').textContent = 'Buscando el siguiente puzzle...';
    document.getElementById('puzzle-rating-label').textContent = '';
    currentPuzzleId = null;
    currentStepIndex = 0;
    hintUsedThisAttempt = false;
    reviewPositions = [];
    reviewIndex = 0;

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
        // Limpiar el tablero, no dejar la posición del último puzzle intentado
        // puesta ahí con pinta de seguir pendiente — confunde, parece que todavía
        // queda algo por resolver cuando no es así.
        renderBoard('8/8/8/8/8/8/8/8 w - - 0 1', [], 'puzzle-board');
        return;
    }

    currentPuzzleId = puzzle.puzzleId;
    currentPuzzleOrientation = puzzle.sideToMove;
    currentPuzzlePreviousFen = puzzle.previousFen;
    currentPuzzlePreviousMoveUci = puzzle.previousMoveUci;
    myColor = puzzle.sideToMove; // para que board.js sepa de quién son las piezas que se pueden mover

    document.getElementById('puzzle-turn-label').textContent =
        puzzle.sideToMove === 'white' ? 'Juegan blancas — encuentra la mejor jugada' : 'Juegan negras — encuentra la mejor jugada';
    document.getElementById('puzzle-rating-label').textContent = `Dificultad de este puzzle: ${puzzle.rating}`;
    document.getElementById('puzzle-hint-btn').hidden = false;

    if (puzzle.previousFen && puzzle.previousMoveUci) {
        // Se enseña primero la posición de ANTES del error que originó el puzzle, y un
        // instante después la posición real del puzzle con esa jugada ya resaltada —
        // así se ve el error ejecutarse, no solo el resultado ya puesto de golpe.
        renderBoard(puzzle.previousFen, [], 'puzzle-board', null, null, puzzle.sideToMove);
        setTimeout(() => {
            renderBoard(puzzle.fen, puzzle.legalMovesUci, 'puzzle-board', null,
                { to: puzzle.previousMoveUci.slice(2, 4), wasCapture: false }, puzzle.sideToMove);
        }, 700);
    } else {
        renderBoard(puzzle.fen, puzzle.legalMovesUci, 'puzzle-board', null, null, puzzle.sideToMove);
    }
}

async function handlePuzzleMoveAttempt(move) {
    if (!currentPuzzleId) {
        return;
    }
    const puzzleId = currentPuzzleId;
    const stepIndex = currentStepIndex;
    // Se vacía mientras la petición está en vuelo, para no poder mandar un segundo
    // intento por encima del primero — se restaura si la petición falla de verdad
    // (no si el paso simplemente resulta incorrecto, eso sí cierra el intento).
    currentPuzzleId = null;

    const promotionLetter = move.promotionType ? (PROMOTION_TYPE_TO_UCI_LETTER[move.promotionType] || '') : '';
    const moveUci = move.from + move.to + promotionLetter;

    let result;
    try {
        result = await submitPuzzleAttempt(puzzleId, moveUci, stepIndex, hintUsedThisAttempt);
    } catch (error) {
        showTransientNotice('No se pudo enviar tu jugada — inténtalo de nuevo');
        currentPuzzleId = puzzleId;
        return;
    }

    if (!result.done) {
        // Correcto, pero quedan más pasos — se anima la respuesta forzada del rival y
        // se deja el siguiente paso listo, con sus propias jugadas legales (el cliente
        // no tiene motor de reglas propio, ver ADR-011, así que sin esto no podría
        // saber qué es legal a partir de aquí).
        currentPuzzleId = puzzleId;
        currentStepIndex = stepIndex + 1;
        document.getElementById('puzzle-hint-text').hidden = true;
        showTransientNotice('¡Bien! Sigue...');
        renderBoard(result.resultingFen, result.legalMovesUci, 'puzzle-board', null,
            { to: result.opponentReplyUci.slice(2, 4), wasCapture: false }, currentPuzzleOrientation);
        return;
    }

    showPuzzleFeedback(result);
}

async function requestPuzzleHint() {
    if (!currentPuzzleId) {
        return;
    }
    try {
        const hint = await fetchPuzzleHint(currentPuzzleId, currentStepIndex);
        hintUsedThisAttempt = true;
        const hintTextEl = document.getElementById('puzzle-hint-text');
        hintTextEl.textContent = `Pista: mueve la pieza que está en ${hint.originSquare}`;
        hintTextEl.hidden = false;
    } catch (error) {
        showTransientNotice('No se pudo pedir la pista — inténtalo de nuevo');
    }
}

function showPuzzleFeedback(result) {
    const feedback = document.getElementById('puzzle-feedback');
    const resultEl = document.getElementById('puzzle-feedback-result');
    const solutionEl = document.getElementById('puzzle-feedback-solution');
    const ratingChangeEl = document.getElementById('puzzle-feedback-rating-change');

    resultEl.textContent = result.correct ? '¡Correcto!' : 'No era esa...';
    resultEl.className = `puzzle-feedback__result ${result.correct ? 'puzzle-feedback__result--correct' : 'puzzle-feedback__result--incorrect'}`;
    solutionEl.textContent = `La línea correcta era: ${result.solutionNotation.join(' ')}`;
    const changeSign = result.ratingChange >= 0 ? '+' : '';
    ratingChangeEl.textContent = `Tu rating de puzzles ahora: ${result.newRating} (${changeSign}${result.ratingChange})`;

    feedback.hidden = false;
    document.getElementById('puzzle-next-btn').hidden = false;
    document.getElementById('puzzle-hint-btn').hidden = true;
    document.getElementById('puzzle-hint-text').hidden = true;

    // La línea de solución completa, jugada a jugada — con la posición de antes del
    // error al principio si la hay, para poder repasar la historia entera con las
    // flechas anterior/siguiente, no solo ver el resultado final puesto de golpe.
    reviewPositions = currentPuzzlePreviousFen
        ? [currentPuzzlePreviousFen, ...result.solutionFenSequence]
        : result.solutionFenSequence;
    reviewSolutionNotation = result.solutionNotation;
    reviewIndex = reviewPositions.length - 1; // se empieza mostrando la posición final, que es la que ya está en pantalla
    document.getElementById('puzzle-review-nav').hidden = false;
    renderReviewPosition();
}

function renderReviewPosition() {
    renderBoard(reviewPositions[reviewIndex], [], 'puzzle-board', null, null, currentPuzzleOrientation);
    // Cuántas posiciones "extra" hay antes de la primera jugada de la línea de
    // solución en sí (la de antes del error, si la hay, más la del propio puzzle) —
    // para saber a qué jugada de reviewSolutionNotation corresponde cada índice.
    const offset = currentPuzzlePreviousFen ? 2 : 1;
    const notationIndex = reviewIndex - offset;
    let label = `Posición ${reviewIndex + 1} / ${reviewPositions.length}`;
    if (notationIndex >= 0 && reviewSolutionNotation[notationIndex]) {
        label += ` — ${reviewSolutionNotation[notationIndex]}`;
    }
    document.getElementById('puzzle-review-counter').textContent = label;
}

function reviewGoToPrevious() {
    if (reviewIndex > 0) {
        reviewIndex--;
        renderReviewPosition();
    }
}

function reviewGoToNext() {
    if (reviewIndex < reviewPositions.length - 1) {
        reviewIndex++;
        renderReviewPosition();
    }
}