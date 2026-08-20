// Apariencia del tablero — puramente cosmético y por-espectador: se guarda en
// localStorage, nunca se manda al servidor ni se sincroniza con nadie. Tu rival ve el
// tablero con SU propia configuración, tú con la tuya, cada uno independiente. Por eso
// vive enteramente aquí, sin tocar el backend en absoluto.

const BOARD_THEMES = [
    {
        id: 'walnut', label: 'Nogal', light: '#ede6d6', dark: '#6b4a35',
        coordOnLight: 'rgba(107, 74, 53, 0.65)', coordOnDark: 'rgba(237, 230, 214, 0.55)'
    },
    {
        id: 'emerald', label: 'Esmeralda', light: '#eef2e6', dark: '#4b6b3a',
        coordOnLight: 'rgba(75, 107, 58, 0.65)', coordOnDark: 'rgba(238, 242, 230, 0.6)'
    },
    {
        id: 'ocean', label: 'Océano', light: '#e7eef5', dark: '#3a5a78',
        coordOnLight: 'rgba(58, 90, 120, 0.65)', coordOnDark: 'rgba(231, 238, 245, 0.6)'
    },
    {
        id: 'slate', label: 'Pizarra', light: '#e8e8e8', dark: '#5c5f66',
        coordOnLight: 'rgba(92, 95, 102, 0.65)', coordOnDark: 'rgba(232, 232, 232, 0.6)'
    },
    {
        id: 'coral', label: 'Coral', light: '#fbe9e1', dark: '#b56354',
        coordOnLight: 'rgba(181, 99, 84, 0.65)', coordOnDark: 'rgba(251, 233, 225, 0.6)'
    },
    {
        id: 'midnight', label: 'Medianoche', light: '#d8d4e8', dark: '#332a5c',
        coordOnLight: 'rgba(51, 42, 92, 0.65)', coordOnDark: 'rgba(216, 212, 232, 0.6)'
    },
];

// "Alto contraste" no tiene datos de color aquí a propósito — applyPieceStyle() le
// quita las variables en vez de ponérselas, y con eso el CSS ya cae solo al valor por
// defecto de siempre (ver var(--piece-white-fill, var(--parchment)) en style.css).
const PIECE_STYLES = [
    { id: 'classic', label: 'Clásico' },
    {
        id: 'contrast', label: 'Alto contraste', whiteFill: '#ffffff', whiteStroke: '#000000',
        blackFill: '#000000', blackStroke: '#ffffff'
    },
];

const BOARD_PREFS_STORAGE_KEY = 'chess-platform-board-prefs';

function getBoardPreferences() {
    try {
        const raw = localStorage.getItem(BOARD_PREFS_STORAGE_KEY);
        const parsed = raw ? JSON.parse(raw) : {};
        return {
            boardTheme: BOARD_THEMES.some(t => t.id === parsed.boardTheme) ? parsed.boardTheme : 'walnut',
            pieceStyle: PIECE_STYLES.some(s => s.id === parsed.pieceStyle) ? parsed.pieceStyle : 'classic',
        };
    } catch {
        return { boardTheme: 'walnut', pieceStyle: 'classic' }; // localStorage corrupto o inaccesible — mejor los valores de siempre que romper el arranque
    }
}

function saveBoardPreferences(prefs) {
    localStorage.setItem(BOARD_PREFS_STORAGE_KEY, JSON.stringify(prefs));
}

function applyBoardTheme(themeId) {
    const theme = BOARD_THEMES.find(t => t.id === themeId) || BOARD_THEMES[0];
    const root = document.documentElement.style;
    root.setProperty('--square-light', theme.light);
    root.setProperty('--square-dark', theme.dark);
    root.setProperty('--square-coord-light', theme.coordOnLight);
    root.setProperty('--square-coord-dark', theme.coordOnDark);
}

function applyPieceStyle(styleId) {
    const style = PIECE_STYLES.find(s => s.id === styleId) || PIECE_STYLES[0];
    const root = document.documentElement.style;
    if (style.whiteFill) {
        root.setProperty('--piece-white-fill', style.whiteFill);
        root.setProperty('--piece-white-stroke', style.whiteStroke);
        root.setProperty('--piece-black-fill', style.blackFill);
        root.setProperty('--piece-black-stroke', style.blackStroke);
    } else {
        // "Clásico" — quitar la variable, no ponerla a un valor fijo, para que el CSS
        // vuelva a caer solo en su valor por defecto (ver var(--piece-white-fill, ...)).
        root.removeProperty('--piece-white-fill');
        root.removeProperty('--piece-white-stroke');
        root.removeProperty('--piece-black-fill');
        root.removeProperty('--piece-black-stroke');
    }
}

/** Se llama una vez al arrancar (ver main.js) — aplica lo guardado antes de que se pinte ningún tablero. */
function applyStoredBoardPreferences() {
    const prefs = getBoardPreferences();
    applyBoardTheme(prefs.boardTheme);
    applyPieceStyle(prefs.pieceStyle);
}

/** Una muestra en miniatura (2x2 casillas) con los colores reales del tema — no un cuadrado de color plano, para que se note que es un tablero. */
function buildThemeSwatchPreview(theme) {
    const preview = document.createElement('div');
    preview.className = 'theme-swatch__preview';
    for (let i = 0; i < 4; i++) {
        const cell = document.createElement('div');
        const isLight = i === 0 || i === 3;
        cell.style.background = isLight ? theme.light : theme.dark;
        preview.appendChild(cell);
    }
    return preview;
}

function renderBoardThemeOptions(selectedId, onSelect) {
    const container = document.getElementById('board-theme-options');
    container.innerHTML = '';
    for (const theme of BOARD_THEMES) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `theme-swatch ${theme.id === selectedId ? 'theme-swatch--selected' : ''}`;
        btn.appendChild(buildThemeSwatchPreview(theme));
        const label = document.createElement('span');
        label.textContent = theme.label;
        btn.appendChild(label);
        btn.addEventListener('click', () => onSelect(theme.id));
        container.appendChild(btn);
    }
}

function renderPieceStyleOptions(selectedId, onSelect) {
    const container = document.getElementById('piece-style-options');
    container.innerHTML = '';
    for (const style of PIECE_STYLES) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = `theme-swatch theme-swatch--piece ${style.id === selectedId ? 'theme-swatch--selected' : ''}`;

        const preview = document.createElement('span');
        preview.className = 'theme-swatch__piece-preview';
        // Un peón de cada color, con el estilo real que tendría — mismas clases que usa
        // el tablero de verdad, así la muestra no se puede desincronizar del resultado real.
        const whitePiece = document.createElement('span');
        whitePiece.className = 'square__piece square__piece--white';
        whitePiece.textContent = '♟';
        const blackPiece = document.createElement('span');
        blackPiece.className = 'square__piece square__piece--black';
        blackPiece.textContent = '♟';
        if (style.whiteFill) {
            whitePiece.style.color = style.whiteFill;
            whitePiece.style.webkitTextStroke = `1.5px ${style.whiteStroke}`;
            blackPiece.style.color = style.blackFill;
            blackPiece.style.webkitTextStroke = `1.5px ${style.blackStroke}`;
        }
        preview.append(whitePiece, blackPiece);
        btn.appendChild(preview);

        const label = document.createElement('span');
        label.textContent = style.label;
        btn.appendChild(label);
        btn.addEventListener('click', () => onSelect(style.id));
        container.appendChild(btn);
    }
}