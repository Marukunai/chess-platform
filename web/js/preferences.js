/**
 * Preferencias visuales de Chess Platform.
 *
 * Estas preferencias son deliberadamente locales:
 *
 * - No afectan al estado de una partida.
 * - No se envían al backend.
 * - No afectan al matchmaking.
 * - No afectan al rating.
 * - No afectan al motor.
 *
 * Se guardan mediante localStorage para que el usuario conserve
 * su apariencia entre sesiones.
 */

const DEFAULT_PREFERENCES = {
    boardTheme: 'walnut',
    pieceTheme: 'classic'
};


const BOARD_THEMES = {

    walnut: {
        light: '#ede6d6',
        dark: '#6b4a35'
    },

    green: {
        light: '#eeeed2',
        dark: '#769656'
    },

    blue: {
        light: '#dee3e6',
        dark: '#5d728a'
    },

    slate: {
        light: '#c5c5c5',
        dark: '#4a4a4a'
    }
};


const PIECE_THEMES = {

    classic: {
        white: '#f5f0e4',
        black: '#211b17',

        whiteOutline: '#2a2320',
        blackOutline: '#f5f0e4'
    },

    contrast: {
        white: '#ffffff',
        black: '#000000',

        whiteOutline: '#111111',
        blackOutline: '#ffffff'
    },

    sepia: {
        white: '#f1dfb6',
        black: '#4b2e1f',

        whiteOutline: '#2f2119',
        blackOutline: '#f1dfb6'
    }
};


const PREFERENCES_STORAGE_KEY =
    'chess-platform-preferences';


let currentPreferences = {
    ...DEFAULT_PREFERENCES
};


/**
 * Lee las preferencias almacenadas.
 *
 * Si localStorage contiene valores corruptos o desconocidos,
 * se recuperan los valores por defecto.
 */
function loadPreferences() {

    try {
        const stored =
            localStorage.getItem(
                PREFERENCES_STORAGE_KEY
            );

        if (!stored) {
            return {
                ...DEFAULT_PREFERENCES
            };
        }

        const parsed =
            JSON.parse(stored);

        return {
            boardTheme:
                BOARD_THEMES[
                    parsed.boardTheme
                ]
                    ? parsed.boardTheme
                    : DEFAULT_PREFERENCES.boardTheme,

            pieceTheme:
                PIECE_THEMES[
                    parsed.pieceTheme
                ]
                    ? parsed.pieceTheme
                    : DEFAULT_PREFERENCES.pieceTheme
        };

    } catch (error) {

        console.warn(
            'No se pudieron cargar las preferencias visuales.',
            error
        );

        return {
            ...DEFAULT_PREFERENCES
        };
    }
}


/**
 * Guarda las preferencias actuales.
 */
function savePreferences() {

    try {
        localStorage.setItem(
            PREFERENCES_STORAGE_KEY,
            JSON.stringify(
                currentPreferences
            )
        );

    } catch (error) {

        console.warn(
            'No se pudieron guardar las preferencias visuales.',
            error
        );
    }
}


/**
 * Aplica un tema de tablero mediante CSS variables.
 */
function applyBoardTheme(themeName) {

    const theme =
        BOARD_THEMES[themeName];

    if (!theme) {
        return;
    }

    const root =
        document.documentElement;

    root.style.setProperty(
        '--square-light',
        theme.light
    );

    root.style.setProperty(
        '--square-dark',
        theme.dark
    );
}


/**
 * Aplica un tema de piezas mediante CSS variables.
 */
function applyPieceTheme(themeName) {

    const theme =
        PIECE_THEMES[themeName];

    if (!theme) {
        return;
    }

    const root =
        document.documentElement;

    root.style.setProperty(
        '--piece-white',
        theme.white
    );

    root.style.setProperty(
        '--piece-black',
        theme.black
    );

    root.style.setProperty(
        '--piece-white-outline',
        theme.whiteOutline
    );

    root.style.setProperty(
        '--piece-black-outline',
        theme.blackOutline
    );
}


/**
 * Aplica todas las preferencias actuales.
 */
function applyPreferences() {

    applyBoardTheme(
        currentPreferences.boardTheme
    );

    applyPieceTheme(
        currentPreferences.pieceTheme
    );

    updatePreview();
}


/**
 * Actualiza la vista previa del selector.
 */
function updatePreview() {

    const preview =
        document.querySelector(
            '.preferences__preview-board'
        );

    if (!preview) {
        return;
    }

    const light =
        BOARD_THEMES[
            currentPreferences.boardTheme
        ].light;

    const dark =
        BOARD_THEMES[
            currentPreferences.boardTheme
        ].dark;

    const pieces =
        PIECE_THEMES[
        currentPreferences.pieceTheme
        ];

    const cells =
        preview.querySelectorAll('span');

    if (cells.length < 4) {
        return;
    }

    cells[0].style.background = dark;
    cells[0].style.color = pieces.white;
    cells[0].style.textShadow = `
        1px 0 0 ${pieces.whiteOutline},
        -1px 0 0 ${pieces.whiteOutline},
        0 1px 0 ${pieces.whiteOutline},
        0 -1px 0 ${pieces.whiteOutline}
    `;

    cells[1].style.background = light;
    cells[1].style.color = pieces.black;
    cells[1].style.textShadow = `
        1px 0 0 ${pieces.blackOutline},
        -1px 0 0 ${pieces.blackOutline},
        0 1px 0 ${pieces.blackOutline},
        0 -1px 0 ${pieces.blackOutline}
    `;

    cells[2].style.background = dark;
    cells[2].style.color = pieces.white;
    cells[2].style.textShadow = `
        1px 0 0 ${pieces.whiteOutline},
        -1px 0 0 ${pieces.whiteOutline},
        0 1px 0 ${pieces.whiteOutline},
        0 -1px 0 ${pieces.whiteOutline}
    `;

    cells[3].style.background = light;
    cells[3].style.color = pieces.black;
    cells[3].style.textShadow = `
        1px 0 0 ${pieces.blackOutline},
        -1px 0 0 ${pieces.blackOutline},
        0 1px 0 ${pieces.blackOutline},
        0 -1px 0 ${pieces.blackOutline}
    `;
}


/**
 * Sincroniza los <select> con las preferencias actuales.
 */
function syncPreferenceControls() {

    const boardSelect =
        document.getElementById(
            'board-theme-select'
        );

    const pieceSelect =
        document.getElementById(
            'piece-theme-select'
        );

    if (boardSelect) {
        boardSelect.value =
            currentPreferences.boardTheme;
    }

    if (pieceSelect) {
        pieceSelect.value =
            currentPreferences.pieceTheme;
    }
}


/**
 * Cambia el tema del tablero.
 */
function handleBoardThemeChange(event) {

    const themeName =
        event.target.value;

    if (!BOARD_THEMES[themeName]) {
        return;
    }

    currentPreferences.boardTheme =
        themeName;

    applyBoardTheme(themeName);
    updatePreview();
    savePreferences();
}


/**
 * Cambia el tema de las piezas.
 */
function handlePieceThemeChange(event) {

    const themeName =
        event.target.value;

    if (!PIECE_THEMES[themeName]) {
        return;
    }

    currentPreferences.pieceTheme =
        themeName;

    applyPieceTheme(themeName);
    updatePreview();
    savePreferences();
}


/**
 * Restaura los valores originales.
 */
function resetPreferences() {

    currentPreferences = {
        ...DEFAULT_PREFERENCES
    };

    syncPreferenceControls();
    applyPreferences();
    savePreferences();
}


/**
 * Abre/cierra el panel de apariencia.
 */
function togglePreferencesPanel() {

    const button =
        document.getElementById(
            'preferences-btn'
        );

    const panel =
        document.getElementById(
            'preferences-panel'
        );

    if (!button || !panel) {
        return;
    }

    const isOpen =
        !panel.hasAttribute('hidden');

    if (isOpen) {
        panel.setAttribute(
            'hidden',
            ''
        );

        button.setAttribute(
            'aria-expanded',
            'false'
        );

    } else {

        panel.removeAttribute(
            'hidden'
        );

        button.setAttribute(
            'aria-expanded',
            'true'
        );
    }
}


/**
 * Inicializa el sistema.
 */
function initializePreferences() {

    currentPreferences =
        loadPreferences();

    applyPreferences();
    syncPreferenceControls();

    const preferencesButton =
        document.getElementById(
            'preferences-btn'
        );

    const boardSelect =
        document.getElementById(
            'board-theme-select'
        );

    const pieceSelect =
        document.getElementById(
            'piece-theme-select'
        );

    const resetButton =
        document.getElementById(
            'preferences-reset-btn'
        );

    if (preferencesButton) {

        preferencesButton.setAttribute(
            'aria-expanded',
            'false'
        );

        preferencesButton.addEventListener(
            'click',
            togglePreferencesPanel
        );
    }

    if (boardSelect) {

        boardSelect.addEventListener(
            'change',
            handleBoardThemeChange
        );
    }

    if (pieceSelect) {

        pieceSelect.addEventListener(
            'change',
            handlePieceThemeChange
        );
    }

    if (resetButton) {

        resetButton.addEventListener(
            'click',
            resetPreferences
        );
    }
}


document.addEventListener(
    'DOMContentLoaded',
    initializePreferences
);