// Buscar usuarios, solicitudes de amistad, lista de amigos. Todo bajo /api/friends
// necesita el token (a diferencia de /api/users/**, aquí no hay ningún GET público —
// hasta buscar depende de saber quién pregunta, para calcular friendshipStatus desde
// su punto de vista).

async function searchUsers(query) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/search?q=${encodeURIComponent(query)}`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        throw new Error(`Error ${response.status} al buscar usuarios`);
    }
    return response.json();
}

async function sendFriendRequest(targetUserId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/requests/${targetUserId}`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.detail || body?.message || `Error ${response.status} al enviar la solicitud`);
    }
}

async function fetchPendingFriendRequests() {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/requests`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar las solicitudes`);
    }
    return response.json();
}

async function respondToFriendRequest(friendshipId, accept) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/requests/${friendshipId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ accept }),
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.detail || body?.message || `Error ${response.status} al responder`);
    }
}

async function fetchFriends() {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar tus amigos`);
    }
    return response.json();
}

/** Fila compartida por los tres listados (resultados de búsqueda, solicitudes, amigos) — avatar + nombre, clicable para la vista rápida. */
function buildFriendRowInfo(userId, username, avatarUrl) {
    const info = document.createElement('div');
    info.className = 'friend-row__info';

    if (avatarUrl) {
        const avatar = document.createElement('img');
        avatar.src = avatarUrl;
        avatar.alt = '';
        avatar.className = 'friend-row__avatar';
        info.appendChild(avatar);
    }

    const name = document.createElement('span');
    name.textContent = username;
    name.className = 'friend-row__name';
    name.addEventListener('click', () => showProfileQuickView(userId));
    info.appendChild(name);

    return info;
} function renderSearchResults(results) {
    const container = document.getElementById('friend-search-results');
    container.innerHTML = '';

    if (results.length === 0) {
        container.textContent = 'Sin resultados.';
        return;
    }

    for (const result of results) {
        const row = document.createElement('div');
        row.className = 'friend-row';
        row.appendChild(buildFriendRowInfo(result.userId, result.username, result.avatarUrl));
        row.appendChild(buildSearchResultAction(result));
        container.appendChild(row);
    }
}

function buildSearchResultAction(result) {
    const action = document.createElement('div');
    action.className = 'friend-row__action';

    if (result.friendshipStatus === 'NONE') {
        const btn = document.createElement('button');
        btn.type = 'button';
        btn.className = 'btn btn--primary';
        btn.textContent = 'Añadir';
        btn.addEventListener('click', async () => {
            btn.disabled = true;
            try {
                await sendFriendRequest(result.userId);
                btn.textContent = 'Solicitud enviada';
            } catch (error) {
                btn.disabled = false;
                showTransientNotice(error.message);
            }
        });
        action.appendChild(btn);
        return action;
    }

    const labels = {
        PENDING_SENT: 'Solicitud enviada',
        PENDING_RECEIVED: 'Te ha escrito — responde abajo',
        FRIENDS: 'Ya sois amigos',
    };
    const status = document.createElement('span');
    status.className = 'friend-row__status';
    status.textContent = labels[result.friendshipStatus] || result.friendshipStatus;
    action.appendChild(status);
    return action;
}

function renderFriendRequests(requests) {
    const section = document.getElementById('friend-requests-section');
    const list = document.getElementById('friend-requests-list');
    list.innerHTML = '';

    if (requests.length === 0) {
        section.hidden = true;
        return;
    }
    section.hidden = false;

    for (const request of requests) {
        const row = document.createElement('div');
        row.className = 'friend-row';
        row.appendChild(buildFriendRowInfo(request.fromUserId, request.fromUsername, request.fromAvatarUrl));

        const actions = document.createElement('div');
        actions.className = 'friend-row__action friend-row__action--split';

        const acceptBtn = document.createElement('button');
        acceptBtn.type = 'button';
        acceptBtn.className = 'btn btn--primary';
        acceptBtn.textContent = 'Aceptar';
        acceptBtn.addEventListener('click', () => respondAndRefresh(request.friendshipId, true));

        const declineBtn = document.createElement('button');
        declineBtn.type = 'button';
        declineBtn.className = 'btn btn--ghost';
        declineBtn.textContent = 'Rechazar';
        declineBtn.addEventListener('click', () => respondAndRefresh(request.friendshipId, false));

        actions.append(acceptBtn, declineBtn);
        row.appendChild(actions);
        list.appendChild(row);
    }
}

async function respondAndRefresh(friendshipId, accept) {
    try {
        await respondToFriendRequest(friendshipId, accept);
        await refreshFriendsScreen();
    } catch (error) {
        showTransientNotice(error.message);
    }
}

function renderFriendsList(friends) {
    const list = document.getElementById('friends-list');
    list.innerHTML = '';

    if (friends.length === 0) {
        list.textContent = 'Todavía no tienes amigos añadidos.';
        return;
    }

    for (const friend of friends) {
        const row = document.createElement('div');
        row.className = 'friend-row';
        row.dataset.userId = friend.userId; // para poder actualizar el punto de estado en directo, ver handlePresenceUpdate

        const info = buildFriendRowInfo(friend.userId, friend.username, friend.avatarUrl);
        const dot = document.createElement('span');
        applyPresenceToDot(dot, friend.status);
        info.prepend(dot);
        row.appendChild(info);

        const action = document.createElement('div');
        action.className = 'friend-row__action friend-row__action--split';
        const challengeBtn = document.createElement('button');
        challengeBtn.type = 'button';
        challengeBtn.className = 'btn btn--ghost';
        challengeBtn.textContent = 'Retar';
        challengeBtn.addEventListener('click', () => openChallengeModal(friend.userId, friend.username));
        action.appendChild(challengeBtn);
        const chatBtn = document.createElement('button');
        chatBtn.type = 'button';
        chatBtn.className = 'btn btn--ghost';
        chatBtn.textContent = 'Chat';
        chatBtn.addEventListener('click', () => openDirectMessageChat(friend.userId, friend.username));
        action.appendChild(chatBtn);
        row.appendChild(action);

        list.appendChild(row);
    }
}

const PRESENCE_LABELS = {
    ONLINE: 'En línea',
    OFFLINE: 'Desconectado',
    IN_GAME: 'En partida',
    DO_NOT_DISTURB: 'No molestar',
};

function applyPresenceToDot(dotEl, status) {
    const normalized = status || 'OFFLINE';
    dotEl.className = `presence-dot presence-dot--${normalized.toLowerCase().replace(/_/g, '-')}`;
    dotEl.title = PRESENCE_LABELS[normalized] || normalized;
}

/** Actualiza solo el punto de la fila correspondiente, sin recargar toda la lista — se llama al recibir un PresenceUpdateMessage en vivo. */
function handlePresenceUpdate(update) {
    const dot = document.querySelector(`.friend-row[data-user-id="${update.userId}"] .presence-dot`);
    if (dot) {
        applyPresenceToDot(dot, update.status);
    }
}

/** Recarga solicitudes + lista de amigos a la vez — se llama al entrar a la pantalla y tras aceptar/rechazar. */
async function refreshFriendsScreen() {
    try {
        const [requests, friends] = await Promise.all([fetchPendingFriendRequests(), fetchFriends()]);
        renderFriendRequests(requests);
        renderFriendsList(friends);
    } catch (error) {
        showTransientNotice(error.message);
    }
}

/** Solo recarga si la pantalla está realmente visible — evita peticiones de sobra cuando llega un aviso en vivo y estás mirando otra cosa. */
function refreshFriendsScreenIfVisible() {
    if (!document.getElementById('friends-screen').hidden) {
        refreshFriendsScreen();
    }
}

/* ============================= Chat privado con un amigo ============================= */
// A diferencia del chat de partida (efímero, solo retransmisión), esta conversación se
// guarda de verdad en el servidor — por eso hay que pedir el historial al abrir, no
// basta con ir acumulando lo que llega mientras la pestaña está abierta.

let currentDmFriendId = null;

async function fetchDirectMessages(friendId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/${friendId}/messages`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar la conversación`);
    }
    return response.json();
}

async function sendDirectMessage(friendId, text) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/${friendId}/messages`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ text }),
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.detail || body?.message || `Error ${response.status} al enviar el mensaje`);
    }
    return response.json();
}

/**
 * Para cuando un mensaje llega en directo mientras la conversación con esa persona YA
 * está abierta — el GET normal de fetchDirectMessages() no vuelve a pedirse en ese
 * momento (el mensaje se añade a la pantalla directamente, ver
 * handleDirectMessageNotification), así que sin esto el servidor nunca se enteraría de
 * que ya lo has visto.
 */
async function markConversationAsRead(friendId) {
    try {
        await fetch(`${BACKEND_HTTP_URL}/api/friends/${friendId}/messages/read`, {
            method: 'POST',
            headers: { Authorization: `Bearer ${getStoredToken()}` },
        });
    } catch {
        // No es crítico si esto falla una vez — como mucho el contador de no leídos se
        // queda un pelín desactualizado hasta la próxima vez que se abra la conversación.
    }
}

async function openDirectMessageChat(friendId, friendUsername) {
    currentDmFriendId = friendId;
    document.getElementById('dm-chat-title').textContent = `Chat con ${friendUsername}`;
    document.getElementById('dm-chat-log').innerHTML = '';
    hideReadReceipt();
    document.getElementById('dm-chat-modal').hidden = false;

    try {
        const messages = await fetchDirectMessages(friendId);
        for (const message of messages) {
            const isFromFriend = message.senderUserId === friendId;
            appendDirectMessageToLog(isFromFriend ? friendUsername : myUsername, message.text, isFromFriend);
        }
        // El recibo de lectura solo tiene sentido para TU último mensaje enviado — si la
        // conversación termina con un mensaje del amigo, no hay nada que mostrar aquí.
        const last = messages[messages.length - 1];
        if (last && last.senderUserId !== friendId && last.read) {
            showReadReceipt();
        }
    } catch (error) {
        showTransientNotice(error.message);
    }
}

function hideDirectMessageChat() {
    currentDmFriendId = null;
    document.getElementById('dm-chat-modal').hidden = true;
    closeEmojiPicker(); // por si estaba abierto apuntando al campo de este chat, que ya se oculta
}

function showReadReceipt() {
    document.getElementById('dm-chat-read-receipt').hidden = false;
}

function hideReadReceipt() {
    document.getElementById('dm-chat-read-receipt').hidden = true;
}

/**
 * Llega a quien ENVIÓ los mensajes, no a quien los leyó — ver MessagesReadNotification
 * en el backend. Solo importa si en este momento tienes abierta la conversación
 * justo con esa persona; si no, no hay ningún recibo que actualizar en pantalla.
 */
function handleMessagesReadNotification(notification) {
    if (currentDmFriendId === notification.readByUserId && !document.getElementById('dm-chat-modal').hidden) {
        showReadReceipt();
    }
}

function appendDirectMessageToLog(senderLabel, text, isFromFriend) {
    const log = document.getElementById('dm-chat-log');
    const entry = document.createElement('p');
    entry.className = `chat__message ${isFromFriend ? '' : 'chat__message--mine'}`;
    const strong = document.createElement('strong');
    strong.textContent = `${senderLabel}: `;
    entry.appendChild(strong);
    entry.append(text);
    log.appendChild(entry);
    log.scrollTop = log.scrollHeight;
}

/**
 * Un mensaje puede llegar mientras la conversación con esa persona está abierta (se
 * añade directamente al chat) o mientras se está en cualquier otra pantalla (se avisa
 * con una notificación breve, sin interrumpir nada) — ver handleUserChannelMessage en
 * main.js, que decide cuál de los dos casos aplica antes de llamar aquí.
 */
async function handleDirectMessageNotification(notification) {
    const chatIsOpenWithSender = currentDmFriendId === notification.fromUserId
        && !document.getElementById('dm-chat-modal').hidden;
    if (chatIsOpenWithSender) {
        appendDirectMessageToLog(notification.fromUsername, notification.text, true);
        hideReadReceipt(); // el último mensaje de la conversación ya no es tuyo, así que no hay nada que mostrar como "leído" aquí
        await markConversationAsRead(notification.fromUserId); // se espera para que el refresco de abajo ya vea el contador correcto
    } else {
        showTransientNotice(`Nuevo mensaje de ${notification.fromUsername}.`);
    }

    // El desplegable general de chat también tiene que enterarse, esté abierto o no —
    // si está abierto se repinta entero, si no solo se refresca la insignia de no
    // leídos (más barato, no hace falta reconstruir toda la lista para eso).
    if (!document.getElementById('chat-dropdown-panel').hidden) {
        refreshChatDropdown();
    } else {
        refreshChatUnreadBadge();
    }
}

/* ============================= Desplegable general de chat ============================= */
// Estilo "lista de amigos" tipo LoL: un único punto de entrada para chatear con
// cualquier amigo, tengas o no una conversación ya empezada con él. Una sola llamada al
// backend (fetchConversations) da a la vez la lista de amigos, la última conversación
// de cada uno si la hay, y cuántos mensajes sin leer — el desplegable no necesita saber
// nada más para pintarse entero.

let lastFetchedConversations = [];

async function fetchConversations() {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/friends/conversations`, {
        headers: { Authorization: `Bearer ${getStoredToken()}` },
    });
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar las conversaciones`);
    }
    return response.json();
}

/** Pide la lista entera y repinta — se usa al abrir el desplegable o cuando llega un mensaje mientras está abierto. */
async function refreshChatDropdown() {
    try {
        lastFetchedConversations = await fetchConversations();
        renderChatDropdownList(lastFetchedConversations, document.getElementById('chat-dropdown-search').value);
        updateChatUnreadBadge(lastFetchedConversations);
    } catch (error) {
        showTransientNotice(error.message);
    }
}

/** Versión ligera — solo la insignia, sin repintar la lista entera. Para cuando llega un mensaje con el desplegable cerrado. */
async function refreshChatUnreadBadge() {
    try {
        lastFetchedConversations = await fetchConversations();
        updateChatUnreadBadge(lastFetchedConversations);
    } catch {
        // silencioso — la insignia no es crítica, no merece interrumpir nada si falla esta vez
    }
}

/**
 * Cuenta CONVERSACIONES con algo sin leer, no la suma de mensajes — si tienes 5
 * mensajes de la misma persona sin abrir, esto es 1, no 5. La insignia responde a "¿de
 * cuánta gente distinta tengo algo pendiente?", no a "¿cuántos mensajes tengo en total?".
 */
function updateChatUnreadBadge(conversations) {
    const unreadConversations = conversations.filter(c => c.unreadCount > 0).length;
    const badge = document.getElementById('chat-unread-badge');
    if (unreadConversations > 0) {
        badge.textContent = unreadConversations > 99 ? '99+' : String(unreadConversations);
        badge.hidden = false;
    } else {
        badge.hidden = true;
    }
}

function renderChatDropdownList(conversations, filterQuery) {
    const list = document.getElementById('chat-dropdown-list');
    list.innerHTML = '';

    const query = (filterQuery || '').trim().toLowerCase();
    const filtered = query
        ? conversations.filter(c => c.username.toLowerCase().includes(query))
        : conversations;

    if (filtered.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'chat-dropdown-empty';
        empty.textContent = query ? 'Sin resultados.' : 'Añade amigos para poder chatear con ellos.';
        list.appendChild(empty);
        return;
    }

    for (const conv of filtered) {
        list.appendChild(buildChatDropdownRow(conv));
    }
}

/**
 * Toda la fila es un único botón que abre el chat — a diferencia de las filas de
 * amigos.js, aquí NO hay un clic aparte para la vista rápida de perfil (eso ya vive en
 * la pantalla de Amigos); en un desplegable compacto, un único gesto claro ("clic =
 * abrir esta conversación") es más predecible que mezclar dos comportamientos en la
 * misma fila.
 */
function buildChatDropdownRow(conv) {
    const row = document.createElement('button');
    row.type = 'button';
    row.className = `chat-dropdown-row ${conv.unreadCount > 0 ? 'chat-dropdown-row--unread' : ''}`;
    row.addEventListener('click', () => {
        closeChatDropdown();
        openDirectMessageChat(conv.userId, conv.username);
    });

    const avatarWrap = document.createElement('span');
    avatarWrap.className = 'chat-dropdown-row__avatar-wrap';
    if (conv.avatarUrl) {
        const avatar = document.createElement('img');
        avatar.src = conv.avatarUrl;
        avatar.alt = '';
        avatar.className = 'chat-dropdown-row__avatar';
        avatarWrap.appendChild(avatar);
    } else {
        const placeholder = document.createElement('span');
        placeholder.className = 'chat-dropdown-row__avatar chat-dropdown-row__avatar--placeholder';
        placeholder.textContent = conv.username.charAt(0).toUpperCase();
        avatarWrap.appendChild(placeholder);
    }
    const dot = document.createElement('span');
    applyPresenceToDot(dot, conv.status);
    dot.classList.add('chat-dropdown-row__dot');
    avatarWrap.appendChild(dot);
    row.appendChild(avatarWrap);

    const textCol = document.createElement('span');
    textCol.className = 'chat-dropdown-row__text';
    const nameEl = document.createElement('span');
    nameEl.className = 'chat-dropdown-row__name';
    nameEl.textContent = conv.username;
    const previewEl = document.createElement('span');
    previewEl.className = 'chat-dropdown-row__preview';
    previewEl.textContent = conv.lastMessageText || 'Sin mensajes todavía';
    textCol.append(nameEl, previewEl);
    row.appendChild(textCol);

    if (conv.lastMessageAt) {
        const timeEl = document.createElement('span');
        timeEl.className = 'chat-dropdown-row__time';
        timeEl.textContent = formatRelativeTime(conv.lastMessageAt);
        row.appendChild(timeEl);
    }

    if (conv.unreadCount > 0) {
        const badge = document.createElement('span');
        badge.className = 'chat-dropdown-row__badge';
        badge.textContent = conv.unreadCount > 9 ? '9+' : String(conv.unreadCount);
        row.appendChild(badge);
    }

    return row;
}

function formatRelativeTime(isoString) {
    const diffSeconds = Math.max(0, Math.floor((Date.now() - new Date(isoString).getTime()) / 1000));

    if (diffSeconds < 60) {
        return 'ahora';
    }
    const diffMinutes = Math.floor(diffSeconds / 60);
    if (diffMinutes < 60) {
        return `hace ${diffMinutes} min`;
    }
    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
        return `hace ${diffHours} h`;
    }
    const diffDays = Math.floor(diffHours / 24);
    if (diffDays === 1) {
        return 'ayer';
    }
    if (diffDays < 7) {
        return `hace ${diffDays} días`;
    }
    return new Date(isoString).toLocaleDateString('es-ES', { day: 'numeric', month: 'short' });
}

function openChatDropdown() {
    document.getElementById('chat-dropdown-panel').hidden = false;
    document.getElementById('chat-dropdown-search').value = '';
    refreshChatDropdown();
}

function closeChatDropdown() {
    document.getElementById('chat-dropdown-panel').hidden = true;
}

function toggleChatDropdown() {
    if (document.getElementById('chat-dropdown-panel').hidden) {
        openChatDropdown();
    } else {
        closeChatDropdown();
    }
}

/* ============================= Retar a un amigo directamente ============================= */
// Mismo patrón que la revancha (propuesta pendiente, aviso por /topic/user/{userId},
// aceptar entra a la partida como un emparejamiento normal) pero iniciado desde la fila
// de un amigo en vez de desde el modal de fin de partida — y sin partida anterior de la
// que sacar los colores, así que aquí sí hace falta elegir control de tiempo primero.

let challengeModalTargetUserId = null;

function openChallengeModal(friendUserId, friendUsername) {
    challengeModalTargetUserId = friendUserId;
    document.getElementById('challenge-modal-title').textContent = `Retar a ${friendUsername}`;
    document.getElementById('challenge-modal').hidden = false;
}

function closeChallengeModal() {
    challengeModalTargetUserId = null;
    document.getElementById('challenge-modal').hidden = true;
}