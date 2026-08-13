// Punto de entrada del cliente web. De momento solo renderiza el tablero y deja
// preparados los botones — la lógica real de unirse a una partida está pendiente de que
// el backend tenga matchmaking + GameSession funcionando de extremo a extremo.

document.addEventListener('DOMContentLoaded', () => {
    renderInitialPosition();

    document.getElementById('join-btn').addEventListener('click', () => {
        // TODO (Fase 1): sustituir este prompt() por un formulario de login real en
        // cuanto exista una pantalla de autenticación en el cliente web — de momento es
        // la forma más rápida de probar el flujo completo (login por API + JWT en el
        // CONNECT de STOMP) sin construir esa pantalla todavía.
        const token = prompt('Pega aquí tu token JWT (obtenido de POST /api/auth/login):');
        if (!token) {
            return;
        }

        connect(token, () => {
            console.log('Conectado — unirse a partida pendiente de implementar (matchmaking)');
            // TODO (Fase 1): enviar solicitud de matchmaking, esperar asignación de
            // gameId, suscribirse a /topic/game/{gameId}.
        });
    });

    document.getElementById('resign-btn').addEventListener('click', () => {
        // TODO (Fase 1): enviar mensaje RESIGN al backend
        console.log('Rendirse — pendiente de implementar');
    });
});