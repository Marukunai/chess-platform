# Decisiones de arquitectura (ADR resumido)

Registro breve de las decisiones clave tomadas al arrancar el proyecto y su
justificación, para no perder el contexto de "por qué" dentro de unos meses.

## ADR-001: Representación del tablero — mailbox 1D

**Decisión:** array de 64 casillas, no bitboards.

**Motivo:** el rendimiento crítico del proyecto está en Stockfish (Fase 2, vía UCI), no en
el validador de reglas propio. Mailbox prioriza legibilidad y testeabilidad para un motor
de reglas escrito desde cero, donde los bugs sutiles en jaque/enroque/en passant son el
pan de cada día. 0x88 queda anotado como posible refactor futuro si hiciera falta
optimizar generación de movimientos.

## ADR-002: STOMP sobre WebSocket, no WebSocket raw

**Decisión:** `spring-boot-starter-websocket` + `@EnableWebSocketMessageBroker` (STOMP).

**Motivo:** el modelo pub/sub por *destinations* (`/topic/game/{gameId}`) encaja con el
modo espectador de Fase 2 sin retrofitear nada, y es una tecnología nueva respecto al
WebSocket raw ya usado en el backend de póker.

## ADR-003: Rating Glicko-2 desde el inicio

**Decisión:** Glicko-2 en vez de Elo simple.

**Motivo:** migrar de Elo a Glicko-2 más adelante implicaría recalcular el histórico de
ratings de todos los jugadores. El coste adicional de implementarlo desde ya es bajo
comparado con eso, y es el sistema que usan Lichess y chess.com.

## ADR-004: Reloj server-authoritative sin hilo por partida

**Decisión:** guardar tiempo restante + timestamp de la última jugada, calcular el
consumo bajo demanda en vez de un hilo haciendo tick cada segundo por partida.

**Motivo:** escala mejor (sin overhead de hilos con muchas partidas concurrentes) y es más
simple de razonar/testear que un scheduler por partida. Implica que la reconexión necesita
una ventana de gracia antes de declarar abandono — se tiene en cuenta desde el diseño de
`GameSession`, no se añade después.

## ADR-005: Monorepo

**Decisión:** un único repositorio para backend, web y Android.

**Motivo:** desarrollo en solitario; el protocolo de mensajes va a cambiar mientras el
proyecto evoluciona y coordinar 3 repos para eso no compensa. Reconsiderar si en el futuro
se suman colaboradores externos centrados en un solo cliente.

## ADR-006: Motor de reglas como módulo puro (`engine/`)

**Decisión:** sin dependencias de Spring, JPA ni WebSocket.

**Motivo:** testeable con JUnit puro sin levantar contexto de Spring, y reutilizable tal
cual desde el generador de puzzles en Fase 2 (que también necesita evaluar posiciones sin
pasar por la capa de tiempo real).

## ADR-007: Tablas por repetición y regla de 50 movimientos — automáticas, no reclamables

**Decisión:** ambas reglas terminan la partida automáticamente en cuanto se cumple la
condición, sin que ningún jugador tenga que "reclamarlas".

**Motivo:** en ajedrez presencial (FIDE) estas reglas son reclamables porque hay un
árbitro que verifica la reclamación, y existe incentivo estratégico en no reclamar si a
uno le conviene seguir jugando. En una plataforma online sin árbitro, y sin haber
construido una interfaz de reclamación, lo más simple y coherente es que la partida
termine sola en cuanto se cumple la condición — exactamente igual que ya hacíamos con el
ahogado (nadie lo "reclama", se decide solo). Es también el comportamiento de lichess y
chess.com.

## ADR-008: JWT validado en el CONNECT de STOMP, no en el handshake HTTP

**Decisión:** el JWT se valida como cabecera `Authorization` dentro del frame STOMP
`CONNECT`, no en la petición HTTP de *handshake* del WebSocket.

**Motivo:** los navegadores no permiten poner cabeceras arbitrarias en la petición de
*upgrade* de WebSocket — solo query params (quedarían en logs, mala práctica) o, la vía
estándar, autenticar sobre el propio protocolo STOMP una vez la conexión ya está
abierta. `StompAuthChannelInterceptor` intercepta el `CONNECT`, valida el token y fija
el `Principal` de la sesión, disponible después en cualquier `@MessageMapping` sin
revalidar en cada mensaje. `/ws/**` queda deliberadamente público a nivel HTTP (ver
`SecurityConfig`) porque la identidad real se verifica aquí, no en el *handshake*.

## ADR-009: `GameEndNotifier` separado de `GameResultRecorder`

**Decisión:** el fin de una partida se reparte en dos colaboradores con
responsabilidades distintas — `GameEndNotifier` avisa por WebSocket y limpia el
registro en memoria; `GameResultRecorder` calcula el nuevo Glicko-2 de ambos jugadores y
guarda la partida en Postgres.

**Motivo:** son dos preocupaciones distintas (transporte/notificación vs
persistencia/rating), y separarlas permite que un fallo de base de datos no impida que
los jugadores se enteren de que la partida ha terminado — `GameEndNotifier` captura
cualquier excepción que lance `GameResultRecorder` y sigue adelante con el aviso y la
limpieza igualmente (ver el test
`endGameStillBroadcastsAndCleansUpEvenIfRecordingTheResultFails`). Es peor dejar a los
jugadores esperando indefinidamente que perder el guardado de una partida concreta.

## ADR-010: Sincronización por partida, no un candado global

**Decisión:** todo acceso al estado de una `GameSession` (jugar, rendirse, unirse, y el
barrido periódico de timeout) se sincroniza sobre la propia instancia de `GameSession`
(`synchronized (session)`), nunca sobre `GameSessionRegistry` ni un candado compartido
entre todas las partidas.

**Motivo:** los mensajes STOMP de un mismo cliente pueden procesarse en hilos distintos,
y `GameTimeoutService` corre en su propio hilo programado — sin protección, dos jugadas
casi simultáneas para la misma partida podrían pasar la comprobación de legalidad las
dos *antes* de que cualquiera mute el tablero, y aplicarse ambas. Sincronizar sobre la
instancia de la partida (no un candado global) acota el bloqueo a esa partida concreta:
miles de partidas concurrentes no se bloquean entre sí por esto — solo se serializa el
acceso a la misma partida, que es exactamente donde puede haber una carrera real.

## ADR-011: Reproducción de partidas reconstruida en el servidor

**Decisión:** el historial guarda solo las jugadas en notación UCI (`Game.moveList`); al
pedir el detalle de una partida, el servidor reproduce esas jugadas sobre un
`Board.initial()` real y manda al cliente la secuencia completa de posiciones FEN ya
calculada (`GameReplayService`).

**Motivo:** el cliente web no tiene ningún motor de reglas de ajedrez en JavaScript, y no
debería necesitarlo — reimplementar las reglas ahí solo para poder "reproducir" jugadas
sería duplicar lógica ya construida, testeada y de confianza en el motor Java (el mismo
que valida las partidas en vivo). El cliente se limita a recorrer un array de FEN con el
mismo `renderBoard()` que ya usa para la partida en directo — cero lógica de ajedrez en
el navegador, en ningún sitio.

## ADR-012: Ventana de gracia de reconexión — 30 segundos, independiente del reloj

**Decisión:** al desconectarse, un jugador tiene 30 segundos para volver a conectar
antes de que la partida se dé por abandonada. Es una condición aparte del timeout por
reloj (ADR-004): el reloj sigue corriendo mientras el jugador está desconectado (no se
pausa), y si los dos jugadores están desconectados a la vez cuando se cumple la
ventana, se declaran tablas en vez de dar la victoria arbitrariamente a quien se
comprueba primero.

**Motivo:** sin esta ventana, un jugador que pierde la conexión con un control de
tiempo largo (p. ej. clásicas, 30+20) podría dejar al rival esperando minutos u horas
hasta que el reloj se agote solo, aunque esté claro desde el principio que no va a
volver. 30 segundos es un valor de partida razonable sin datos propios de uso real que
lo justifiquen mejor — vale la pena ajustarlo si en el futuro se observa que es
demasiado corto o largo en la práctica.