// Tablero interactivo: renderiza desde FEN (llega del servidor en cada
// GameStateSyncMessage) y construye jugadas en notación algebraica para enviar por
// WebSocket. Ya no hay posición fija — todo sale de lo que manda el backend.
//
// Modelo de interacción: clic-clic y arrastrar conviven. El arrastre se detecta con
// Pointer Events (unifica ratón y toque en el mismo código, arrastrar con el dedo sale
// gratis) y, cuando el movimiento del puntero supera un umbral pequeño, toma el control
// y completa la jugada al soltar; si el movimiento fue mínimo, no hace nada y deja que
// el 'click' normal se encargue — así el flujo de clic ya existente no se toca, solo se
// extrae a funciones reutilizables (selectSquare/clearSelection/tryCompleteMove) que
// ambos caminos comparten.

// Siempre la variante "negra" (sólida) del glifo, para las dos bandas — la variante
// "blanca" de Unicode (♔♕♖♗♘♙) es solo contorno por diseño de la propia fuente, con el
// interior transparente, así que casi no responde a un color de texto. Con la sólida
// para ambas, el color real de cada bando lo decide el CSS
// (.square__piece--white/--black), no la forma del glifo.
const PIECE_GLYPH = {
    K: '♚', Q: '♛', R: '♜', B: '♝', N: '♞', P: '♟',
};

const DRAG_THRESHOLD_PX = 6;
const ARROW_COLOR = 'var(--verdigris)';
const HIGHLIGHT_COLOR = 'var(--garnet)';

let selectedSquareEl = null;
let selectedAlgebraic = null;
let currentLegalMovesUci = [];
let currentPositionRows = []; // posición ya parseada del último renderBoard() — para saber qué hay en cada casilla sin volver a parsear el FEN
let myColor = null; // 'white' | 'black', fijado al emparejar (ver main.js)
let onMoveAttempt = null; // callback inyectado por main.js cuando el jugador elige una jugada

let dragState = null; // info del arrastre en curso, o null si no hay ninguno
let justDragged = false; // evita que el 'click' nativo que sigue a un arrastre dispare una segunda jugada
let activeBoardElementId = 'board'; // qué tablero está activo — #board y #replay-board conviven en el DOM a la vez

let annotations = []; // flechas y resaltados dibujados por el usuario con el clic derecho — { type: 'arrow', from, to } | { type: 'mark', square }
let rightDragState = null; // { fromAlgebraic } mientras se arrastra con el botón derecho para dibujar una flecha

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

function pieceAtAlgebraic(algebraic) {
    const file = algebraic.charCodeAt(0) - 97;
    const rank = Number(algebraic[1]);
    const displayRow = 8 - rank;
    return currentPositionRows[displayRow]?.[file] || null;
}

/**
 * legalMovesUci determina qué se puede seleccionar/mover/arrastrar — pasa la lista real
 * solo cuando es tu turno (ver main.js), y una lista vacía cuando no lo es, así el
 * tablero queda "de solo lectura" sin necesidad de una comprobación de turno aparte
 * aquí. Lo mismo se aprovecha para el tablero de reproducción del historial (ver
 * history.js): pasando siempre [] queda de solo lectura e inerte al arrastre sin
 * necesitar ningún camino especial aquí.
 *
 * boardElementId: a qué <div> pintar — 'board' (partida en vivo) por defecto, o
 * 'replay-board' al reproducir una partida del historial. Ambos tableros nunca están
 * visibles a la vez (pantallas mutuamente excluyentes), así que compartir el estado de
 * selección global es seguro.
 */
function renderBoard(fen, legalMovesUci, boardElementId = 'board') {
    currentLegalMovesUci = legalMovesUci || [];
    currentPositionRows = parseFen(fen);
    activeBoardElementId = boardElementId;
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

            const piece = currentPositionRows[displayRow][file];
            if (piece) {
                const pieceColor = piece[0] === 'w' ? 'white' : 'black';
                const pieceEl = document.createElement('span');
                pieceEl.className = `square__piece square__piece--${pieceColor}`;
                pieceEl.textContent = PIECE_GLYPH[piece[1]];
                square.appendChild(pieceEl);
            }

            // Coordenadas: rango (1-8) en la columna 'a', archivo (a-h) en la fila 1 —
            // igual que lichess/chess.com, dentro de la propia casilla en vez de un
            // marco aparte alrededor del tablero.
            if (file === 0) {
                const rankLabel = document.createElement('span');
                rankLabel.className = 'square__coord square__coord--rank';
                rankLabel.textContent = String(8 - displayRow);
                square.appendChild(rankLabel);
            }
            if (displayRow === 7) {
                const fileLabel = document.createElement('span');
                fileLabel.className = 'square__coord square__coord--file';
                fileLabel.textContent = String.fromCharCode(97 + file);
                square.appendChild(fileLabel);
            }

            square.addEventListener('click', () => handleSquareClick(algebraic, square));
            attachDragHandlers(square, algebraic);
            attachAnnotationHandlers(square, algebraic);
            boardEl.appendChild(square);
        }
    }

    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('class', 'annotations-overlay');
    svg.setAttribute('viewBox', '0 0 800 800');
    svg.setAttribute('preserveAspectRatio', 'none');
    boardEl.appendChild(svg);

    annotations = []; // posición nueva — las flechas de la jugada anterior ya no pintan nada
}

function handleSquareClick(algebraic, squareEl) {
    if (justDragged) {
        justDragged = false; // ese 'click' es el eco de un arrastre que ya se resolvió
        return;
    }

    clearAnnotations(); // cualquier clic izquierdo borra las flechas/marcas dibujadas, como en chess.com

    if (!selectedAlgebraic) {
        if (!hasLegalMoveFrom(algebraic)) {
            return;
        }
        selectSquare(algebraic, squareEl);
        return;
    }

    if (algebraic === selectedAlgebraic) {
        clearSelection();
        return;
    }

    const from = selectedAlgebraic;
    const isLegalDestination = currentLegalMovesUci.some(uci => uci.startsWith(from + algebraic));
    clearSelection();

    if (isLegalDestination) {
        tryCompleteMove(from, algebraic);
    } else if (hasLegalMoveFrom(algebraic)) {
        // No era un destino legal, pero la casilla clicada tiene piezas propias con
        // jugadas — cambia la selección a ella en vez de dejar todo deseleccionado.
        selectSquare(algebraic, squareEl);
    }
}

function hasLegalMoveFrom(algebraic) {
    return currentLegalMovesUci.some(uci => uci.startsWith(algebraic));
}

function selectSquare(algebraic, squareEl) {
    selectedAlgebraic = algebraic;
    selectedSquareEl = squareEl;
    squareEl.classList.add('square--selected');
    showLegalDestinations(algebraic);
}

function clearSelection() {
    if (selectedSquareEl) {
        selectedSquareEl.classList.remove('square--selected');
    }
    selectedSquareEl = null;
    selectedAlgebraic = null;
    clearLegalDestinations();
}

/** Punto en cada destino legal desde `fromAlgebraic`: relleno si está vacío, anillo si es una captura. */
function showLegalDestinations(fromAlgebraic) {
    const destinations = currentLegalMovesUci
        .filter(uci => uci.startsWith(fromAlgebraic))
        .map(uci => uci.substring(2, 4));

    for (const dest of destinations) {
        const squareEl = document.querySelector(`#${activeBoardElementId} [data-square="${dest}"]`);
        if (!squareEl) {
            continue;
        }
        const isCapture = pieceAtAlgebraic(dest) !== null;
        squareEl.classList.add(isCapture ? 'square--dest-capture' : 'square--dest-empty');
    }
}

function clearLegalDestinations() {
    document.querySelectorAll(`#${activeBoardElementId} .square--dest-empty, #${activeBoardElementId} .square--dest-capture`)
        .forEach((el) => el.classList.remove('square--dest-empty', 'square--dest-capture'));
}

/** Si from->to necesita coronar, pregunta qué pieza antes de avisar a main.js. */
function tryCompleteMove(from, to) {
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

function askPromotionChoice() {
    const choice = prompt('¿A qué pieza coronas? (Q = dama, R = torre, B = alfil, N = caballo)', 'Q');
    if (!choice) {
        return null;
    }
    const map = { Q: 'QUEEN', R: 'ROOK', B: 'BISHOP', N: 'KNIGHT' };
    return map[choice.trim().toUpperCase()] || 'QUEEN';
}

/* ============================= Arrastrar y soltar ============================= */

function attachDragHandlers(squareEl, algebraic) {
    squareEl.addEventListener('pointerdown', (event) => {
        if (event.button !== 0) {
            return; // solo botón/toque principal — el derecho es para las flechas (aparte)
        }
        if (!hasLegalMoveFrom(algebraic)) {
            return; // nada que arrastrar desde una casilla sin jugadas propias
        }
        const pieceEl = squareEl.querySelector('.square__piece');
        dragState = {
            fromAlgebraic: algebraic,
            originSquareEl: squareEl,
            pointerId: event.pointerId,
            startX: event.clientX,
            startY: event.clientY,
            isDragging: false,
            ghostEl: null,
            glyph: pieceEl ? pieceEl.textContent : '',
            pieceColorClass: pieceEl ? [...pieceEl.classList].find((c) => c.startsWith('square__piece--')) : '',
        };
    });
}

// Adjuntados una sola vez al documento entero (no por casilla): el arrastre puede sacar
// el puntero fuera del tablero, y solo hace falta un listener para seguirlo pase lo que
// pase.
document.addEventListener('pointermove', (event) => {
    if (!dragState || event.pointerId !== dragState.pointerId) {
        return;
    }

    if (!dragState.isDragging) {
        const moved = Math.hypot(event.clientX - dragState.startX, event.clientY - dragState.startY);
        if (moved < DRAG_THRESHOLD_PX) {
            return; // todavía no cuenta como arrastre — podría acabar siendo un clic normal
        }
        beginDragging();
    }

    positionGhost(event.clientX, event.clientY, event.pointerType);
    updateDragHoverSquare(event.clientX, event.clientY);
});

document.addEventListener('pointerup', (event) => {
    if (!dragState || event.pointerId !== dragState.pointerId) {
        return;
    }

    const { fromAlgebraic, isDragging } = dragState;

    if (isDragging) {
        const dropEl = document.elementFromPoint(event.clientX, event.clientY)?.closest('[data-square]');
        const toAlgebraic = dropEl ? dropEl.dataset.square : null;
        const isLegalDestination = toAlgebraic
            && currentLegalMovesUci.some(uci => uci.startsWith(fromAlgebraic + toAlgebraic));

        endDragging();
        clearSelection();
        justDragged = true; // el 'click' que viene detrás de este gesto no debe hacer nada

        if (isLegalDestination) {
            tryCompleteMove(fromAlgebraic, toAlgebraic);
        }
    }

    dragState = null;
});

document.addEventListener('pointercancel', () => {
    if (dragState?.isDragging) {
        endDragging();
        clearSelection();
    }
    dragState = null;
});

function beginDragging() {
    dragState.isDragging = true;
    clearSelection();
    clearAnnotations();
    selectSquare(dragState.fromAlgebraic, dragState.originSquareEl);
    dragState.originSquareEl.classList.add('square--drag-source');

    const ghost = document.createElement('div');
    ghost.className = `drag-ghost ${dragState.pieceColorClass}`;
    ghost.textContent = dragState.glyph;
    document.body.appendChild(ghost);
    dragState.ghostEl = ghost;
}

function positionGhost(x, y, pointerType) {
    if (!dragState.ghostEl) {
        return;
    }
    // En pantallas táctiles, la pieza se sube un poco por encima del dedo — si no, el
    // propio dedo tapa justo la casilla donde se va a soltar.
    const yOffset = pointerType === 'touch' ? -60 : 0;
    dragState.ghostEl.style.left = `${x}px`;
    dragState.ghostEl.style.top = `${y + yOffset}px`;
}

function updateDragHoverSquare(x, y) {
    document.querySelectorAll('.square--drag-hover').forEach((el) => el.classList.remove('square--drag-hover'));
    const el = document.elementFromPoint(x, y)?.closest('[data-square]');
    if (el) {
        el.classList.add('square--drag-hover');
    }
}

function endDragging() {
    dragState.ghostEl?.remove();
    dragState.originSquareEl?.classList.remove('square--drag-source');
    document.querySelectorAll('.square--drag-hover').forEach((el) => el.classList.remove('square--drag-hover'));
}

/* ============================= Flechas y marcas (clic derecho) ============================= */

/**
 * Igual que chess.com/lichess: arrastrar con el botón derecho de una casilla a otra
 * dibuja una flecha; clic derecho sin arrastrar en una sola casilla la resalta en rojo.
 * Repetir exactamente la misma flecha/marca la borra (alternar). Es puramente
 * cosmético — nunca se manda al servidor, solo vive en el navegador de quien la dibuja.
 * Funciona igual en el tablero en vivo que en la reproducción, porque ambos comparten
 * este mismo attachAnnotationHandlers() vía renderBoard().
 */
function attachAnnotationHandlers(squareEl, algebraic) {
    squareEl.addEventListener('contextmenu', (event) => event.preventDefault());

    squareEl.addEventListener('mousedown', (event) => {
        if (event.button === 2) {
            rightDragState = { fromAlgebraic: algebraic };
        }
    });

    squareEl.addEventListener('mouseup', (event) => {
        if (event.button !== 2 || !rightDragState) {
            return;
        }
        const from = rightDragState.fromAlgebraic;
        rightDragState = null;

        if (from === algebraic) {
            toggleAnnotation({ type: 'mark', square: from });
        } else {
            toggleAnnotation({ type: 'arrow', from, to: algebraic });
        }
    });
}

function toggleAnnotation(annotation) {
    const index = annotations.findIndex((a) => sameAnnotation(a, annotation));
    if (index >= 0) {
        annotations.splice(index, 1);
    } else {
        annotations.push(annotation);
    }
    renderAnnotations();
}

function sameAnnotation(a, b) {
    if (a.type !== b.type) {
        return false;
    }
    return a.type === 'mark' ? a.square === b.square : a.from === b.from && a.to === b.to;
}

function clearAnnotations() {
    if (annotations.length === 0) {
        return;
    }
    annotations = [];
    renderAnnotations();
}

/** Centro de una casilla en el sistema de coordenadas 0-800 del <svg> (100 unidades por casilla). */
function svgCenterOf(algebraic) {
    const file = algebraic.charCodeAt(0) - 97;
    const rank = Number(algebraic[1]);
    const displayRow = 8 - rank;
    return { x: file * 100 + 50, y: displayRow * 100 + 50 };
}

function renderAnnotations() {
    const svg = document.querySelector(`#${activeBoardElementId} .annotations-overlay`);
    if (!svg) {
        return;
    }
    // markerUnits="userSpaceOnUse" a propósito: por defecto SVG escala el <marker> según
    // el stroke-width de la línea a la que se engancha, así que con un trazo grueso
    // (14 unidades) la punta salía enorme (7 * 14 = 98, casi una casilla entera). Con
    // userSpaceOnUse, el tamaño de la punta se controla aparte, en las mismas unidades
    // que el resto del tablero (100 unidades = 1 casilla).
    svg.innerHTML = `<defs>
        <marker id="arrow-tip-${activeBoardElementId}" markerUnits="userSpaceOnUse"
                markerWidth="34" markerHeight="30" refX="30" refY="15" orient="auto">
            <path d="M0,2 L30,15 L0,28 Z" fill="${ARROW_COLOR}" />
        </marker>
    </defs>`;

    for (const annotation of annotations) {
        if (annotation.type === 'mark') {
            svg.appendChild(buildMarkRect(annotation.square));
        } else {
            svg.appendChild(buildArrowLine(annotation.from, annotation.to));
        }
    }
}

function buildMarkRect(square) {
    const { x, y } = svgCenterOf(square);
    const rect = document.createElementNS('http://www.w3.org/2000/svg', 'rect');
    rect.setAttribute('x', x - 50);
    rect.setAttribute('y', y - 50);
    rect.setAttribute('width', 100);
    rect.setAttribute('height', 100);
    rect.setAttribute('fill', HIGHLIGHT_COLOR);
    rect.setAttribute('opacity', 0.55);
    return rect;
}

function buildArrowLine(from, to) {
    const start = svgCenterOf(from);
    const end = svgCenterOf(to);

    // La punta se dibuja encima del final de la línea (después, en el orden de SVG), así
    // que tapa sola el extremo redondeado del trazo — sin necesidad de acortar la línea
    // a mano para que no asome por debajo.
    const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    line.setAttribute('x1', start.x);
    line.setAttribute('y1', start.y);
    line.setAttribute('x2', end.x);
    line.setAttribute('y2', end.y);
    line.setAttribute('stroke', ARROW_COLOR);
    line.setAttribute('stroke-width', 11);
    line.setAttribute('stroke-linecap', 'round');
    line.setAttribute('opacity', 0.85);
    line.setAttribute('marker-end', `url(#arrow-tip-${activeBoardElementId})`);
    return line;
}

/* ============================= Planilla (sin cambios) ============================= */

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