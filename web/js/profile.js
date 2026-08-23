// Perfil y ranking. Consultar (GET) es de lectura pública, igual que el historial.
// Editar (PUT) sí necesita el token — es la primera petición HTTP normal del cliente
// que lo manda, todo lo demás hasta ahora o bien no necesitaba identidad (login,
// registro) o iba por WebSocket, donde el token ya viaja en el CONNECT (ver ADR-008).

async function fetchUserProfile(userId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/users/${userId}`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar el perfil`);
    }
    return response.json();
}

async function updateUserProfile(userId, { username, country, avatarUrl }) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/users/${userId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ username, country, avatarUrl }),
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.detail || body?.message || `Error ${response.status} al guardar el perfil`);
    }
    return response.json();
}

async function changePassword(userId, currentPassword, newPassword) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/users/${userId}/password`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ currentPassword, newPassword }),
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.detail || body?.message || `Error ${response.status} al cambiar la contraseña`);
    }
}

async function deleteAccount(userId, password) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/users/${userId}`, {
        method: 'DELETE',
        headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${getStoredToken()}`,
        },
        body: JSON.stringify({ password }),
    });
    if (!response.ok) {
        const body = await response.json().catch(() => null);
        throw new Error(body?.detail || body?.message || `Error ${response.status} al borrar la cuenta`);
    }
}

async function fetchLeaderboard(mode) {
    // El ranking de puzzles no es una modalidad de partida (BULLET/BLITZ/...) — vive en
    // su propio endpoint sin parámetro, ver PuzzleController.leaderboard().
    const url = mode === 'PUZZLES'
        ? `${BACKEND_HTTP_URL}/api/puzzles/leaderboard`
        : `${BACKEND_HTTP_URL}/api/users/leaderboard?mode=${encodeURIComponent(mode)}`;
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar el ranking`);
    }
    return response.json();
}

const GAME_MODE_LABELS = { BULLET: 'Bullet', BLITZ: 'Blitz', RAPID: 'Rápidas', CLASSICAL: 'Clásicas' };
const GAME_MODE_ORDER = ['BULLET', 'BLITZ', 'RAPID', 'CLASSICAL'];

/**
 * Las 4 modalidades siempre en el mismo orden, cada una con su propio rating — usado
 * tanto en la pantalla de perfil como en la vista rápida (showProfileQuickView, en
 * main.js), así que vive aquí en vez de duplicarse en los dos sitios.
 */
function renderModeRatings(containerId, ratings) {
    const container = document.getElementById(containerId);
    container.innerHTML = '';
    const byMode = Object.fromEntries((ratings || []).map(r => [r.mode, r]));

    for (const mode of GAME_MODE_ORDER) {
        const entry = byMode[mode];
        const card = document.createElement('div');
        card.className = 'mode-rating-card';
        const value = document.createElement('span');
        value.className = 'mode-rating-card__value';
        value.textContent = entry ? entry.rating : '—';
        const label = document.createElement('span');
        label.className = 'mode-rating-card__label';
        label.textContent = GAME_MODE_LABELS[mode];
        card.append(value, label);
        container.appendChild(card);
    }
}

function renderProfile(profile) {
    const avatarEl = document.getElementById('profile-avatar');
    if (profile.avatarUrl) {
        avatarEl.src = profile.avatarUrl;
        avatarEl.alt = `Avatar de ${profile.username}`;
        avatarEl.hidden = false;
    } else {
        avatarEl.hidden = true;
    }

    document.getElementById('profile-username').textContent = profile.username;

    const countryEl = document.getElementById('profile-country');
    if (profile.country) {
        countryEl.textContent = profile.country;
        countryEl.hidden = false;
    } else {
        countryEl.hidden = true;
    }

    renderModeRatings('profile-ratings', profile.ratings);
    document.getElementById('profile-winrate').textContent = `${profile.winRatePercent}% de victorias`;
    document.getElementById('profile-games').textContent = profile.gamesPlayed;
    document.getElementById('profile-wins').textContent = profile.wins;
    document.getElementById('profile-losses').textContent = profile.losses;
    document.getElementById('profile-draws').textContent = profile.draws;

    // Único desglose por motivo que se muestra aquí — de las victorias, cuántas fueron
    // dando jaque mate. El resto de motivos (rendición, tiempo...) solo se ven partida a
    // partida en el historial, no agregados: con 8 motivos posibles, desglosarlos todos
    // aquí sería más ruido que información.
    const checkmateEl = document.getElementById('profile-checkmate-wins');
    if (profile.wins > 0) {
        checkmateEl.textContent = `${profile.winsByCheckmate} de ${profile.wins} victorias por jaque mate`;
        checkmateEl.hidden = false;
    } else {
        checkmateEl.hidden = true;
    }

    renderRecentOpponents(profile.recentOpponents);
}

/**
 * Chips clicables, cada uno abre la vista rápida de ese rival (showProfileQuickView
 * vive en main.js, que se carga después de este archivo — funciona igual: la llamada
 * solo ocurre al hacer clic, mucho después de que todos los scripts ya estén cargados).
 */
function renderRecentOpponents(recentOpponents) {
    const container = document.getElementById('recent-opponents');
    const list = document.getElementById('recent-opponents-list');
    list.innerHTML = '';

    if (!recentOpponents || recentOpponents.length === 0) {
        container.hidden = true;
        return;
    }

    for (const opponent of recentOpponents) {
        const chip = document.createElement('button');
        chip.type = 'button';
        chip.className = 'recent-opponents__chip';
        chip.textContent = opponent.username;
        chip.addEventListener('click', () => showProfileQuickView(opponent.userId));
        list.appendChild(chip);
    }
    container.hidden = false;
}

/** Rellena el formulario de edición con lo que ya se sabe del perfil, para no partir de campos vacíos. */
function fillEditProfileForm(profile) {
    document.getElementById('edit-username').value = profile.username || '';
    document.getElementById('edit-country').value = profile.country || '';
    document.getElementById('edit-avatar-url').value = profile.avatarUrl || '';
    document.getElementById('edit-profile-error').textContent = '';
}

function renderLeaderboard(entries, viewerUserId) {
    const tbody = document.getElementById('leaderboard-list');
    tbody.innerHTML = '';

    if (entries.length === 0) {
        const row = document.createElement('tr');
        row.innerHTML = '<td colspan="3">Todavía no hay nadie en el ranking.</td>';
        tbody.appendChild(row);
        return;
    }

    for (const entry of entries) {
        const row = document.createElement('tr');
        row.className = 'standings__row';
        if (entry.userId === viewerUserId) {
            row.classList.add('standings__self');
        }
        row.innerHTML = `<td>${entry.rank}</td><td>${entry.username}</td><td>${entry.rating}</td>`;
        row.addEventListener('click', () => showProfileQuickView(entry.userId));
        tbody.appendChild(row);
    }
}