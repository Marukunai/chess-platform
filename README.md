# ♟️ Chess Platform — Plataforma de Ajedrez Online en Tiempo Real

[![Backend CI](https://github.com/Marukunai/chess-platform/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/Marukunai/chess-platform/actions/workflows/backend-ci.yml)

Plataforma de ajedrez online multiplataforma (web + Android) donde dos jugadores pueden
jugar partidas en tiempo real, con sistema de rating Glicko-2, bots basados en Stockfish y
generación automática de puzzles tácticos a partir de partidas jugadas.

Proyecto personal construido reutilizando y ampliando la base de un backend de póker
Texas Hold'em en tiempo real (WebSocket, JWT, torneos, moderación) — aquí el foco está en
un motor de reglas propio, un sistema de rating serio y la integración con un motor de
ajedrez externo.

## Alcance

### Fase 1 — MVP

- [x] Motor de reglas de ajedrez propio (movimientos legales, jaque, jaque mate, ahogado,
  enroque, en passant, coronación, tablas por repetición/50 movimientos)
- [x] Partidas 1v1 en tiempo real vía STOMP sobre WebSocket, con reloj configurable
  (bullet/blitz/rápidas/clásicas)
- [x] Autenticación JWT
- [x] Rating Glicko-2 actualizado tras cada partida
- [x] Matchmaking básico por cola, emparejando por rating cercano
- [x] Cliente web con tablero interactivo
- [ ] Cliente Android conectado a la misma partida en vivo
- [x] Historial de partidas con reproducción movimiento a movimiento

### Fase 2 — Ampliación (planificado)

- [ ] Bots vía protocolo UCI (Stockfish) con distintos niveles de fuerza
- [ ] Generador de puzzles a partir de partidas analizadas
- [ ] Modo espectador en directo
- [ ] Importación/exportación PGN
- [ ] Perfiles públicos con estadísticas
- [ ] Salas privadas / partidas por invitación

### Fuera de alcance por ahora

Ajedrez por correspondencia, variantes (Chess960, Crazyhouse...), torneos organizados
(posible Fase 3, reutilizando lo aprendido del sistema de torneos del póker).

## Estructura del repositorio

Monorepo — ver justificación más abajo.

```
chess-platform/
├── backend/            # Spring Boot — API, WebSocket, motor de reglas, rating, matchmaking
│   └── src/main/java/com/chessplatform/
│       ├── engine/         # Reglas de ajedrez — módulo puro, sin dependencias de Spring
│       ├── realtime/        # Config STOMP, GameSession, registro de salas, DTOs de mensajes
│       ├── matchmaking/     # Cola y emparejamiento por rating
│       ├── rating/          # Glicko-2
│       ├── auth/            # JWT, Spring Security
│       └── persistence/     # Entidades JPA, repositorios
├── web/                # Cliente web (vanilla JS + CSS, sin framework pesado)
├── android/            # Cliente Android (Kotlin) — placeholder, ver android/README.md
├── docs/               # Documentación técnica adicional (decisiones de arquitectura)
├── docker-compose.yml
├── .env.example
├── CONTRIBUTING.md     # Flujo de Git: ramas, commits, qué se sube al repo
└── README.md
```

## Cómo levantar el entorno de desarrollo

### Requisitos

- Docker y Docker Compose
- JDK 21 (solo si quieres correr el backend fuera de Docker)
- Maven 3.9+ (opcional si usas Docker)

### Pasos

1. Copia el archivo de variables de entorno y ajusta los valores:

   ```bash
   cp .env.example .env
   ```

   Genera un `JWT_SECRET` propio — **no uses el valor de ejemplo ni en local**:

   ```bash
   openssl rand -base64 48
   ```

2. Levanta base de datos + backend:

   ```bash
   docker compose up --build
   ```

   El backend queda expuesto en `http://localhost:8080` (o el puerto que definas en
   `SERVER_PORT`). El endpoint WebSocket está en `http://localhost:8080/ws`.

3. Sirve el cliente web (mientras no tenga build propio, basta un servidor estático):

   ```bash
   cd web
   python3 -m http.server 5500
   ```

   Ábrelo en `http://localhost:5500`.

### Variables de entorno (`.env`)

| Variable | Descripción |
|---|---|
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Credenciales de la base de datos local |
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring activo (`docker` por defecto) |
| `SERVER_PORT` | Puerto donde escucha el backend |
| `JWT_SECRET`, `JWT_EXPIRATION_MS` | Firma y expiración de los tokens JWT |
| `STOCKFISH_PATH` | Ruta al binario de Stockfish (se usa a partir de Fase 2) |

Ver `.env.example` para la plantilla completa. **`.env` nunca se sube al repositorio** —
detalles en [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Monorepo, y por qué

Trabajando en solitario, monorepo. Motivos:

- El protocolo de mensajes WebSocket entre backend, web y Android va a cambiar mientras el
  proyecto evoluciona — con monorepo, un cambio de protocolo y su adaptación en los
  clientes puede ir en commits relacionados y visibles juntos, en vez de coordinar
  versiones entre repos separados.
- Un único issue tracker y un único historial simplifica la vuelta atrás y el seguimiento
  de qué se hizo cuándo.
- No hay equipos distintos con permisos distintos que justifiquen separar repos (la razón
  habitual para hacerlo en entornos de empresa).

Si en el futuro el proyecto sumara colaboradores externos centrados solo en el cliente
Android, sería razonable reconsiderar y extraerlo — no es el caso ahora.

## Qué voy a aprender / profundizar con este proyecto

| Área | Qué es nuevo respecto al backend de póker | Por qué importa |
|---|---|---|
| **Motor de reglas desde cero** | El póker no requiere validar un grafo de estados tan complejo como jaque/mate/enroque/en passant/tablas | Diseño de dominio puro, testing exhaustivo de edge cases, máquina de estados de una partida |
| **STOMP sobre WebSocket** | El póker usa WebSocket raw | Modelo pub/sub por *destinations*, más cercano a cómo se diseñan sistemas de mensajería en producción |
| **Rating Glicko-2** | Nuevo por completo | Sistema de rating con incertidumbre (*rating deviation*) y volatilidad — más sofisticado que un Elo simple, el que usan Lichess y chess.com |
| **Integración UCI con motor externo (Stockfish)** | El póker tenía bots con IA propia | Comunicarse con un proceso externo vía un protocolo de texto estándar, gestionar su ciclo de vida y traducir entre tu dominio y el suyo |
| **Generación de contenido a partir de datos reales** | Nuevo por completo | Pipeline de análisis: partida jugada → evaluación posición a posición con el motor → detección de swing táctico → puzzle |
| **Cliente Android reutilizando Retrofit + WebSocket** | Ya lo tienes de tu app de anime | Consolidar el patrón contra un dominio distinto y con estado de partida en tiempo real más exigente (reloj, reconexión) |

## Flujo de trabajo Git

Ver [`CONTRIBUTING.md`](./CONTRIBUTING.md) para convención de ramas, commits (Conventional
Commits) y qué archivos se suben al repositorio.

## Decisiones de arquitectura

Ver [`docs/architecture-decisions.md`](./docs/architecture-decisions.md) para el registro
de las decisiones clave tomadas al arrancar el proyecto y su justificación.

## Limitaciones conocidas

Cosas que sé que faltan ahora mismo, documentadas a propósito en vez de dejarlas como
sorpresa — cada una tiene su `TODO` correspondiente en el código:

- **Sin ventana de gracia al reconectar**: si un jugador pierde la conexión a mitad de
  partida, el reloj sigue corriendo (correcto — es *server-authoritative*, no depende de
  que el cliente siga conectado), pero no hay ningún periodo de gracia para volver a
  conectarse antes de que el tiempo se agote. Simplemente pierde por tiempo si no
  regresa a tiempo. Ver `TODO` en `GameSession`.
- **CORS abierto a cualquier origen** (`setAllowedOriginPatterns("*")`, tanto en
  `SecurityConfig` como en `WebSocketConfig`) — correcto para desarrollo local, pero hay
  que restringirlo al dominio real antes de cualquier despliegue accesible desde fuera.
- **No existe `JwtAuthenticationFilter` para peticiones HTTP normales**: la identidad
  solo se valida en el `CONNECT` de STOMP (ver ADR-008) y, obviamente, dentro de
  `/api/auth/**` al hacer login. Como no hay todavía ningún otro endpoint REST que
  necesite identidad (`/api/games/**` es público a propósito, ver ADR-011 en
  `docs/architecture-decisions.md`), no ha hecho falta construirlo — es lo primero que
  tocaría en cuanto aparezca uno.

## Licencia

Proyecto personal / de aprendizaje. Ajusta esta sección si más adelante decides
publicarlo con una licencia concreta (MIT es la opción habitual para este tipo de
proyecto si en algún momento quieres abrirlo).