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
