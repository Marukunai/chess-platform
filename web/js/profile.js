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

async function fetchLeaderboard() {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/users/leaderboard`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar el ranking`);
    }
    return response.json();
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

    document.getElementById('profile-rating-value').textContent = profile.rating;
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
        if (entry.userId === viewerUserId) {
            row.classList.add('standings__self');
        }
        row.innerHTML = `<td>${entry.rank}</td><td>${entry.username}</td><td>${entry.rating}</td>`;
        tbody.appendChild(row);
    }
}