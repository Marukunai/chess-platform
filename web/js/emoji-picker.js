// Selector de emoticonos — un único panel compartido entre el chat de partida y el
// chat directo, en vez de uno por cada sitio (evita duplicar la rejilla dos veces y
// mantenerla sincronizada). Puramente cliente: los emoticonos son texto Unicode normal
// y corriente, así que insertarlos en el campo de mensaje es indistinguible de haberlos
// escrito a mano — no hace falta tocar el backend en absoluto.

// Selección curada, no el teclado de emoticonos completo del sistema — con unos 50 hay
// de sobra para lo habitual en un chat, sin necesitar categorías ni buscador.
const EMOJI_PICKER_LIST = [
    '😀', '😂', '😅', '😊', '😍', '😘', '😜', '😎', '🤔', '😐', '😴', '😢',
    '😭', '😡', '🤯', '😱', '👍', '👎', '👏', '🙌', '🙏', '💪', '🤝', '✌️',
    '👋', '🤞', '❤️', '💔', '🔥', '✨', '🎉', '💯', '⭐', '😤', '🥳', '😬',
    '♟️', '♛', '♞', '♜', '♝', '♚', '🏆', '🎯', '🧠', '⏱️', '⚡', '🎮',
];

// A qué campo de texto hay que insertarle el emoticono elegido — se fija cada vez que
// se abre el panel desde un botón concreto (ver openEmojiPicker).
let emojiPickerTargetInput = null;

function buildEmojiPickerPanel() {
    const panel = document.getElementById('emoji-picker-panel');
    for (const emoji of EMOJI_PICKER_LIST) {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'emoji-picker-item';
        btn.textContent = emoji;
        btn.addEventListener('click', () => insertEmojiIntoTarget(emoji));
        panel.appendChild(btn);
    }
}

/** Inserta en la posición del cursor, no siempre al final — así se puede meter un emoticono en medio de una frase ya escrita. */
function insertEmojiIntoTarget(emoji) {
    if (!emojiPickerTargetInput) {
        return;
    }
    const input = emojiPickerTargetInput;
    const start = input.selectionStart ?? input.value.length;
    const end = input.selectionEnd ?? input.value.length;
    input.value = input.value.slice(0, start) + emoji + input.value.slice(end);
    const newCursorPosition = start + emoji.length;
    input.focus();
    input.setSelectionRange(newCursorPosition, newCursorPosition);
    closeEmojiPicker();
}

/** Se reposiciona junto al botón que lo abrió cada vez — el mismo panel sirve para el chat de partida y el chat directo. */
function openEmojiPicker(triggerButton, targetInput) {
    emojiPickerTargetInput = targetInput;
    const panel = document.getElementById('emoji-picker-panel');
    if (!panel.childElementCount) {
        buildEmojiPickerPanel(); // se construye una sola vez, la primera vez que hace falta
    }
    const rect = triggerButton.getBoundingClientRect();
    panel.style.left = `${rect.left}px`;
    panel.style.top = `${rect.bottom + 6}px`;
    panel.hidden = false;
}

function closeEmojiPicker() {
    document.getElementById('emoji-picker-panel').hidden = true;
    emojiPickerTargetInput = null;
}

function toggleEmojiPicker(triggerButton, targetInput) {
    const panel = document.getElementById('emoji-picker-panel');
    if (!panel.hidden && emojiPickerTargetInput === targetInput) {
        closeEmojiPicker();
    } else {
        openEmojiPicker(triggerButton, targetInput);
    }
}