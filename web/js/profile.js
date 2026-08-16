// Perfil y ranking — de lectura pública, igual que el historial. Reutiliza el estilo de
// la planilla (.scoresheet) para el ranking: una clasificación de torneo es el mismo
// tipo de artefacto (tabla numerada, monoespaciada), no un componente nuevo de cero.

async function fetchUserProfile(userId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/users/${userId}`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar el perfil`);
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
    document.getElementById('profile-username').textContent = profile.username;
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