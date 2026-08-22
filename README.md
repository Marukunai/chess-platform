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

### Fase 1.5 — Social y personalización (añadida sobre la marcha, fuera del roadmap original)

Lo que sigue no estaba en el plan inicial de Fase 1, pero fue saliendo de forma natural
una vez la base (partidas en tiempo real + cuentas) ya estaba en pie:

- [x] Reconexión automática (backoff creciente, sin límite de reintentos) — recupera la
  partida activa tras F5 o un corte de red
- [x] Revancha, con los colores intercambiados respecto a la partida anterior
- [x] Chat de partida (efímero, solo retransmisión — no se guarda)
- [x] Perfil editable (nombre de usuario, país, avatar por URL), cambio de contraseña
  (con historial de las últimas 5 para evitar reutilización), borrado lógico de cuenta
- [x] Sistema de amigos: buscar, solicitudes, lista con estado de presencia en vivo
  (en línea / en partida / no molestar)
- [x] Mensajería privada persistente entre amigos, con confirmación de lectura y un
  desplegable general de chat (búsqueda, conversaciones recientes, no leídos)
- [x] Vista rápida de perfil (clic en el rival durante la partida o en una fila del
  ranking), rivales recientes y porcentaje de victorias en el perfil
- [x] Apariencia del tablero personalizable (6 paletas de color, 2 estilos de pieza) —
  cosmético y solo para quien lo cambia, nunca sincronizado con el rival
- [x] Tablero orientado según tu color (piezas propias siempre abajo)
- [x] Lobby y pantalla de login rediseñados, con accesos grandes por icono
- [x] Emoticonos, imágenes por URL y búsqueda de GIFs (proxy propio, la clave de API
  nunca llega al cliente) en el chat de partida y el chat directo
- [x] Silenciar a un rival en el chat de partida — puramente del cliente, no persiste
  entre partidas
- [x] Retar a un amigo directamente a una partida, sin pasar por el emparejamiento
  aleatorio
- [x] Rating separado por modalidad (bullet/blitz/rápidas/clásicas) — cada una con su
  propio ranking, en vez de un único rating global
- [x] Sistema de logros: 38 para empezar, con progreso en vivo, confirmación de lectura
  con fecha de cuándo se desbloqueó cada uno, quién fue la primera persona en
  conseguirlo, qué porcentaje de jugadores lo tiene, y aviso en directo al desbloquear

### Fase 2 — Ampliación (planificado)

- [ ] Bots vía protocolo UCI (Stockfish) con distintos niveles de fuerza
- [ ] Generador de puzzles a partir de partidas analizadas
- [ ] Modo espectador en directo
- [ ] Importación/exportación PGN
- [ ] Salas privadas / partidas por invitación

### Fuera de alcance por ahora

Ajedrez por correspondencia, variantes (Chess960, Crazyhouse...), torneos organizados
(posible Fase 3, reutilizando lo aprendido del sistema de torneos del póker).

**Llamadas de voz/vídeo**, evaluado y descartado a propósito: implicaría montar
infraestructura WebRTC entera (señalización, negociación punto a punto) que no aporta
al núcleo de una plataforma de ajedrez lo suficiente como para justificar el esfuerzo.
Los audios de voz en el chat quedan en el mismo saco por el problema de almacenamiento
que comparten con las imágenes.

## Estructura del repositorio

Monorepo — ver justificación más abajo.

```
chess-platform/
├── backend/            # Spring Boot — API, WebSocket, motor de reglas, rating, matchmaking
│   └── src/main/java/com/chessplatform/
│       ├── engine/         # Reglas de ajedrez — módulo puro, sin dependencias de Spring
│       ├── realtime/        # Config STOMP, GameSession, registro de salas, chat de partida, DTOs de mensajes
│       ├── matchmaking/     # Cola y emparejamiento por rating
│       ├── rating/          # Glicko-2, ratings separados por modalidad, catálogo de logros
│       ├── achievement/     # Progreso de logros, calculado al vuelo salvo la fecha de desbloqueo
│       ├── challenge/       # Retar a un amigo directamente, sin pasar por el emparejamiento
│       ├── media/           # Proxy de búsqueda de GIFs (Giphy) — la clave de API nunca llega al cliente
│       ├── rematch/         # Revancha (propuesta, colores intercambiados, aceptación)
│       ├── presence/        # Estado en línea/en partida/no molestar, avisos a amigos
│       ├── friendship/      # Amigos, solicitudes, mensajería privada persistente
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
| `STOCKFISH_PATH` | Ruta al binario de Stockfish para las partidas contra bot — `/usr/bin/stockfish` por defecto, coincide con dónde lo instala `apk` en la imagen del backend |
| `GIPHY_API_KEY` | Clave gratuita de [developers.giphy.com](https://developers.giphy.com) para el buscador de GIFs del chat — sin ella, el buscador simplemente no da resultados, nada más depende de esto |

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
| **Motor de reglas desde cero** | El póker no requiere validar un grafo de estados tan complejo como jaque/mate/enroque/en passant | Diseño de dominio puro, testing exhaustivo de edge cases, máquina de estados de una partida |
| **STOMP sobre WebSocket** | El póker usa WebSocket raw | Modelo pub/sub por *destinations*, más cercano a cómo se diseñan sistemas de mensajería en producción |
| **Rating Glicko-2** | Nuevo por completo | Sistema de rating con incertidumbre (*rating deviation*) y volatilidad — más sofisticado que un Elo simple, el que usan Lichess y chess.com |
| **Integración UCI con motor externo (Stockfish)** | El póker tenía bots con IA propia | Comunicarse con un proceso externo vía un protocolo de texto estándar, gestionar su ciclo de vida y traducir entre tu dominio y el suyo |
| **Generación de contenido a partir de datos reales** | Nuevo por completo | Pipeline de análisis: partida jugada → evaluación posición a posición con el motor → detección de swing táctico → puzzle |
| **Sistemas en tiempo real más allá de una partida** | El póker tiene mesas, no un grafo social | Presencia (quién está online/en partida), mensajería persistente con confirmación de lectura, notificaciones dirigidas a un usuario concreto sin importar en qué pantalla esté — un canal STOMP por-usuario (`/topic/user/{userId}`) además de los canales por-partida y por-cola |
| **Integrar una API externa de terceros (Giphy)** | Nuevo por completo | Proxy en el propio backend para que la clave de API nunca llegue al cliente, degradar con elegancia si el servicio externo falla, y respetar la cuota con *debounce* en el cliente |
| **Sistema de logros sin tabla de "logros" propia** | Nuevo por completo | Catálogo fijo en código, progreso calculado al vuelo a partir de datos que ya existen — y, para lo poco que sí hay que persistir (cuándo se desbloqueó cada uno, para poder avisar en el momento y saber quién fue el primero), enganchar la detección justo en los eventos relevantes en vez de en cada lectura |
| **Cliente Android reutilizando Retrofit + WebSocket** | Ya lo tienes de tu app de anime | Consolidar el patrón contra un dominio distinto y con estado de partida en tiempo real más exigente (reloj, reconexión) |

## Flujo de trabajo Git

Ver [`CONTRIBUTING.md`](./CONTRIBUTING.md) para convención de ramas, commits (Conventional
Commits) y qué archivos se suben al repositorio.

## Demo pública (despliegue)

Backend en [Render](https://render.com) (*free tier*, Docker) + base de datos en
[Neon](https://neon.tech) (*free tier* permanente — deliberadamente no el Postgres
propio de Render, que caduca a los 30 días) + cliente web como *static site*, también en
Render. Todo definido en [`render.yaml`](./render.yaml) como *Blueprint*.

### 1. Crea la base de datos en Neon

1. [neon.tech](https://neon.tech) → crea una cuenta (sin tarjeta) → *New Project*.
2. Copia la cadena de conexión que te da
   (`postgresql://usuario:contraseña@host/db?sslmode=require`) — la necesitas en el
   paso 3, separada en sus partes.

### 2. Despliega el Blueprint en Render

1. [render.com](https://render.com) → crea una cuenta (sin tarjeta) → *New* → *Blueprint*.
2. Conecta el repositorio de GitHub.
3. Render detecta `render.yaml` solo y propone los dos servicios
   (`chess-platform-backend` y `chess-platform-web`) — confirma.

### 3. Rellena las variables de entorno del backend

En el dashboard de Render, en `chess-platform-backend` → *Environment*:

| Variable | Valor |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host-de-neon>/<db>?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | El usuario de la cadena de conexión de Neon |
| `SPRING_DATASOURCE_PASSWORD` | La contraseña de la cadena de conexión de Neon |
| `ALLOWED_ORIGIN` | La URL de `chess-platform-web`, una vez desplegado (se ve en su propio dashboard) |

`JWT_SECRET` se genera solo (`generateValue: true` en el *Blueprint*) — no hay que
tocarlo.

### 4. Actualiza `web/js/config.js` con la URL real del backend

Con `chess-platform-backend` ya con su URL definitiva (algo como
`https://chess-platform-backend.onrender.com`), sustitúyela en `web/js/config.js` y haz
commit — Render vuelve a desplegar el *static site* solo al detectar el *push*.

### Nota sobre el primer arranque

El plan gratuito de Render "duerme" el backend a los 15 minutos de inactividad — la
primera petición tras eso tarda 30-60 segundos en responder mientras despierta. Es
normal, no un fallo — verlo así una vez no significa que algo se haya roto. Es también
la razón práctica por la que una partida en curso puede perderse sin avisar — ver
"Limitaciones conocidas" más abajo.

## Decisiones de arquitectura

Ver [`docs/architecture-decisions.md`](./docs/architecture-decisions.md) para el registro
de las decisiones clave tomadas al arrancar el proyecto y su justificación.

## Limitaciones conocidas

Cosas que sé que faltan ahora mismo, documentadas a propósito en vez de dejarlas como
sorpresa:

- **Una partida en curso no sobrevive a que el backend se reinicie**: `GameSession`
  vive solo en memoria (`GameSessionRegistry`) y no se guarda en la base de datos hasta
  que termina de forma normal (jaque mate, rendición, tiempo, tablas...) — ver
  `GameResultRecorder`. Si el proceso del backend se reinicia a media partida (un
  despliegue, un `Ctrl+C`, o el propio *free tier* de Render "despertando" tras dormir),
  esa partida desaparece sin dejar rastro, ni siquiera en el historial. La reconexión
  del cliente (ver ADR-012 y `websocket-client.js`) resuelve los baches de red o cerrar
  y reabrir la pestaña — el servidor sigue teniendo la partida guardada en memoria, solo
  hay que volver a engancharse — pero no puede hacer nada si el propio servidor pierde
  esa memoria. Arreglarlo de verdad implicaría persistir el estado de cada partida de
  forma continua mientras está en curso, no solo al terminar — un cambio bastante más
  grande que la reconexión en sí, y que de momento se deja fuera a propósito.

- **La reproducción de una partida siempre se ve con blancas abajo**, sin importar qué
  color jugaras tú en esa partida en concreto — a diferencia de la partida en vivo, que
  sí se orienta según tu color. Decisión consciente de alcance, no un descuido: evita
  que la orientación de tu última partida en vivo "se filtre" a una reproducción sin
  relación con ella.

- **El ranking global de logros recalcula el progreso de cada usuario activo en cada
  petición**: no hay ningún contador cacheado — ver el javadoc de `AchievementService`.
  Aceptable a la escala de un proyecto personal, pero el primer sitio a optimizar
  (cachear o persistir el recuento) si esto llegara a tener miles de usuarios
  concurrentes de verdad.

- **Los audios/vídeos que se comparten en el chat son enlaces, no archivos subidos**:
  igual que el avatar del perfil, pegar la URL de una imagen o GIF ya alojado en otro
  sitio — no hay almacenamiento de ficheros propio en la plataforma, ni falta que hace
  para lo que aporta.

## Licencia

Proyecto personal / de aprendizaje. Ajusta esta sección si más adelante decides
publicarlo con una licencia concreta (MIT es la opción habitual para este tipo de
proyecto si en algún momento quieres abrirlo).