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
        action.className = 'friend-row__action';
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

async function openDirectMessageChat(friendId, friendUsername) {
    currentDmFriendId = friendId;
    document.getElementById('dm-chat-title').textContent = `Chat con ${friendUsername}`;
    document.getElementById('dm-chat-log').innerHTML = '';
    document.getElementById('dm-chat-modal').hidden = false;

    try {
        const messages = await fetchDirectMessages(friendId);
        for (const message of messages) {
            const isFromFriend = message.senderUserId === friendId;
            appendDirectMessageToLog(isFromFriend ? friendUsername : myUsername, message.text, isFromFriend);
        }
    } catch (error) {
        showTransientNotice(error.message);
    }
}

function hideDirectMessageChat() {
    currentDmFriendId = null;
    document.getElementById('dm-chat-modal').hidden = true;
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
function handleDirectMessageNotification(notification) {
    const chatIsOpenWithSender = currentDmFriendId === notification.fromUserId
        && !document.getElementById('dm-chat-modal').hidden;
    if (chatIsOpenWithSender) {
        appendDirectMessageToLog(notification.fromUsername, notification.text, true);
    } else {
        showTransientNotice(`Nuevo mensaje de ${notification.fromUsername}.`);
    }
}