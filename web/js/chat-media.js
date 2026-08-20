// Imágenes y GIFs en el chat — sin tocar ChatMessage ni DirectMessage en absoluto. El
// diseño es deliberadamente simple: el propio texto del mensaje ES la URL de la imagen.
// Al pintar un mensaje, si su texto completo (sin nada más alrededor) es una URL que
// termina en una extensión de imagen conocida, se pinta como <img> en vez de texto
// plano — igual de válido para una imagen normal que para un GIF (elegir uno del
// buscador de Giphy solo inserta su URL como si la hubieras pegado tú mismo). Cero
// cambios de protocolo, cero campos nuevos en ningún DTO — ver ADR correspondiente.

const CHAT_IMAGE_URL_PATTERN = /^https?:\/\/\S+\.(?:jpe?g|png|gif|webp)(?:\?\S*)?$/i;

function isChatImageUrl(text) {
    return CHAT_IMAGE_URL_PATTERN.test(text.trim());
}

/**
 * Añade el CONTENIDO de un mensaje (después del "Fulanito: ") a un elemento ya creado —
 * imagen si el texto entero es una URL de imagen reconocible, texto plano si no. Un
 * enlace roto se cae a mostrar la URL como texto en vez de dejar un hueco vacío sin
 * explicación.
 */
function appendChatMessageBody(container, text) {
    if (!isChatImageUrl(text)) {
        container.append(text);
        return;
    }
    const img = document.createElement('img');
    img.src = text;
    img.alt = 'Imagen compartida en el chat';
    img.className = 'chat__image';
    img.loading = 'lazy';
    img.addEventListener('error', () => {
        img.remove();
        container.append(text);
    }, { once: true });
    container.appendChild(img);
}

/* ============================= Envío compartido (partida / chat directo) ============================= */
// Los botones de imagen y GIF viven en los dos formularios de chat, así que necesitan
// saber a cuál de los dos mandar lo que se elija — se fija cada vez que se abre un
// selector desde un botón concreto, igual que emojiPickerTargetInput en emoji-picker.js.

let chatMediaTargetContext = null; // 'game' | 'dm'

function sendGameChatText(text) {
    if (currentGameId) {
        sendChatMessage(currentGameId, text);
    }
}

async function sendDirectChatText(text) {
    if (!currentDmFriendId) {
        return;
    }
    try {
        await sendDirectMessage(currentDmFriendId, text);
        appendDirectMessageToLog(myUsername, text, false);
        hideReadReceipt();
    } catch (error) {
        showTransientNotice(error.message);
    }
}

function sendChatMediaMessage(text) {
    if (chatMediaTargetContext === 'game') {
        sendGameChatText(text);
    } else if (chatMediaTargetContext === 'dm') {
        sendDirectChatText(text);
    }
}

function positionChatMediaPopover(triggerButton, popover) {
    const rect = triggerButton.getBoundingClientRect();
    popover.style.left = `${rect.left}px`;
    popover.style.top = `${rect.bottom + 6}px`;
}

/* ============================= Pegar una URL de imagen ============================= */

function openImageUrlPopover(triggerButton, context) {
    closeGifSearchPopover(); // solo un selector abierto a la vez
    chatMediaTargetContext = context;
    const popover = document.getElementById('image-url-popover');
    document.getElementById('image-url-input').value = '';
    positionChatMediaPopover(triggerButton, popover);
    popover.hidden = false;
    document.getElementById('image-url-input').focus();
}

function closeImageUrlPopover() {
    document.getElementById('image-url-popover').hidden = true;
}

function sendImageUrlFromPopover() {
    const url = document.getElementById('image-url-input').value.trim();
    if (!url) {
        return;
    }
    sendChatMediaMessage(url);
    closeImageUrlPopover();
}

/* ============================= Buscar un GIF (proxy vía el backend, ver GifSearchController) ============================= */

let gifSearchDebounceTimer = null;

function openGifSearchPopover(triggerButton, context) {
    closeImageUrlPopover(); // solo un selector abierto a la vez
    chatMediaTargetContext = context;
    const popover = document.getElementById('gif-search-popover');
    document.getElementById('gif-search-input').value = '';
    document.getElementById('gif-search-results').innerHTML = '';
    positionChatMediaPopover(triggerButton, popover);
    popover.hidden = false;
    document.getElementById('gif-search-input').focus();
}

function closeGifSearchPopover() {
    document.getElementById('gif-search-popover').hidden = true;
    clearTimeout(gifSearchDebounceTimer);
}

function scheduleGifSearch(query) {
    clearTimeout(gifSearchDebounceTimer);
    const resultsEl = document.getElementById('gif-search-results');
    if (!query.trim()) {
        resultsEl.innerHTML = '';
        return;
    }
    // Esperar a que la persona deje de teclear en vez de buscar en cada pulsación —
    // evita machacar el proxy (y con él, la cuota de la clave de Giphy) sin necesidad.
    gifSearchDebounceTimer = setTimeout(() => performGifSearch(query.trim()), 400);
}

async function performGifSearch(query) {
    const resultsEl = document.getElementById('gif-search-results');
    try {
        const response = await fetch(`${BACKEND_HTTP_URL}/api/gifs/search?q=${encodeURIComponent(query)}`, {
            headers: { Authorization: `Bearer ${getStoredToken()}` },
        });
        if (!response.ok) {
            throw new Error(`Error ${response.status} al buscar GIFs`);
        }
        renderGifSearchResults(await response.json());
    } catch (error) {
        resultsEl.innerHTML = '';
        showTransientNotice(error.message);
    }
}

function renderGifSearchResults(results) {
    const resultsEl = document.getElementById('gif-search-results');
    resultsEl.innerHTML = '';
    if (results.length === 0) {
        resultsEl.textContent = 'Sin resultados.';
        return;
    }
    for (const gif of results) {
        const thumb = document.createElement('img');
        thumb.src = gif.previewUrl;
        thumb.alt = '';
        thumb.className = 'gif-search-result';
        thumb.addEventListener('click', () => {
            sendChatMediaMessage(gif.fullUrl);
            closeGifSearchPopover();
        });
        resultsEl.appendChild(thumb);
    }
}