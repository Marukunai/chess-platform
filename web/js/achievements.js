// Logros — de lectura pública, igual que el perfil: ver los tuyos o los de cualquier
// otro usa el mismo endpoint, GET /api/achievements/{userId}. El progreso viene ya
// calculado del backend (ver AchievementService) — aquí solo se pinta.

const ACHIEVEMENT_CATEGORY_LABELS = {
    GENERAL: 'General',
    VICTORIAS: 'Victorias',
    RATING: 'Rating',
    MODALIDADES: 'Modalidades',
    SOCIAL: 'Social',
    PERFIL: 'Perfil',
};

async function fetchAchievements(userId) {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/achievements/${userId}`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar los logros`);
    }
    return response.json();
}

async function fetchAchievementsLeaderboard() {
    const response = await fetch(`${BACKEND_HTTP_URL}/api/achievements/leaderboard`);
    if (!response.ok) {
        throw new Error(`Error ${response.status} al cargar el ranking de logros`);
    }
    return response.json();
}

/**
 * Punto único de entrada a la pantalla de logros — sirve tanto para "los míos" (desde
 * el lobby, username=null) como para los de cualquier otro (desde el ranking global,
 * pasando su nombre). El propio backend no distingue quién pregunta: /api/achievements/{userId}
 * es público, así que no hace falta ningún camino especial para "ver los de otro".
 */
async function openAchievementsScreen(userId, username) {
    document.getElementById('achievements-title').textContent = username ? `Logros de ${username}` : 'Mis logros';
    try {
        renderAchievements(await fetchAchievements(userId));
        showScreen('achievements-screen');
    } catch (error) {
        showTransientNotice(error.message);
    }
}

function renderAchievements(achievements) {
    const unlockedCount = achievements.filter(a => a.unlocked).length;
    const total = achievements.length;
    const percent = total > 0 ? Math.round((unlockedCount / total) * 100) : 0;

    document.getElementById('achievements-summary').textContent =
        `${unlockedCount} de ${total} logros desbloqueados (${percent}%)`;
    document.getElementById('achievements-overall-fill').style.width = `${percent}%`;

    // Agrupados por categoría, conservando el orden en que ya vienen del backend (el
    // catálogo tiene un orden fijo, ver AchievementCatalog) — no hace falta reordenar
    // nada aquí, solo separar visualmente por categoría según van llegando.
    const container = document.getElementById('achievements-list');
    container.innerHTML = '';
    let lastCategory = null;
    for (const achievement of achievements) {
        if (achievement.category !== lastCategory) {
            const heading = document.createElement('p');
            heading.className = 'achievements-category-label';
            heading.textContent = ACHIEVEMENT_CATEGORY_LABELS[achievement.category] || achievement.category;
            container.appendChild(heading);
            lastCategory = achievement.category;
        }
        container.appendChild(buildAchievementRow(achievement));
    }
}

function buildAchievementRow(achievement) {
    const progressPercent = achievement.target > 0
        ? Math.min(100, Math.round((achievement.currentProgress / achievement.target) * 100))
        : 0;
    // "Casi conseguido" — al 80% o más pero todavía sin desbloquear, para dar la
    // sensación de "estás a punto" sin necesitar tocar el backend para esto: es puro
    // cálculo a partir de datos que ya llegan.
    const almostThere = !achievement.unlocked && progressPercent >= 80;

    const row = document.createElement('div');
    row.className = ['achievement-row',
        achievement.unlocked ? 'achievement-row--unlocked' : '',
        almostThere ? 'achievement-row--almost' : ''].filter(Boolean).join(' ');

    const icon = document.createElement('span');
    icon.className = 'achievement-row__icon';
    icon.textContent = achievement.unlocked ? '🏆' : '🔒';
    row.appendChild(icon);

    const info = document.createElement('div');
    info.className = 'achievement-row__info';

    const name = document.createElement('p');
    name.className = 'achievement-row__name';
    name.textContent = achievement.name;
    info.appendChild(name);

    const description = document.createElement('p');
    description.className = 'achievement-row__description';
    description.textContent = achievement.description;
    info.appendChild(description);

    const progressBar = document.createElement('div');
    progressBar.className = 'achievement-row__progress-bar';
    const progressFill = document.createElement('div');
    progressFill.className = 'achievement-row__progress-fill';
    progressFill.style.width = `${progressPercent}%`;
    progressBar.appendChild(progressFill);
    info.appendChild(progressBar);

    const progressLabel = document.createElement('span');
    progressLabel.className = 'achievement-row__progress-label';
    progressLabel.textContent = `${achievement.currentProgress}/${achievement.target}`;
    info.appendChild(progressLabel);

    info.appendChild(buildAchievementMeta(achievement));

    row.appendChild(info);
    return row;
}

/**
 * La línea pequeña bajo la barra de progreso: cuándo lo conseguiste (si lo tienes), qué
 * porcentaje de jugadores lo tiene, y quién fue el primero en toda la plataforma —
 * unidos con "·" y solo los que de verdad aplican (un logro que nadie tiene todavía no
 * lleva "Primero:", por ejemplo).
 */
function buildAchievementMeta(achievement) {
    const meta = document.createElement('p');
    meta.className = 'achievement-row__meta';

    const parts = [];
    if (achievement.unlocked && achievement.unlockedAt) {
        parts.push(`Conseguido el ${formatAchievementDate(achievement.unlockedAt)}`);
    }
    parts.push(`${achievement.rarityPercent}% lo tiene`);
    if (achievement.firstUnlockedByUsername) {
        parts.push(`Primero: ${achievement.firstUnlockedByUsername}`);
    }
    meta.textContent = parts.join(' · ');
    return meta;
}

function formatAchievementDate(isoString) {
    return new Date(isoString).toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' });
}

/** Cada fila abre los logros de esa persona — mismo patrón que ya usa el ranking de rating con showProfileQuickView, solo que aquí navega a pantalla completa en vez de un modal (una lista de hasta 38 logros con descripción y barra de progreso pide más sitio del que da un modal compacto). */
function renderAchievementsLeaderboard(entries, viewerUserId) {
    const tbody = document.getElementById('achievements-leaderboard-list');
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
        row.innerHTML = `<td>${entry.rank}</td><td>${entry.username}</td><td>${entry.unlockedCount}/${entry.totalCount}</td>`;
        row.addEventListener('click', () => openAchievementsScreen(entry.userId, entry.username));
        tbody.appendChild(row);
    }
}