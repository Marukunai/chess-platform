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

## ADR-013: Despliegue en Render + Neon, no el Postgres propio de Render

**Decisión:** el backend y el cliente web se despliegan en Render (nivel gratuito,
`render.yaml` como *Blueprint*); la base de datos, en Neon, no en el Postgres propio de
Render. Los orígenes CORS pasan a ser configurables por variable de entorno
(`ALLOWED_ORIGIN`, ver `CorsProperties`) en vez del `"*"` fijo que había hasta ahora.

**Motivo:** el Postgres gratuito de Render caduca a los 30 días de creado — inviable
para una demo que se supone debe seguir funcionando. El nivel gratuito de Neon es
permanente (sin tarjeta, sin caducidad), pensado justo para este caso. Separar backend y
Postgres en proveedores distintos es una molestia menor (dos paneles en vez de uno) a
cambio de que la demo no se rompa sola al mes de desplegarla.

No se conocía la URL final del cliente desplegado al escribir el `Blueprint`, así que
`ALLOWED_ORIGIN` se deja como variable de entorno (`sync: false` en `render.yaml`) para
rellenar a mano una vez Render asigna las URLs reales de ambos servicios.

## ADR-014: Un único index.html con pantallas mostradas/ocultadas por JS

**Decisión:** el cliente web es un único `index.html` que carga las 5 pantallas (login,
lobby, partida, historial, reproducción) como `<section>` alternadas con el atributo
`hidden` desde `main.js` (`showScreen()`), en vez de un archivo `.html` independiente
por pantalla con navegación real entre páginas.

**Motivo:** la app mantiene una conexión WebSocket viva (SockJS/STOMP) y estado en
memoria (partida activa, suscripciones, token) que tienen que sobrevivir mientras el
jugador se mueve entre pantallas. Con páginas HTML separadas, cada navegación recarga
el documento entero: el contexto de JavaScript se destruye, la conexión WebSocket se
cierra, y habría que reconectar y reconstruir todo el estado en cada cambio de pantalla
— inaceptable para algo con tiempo real de por medio. Cambiar de pantalla con `hidden`
es instantáneo, sin parpadeo de recarga, y la conexión nunca se toca.

Es el mismo patrón que usan frameworks tipo React/Vue por debajo (un único punto de
entrada, "páginas" que en realidad son JavaScript mostrando/ocultando árboles de
componentes) — aquí está escrito a mano porque Fase 1 decidió no meter un framework de
frontend. Lichess y chess.com, las referencias directas del dominio, funcionan igual
por el mismo motivo.

**Cuándo NO haría falta esto**: contenido estático donde cada página es independiente y
no necesita mantener nada vivo entre navegaciones — un blog, páginas de marketing,
documentación. Ahí un `.html` por página es más simple y no hay motivo para
complicarlo.

## ADR-015: Canal STOMP persistente por usuario (`/topic/user/{userId}`)

**Decisión:** además de `/topic/game/{gameId}` (mientras dura una partida concreta) y
`/topic/matchmaking/{userId}` (mientras se está en la cola buscando rival), existe un
tercer canal, `/topic/user/{userId}`, al que el cliente se suscribe una única vez justo
al conectar (`connectAndGoToLobby` en `main.js`) y mantiene mientras dure la sesión,
sin importar en qué pantalla esté ni si hay alguna partida activa.

**Motivo:** proponer una revancha pasa *después* de que la partida original ya terminó
— su `GameSession` ya se eliminó del registro (ver `GameEndNotifier`), y con ella
cualquier canal ligado a esa partida concreta. Mientras tanto, quien recibe la
propuesta puede estar en cualquier sitio: todavía mirando el modal de fin de partida,
de vuelta en el lobby, revisando su historial o su perfil. Ninguno de los dos canales
existentes (`/topic/game/{gameId}`, ligado a una partida que ya no existe;
`/topic/matchmaking/{userId}`, del que el cliente se desuscribe en cuanto encuentra
partida, ver `onMatchFound`) llega a esas pantallas. Un canal fijo por usuario, vivo
toda la sesión, sí.

No hace falta tocar `WebSocketConfig`: `enableSimpleBroker("/topic")` ya habilita
cualquier destino bajo ese prefijo, así que `/topic/user/{userId}` funciona exactamente
igual que los otros dos sin configuración adicional.

**Alcance actual:** solo lo usa la revancha (`RematchController`), pero está pensado
como el sitio natural para cualquier futuro aviso que tenga que alcanzar a un usuario
concreto sin importar la pantalla — no una pieza de un solo uso. (Actualización: desde
entonces también lo usan amistad, presencia y mensajería directa — ver ADR-019 y
ADR-020 — confirmando que la previsión no iba desencaminada.)

## ADR-016: `JwtAuthenticationFilter` para peticiones HTTP normales

**Decisión:** un filtro (`OncePerRequestFilter`) valida el JWT en peticiones HTTP
normales — distinto de `StompAuthChannelInterceptor` (ADR-008), que sigue validando
solo el `CONNECT` de STOMP. El filtro puebla el `SecurityContext` si el token es
válido, pero nunca rechaza la petición él solo: quien decide si una ruta necesita
identidad es `SecurityConfig` (`.authenticated()` vs `.permitAll()`). Sin token, o con
uno inválido, la petición sigue adelante sin autenticar, y si la ruta lo exigía, Spring
Security la rechaza más adelante en la cadena con un 401 normal.

Como consecuencia, `/api/games/**` y `/api/users/**` distinguen ahora GET (público, ver
motivo original en ADR-011: cualquiera puede consultar partidas o perfiles) de
POST/PUT/DELETE (requieren identidad).

**Motivo:** hasta ahora no había ningún endpoint REST más allá de `/api/auth/**` que
necesitara identidad — todo lo demás era de lectura pública, o pasaba por WebSocket
(donde el `CONNECT` ya resuelve la identidad una vez por conexión). Editar el propio
perfil fue el primer caso real que necesitaba saber "quién eres" en una petición HTTP
normal, y ahí dejó de tener sentido seguir sin este filtro.

## ADR-017: Borrado de cuenta — anonimización, no `DELETE` de la fila

**Decisión:** borrar una cuenta no elimina la fila de `User` — la anonimiza
(`User.anonymizeForDeletion()`): el nombre pasa a `usuario-eliminado-XXXXXXXX`, la
contraseña se sustituye por un hash de una contraseña aleatoria que nadie conoce (login
descartado sin necesitar un campo "activo" aparte), país y avatar se limpian, y queda
excluida del ranking. La fila sigue existiendo.

**Motivo:** las partidas de OTROS jugadores tienen una relación `@ManyToOne` hacia este
`User`. Borrar la fila de verdad rompería su historial (violación de clave foránea) o,
con cascada, se llevaría por delante sus partidas también — algo que ellos no pidieron.
Con la fila anonimizada en su sitio, el historial de quien jugó contigo sigue intacto;
simplemente te ve como "Usuario eliminado" en vez de tu nombre real. Es el mismo patrón
que usan la mayoría de plataformas con historial compartido entre usuarios.

## ADR-018: Historial de las últimas contraseñas, para evitar reutilización

**Decisión:** `User` guarda los hashes de las 4 contraseñas anteriores a la actual
(`passwordHistory`, `@ElementCollection`) — cambiar la contraseña comprueba que la
nueva no coincida ni con la actual ni con ninguna de esas 4 (`matchesAnyRecentPassword`),
es decir, ninguna de las últimas 5 en total.

**Motivo:** sin esto, "cambiar" la contraseña por la misma que ya tenías pasaba la
validación sin más — un hueco de seguridad real detectado durante pruebas manuales, no
solo una mejora cosmética. Limitar a 5 (no guardar el historial entero) es a propósito:
basta para el caso de uso (evitar el "cambio" trivial que no cambia nada) sin acumular
hashes indefinidamente.

## ADR-019: Presencia en memoria, con "no molestar" por encima de "en partida"

**Decisión:** `PresenceRegistry` guarda quién está conectado ahora mismo enteramente en
memoria (igual que `GameSessionRegistry`, ver ADR-004/ADR-010), no en base de datos. El
estado que se muestra a los amigos (`PresenceService.statusOf()`) se calcula con esta
prioridad, de mayor a menor: desconectado > no molestar > en partida > en línea. "No
molestar", que el propio usuario activa, manda por encima de "en partida" (que se
calcula solo a partir de `GameSessionRegistry`) — si alguien activó no molestar,
respetarlo aunque esté jugando.

Además, "no molestar" silencia los AVISOS en vivo (mensajes directos, solicitudes de
amistad, ofertas de revancha) pero nunca la entrega real — un mensaje directo se guarda
igual, y `MatchFoundMessage` (una partida ha empezado de verdad) nunca se silencia, por
no ser una notificación social que se pueda ignorar sin más.

**Motivo:** la presencia es un dato transitorio por naturaleza (deja de ser cierto en
cuanto alguien cierra la pestaña) — guardarlo en base de datos añadiría escrituras
constantes por algo que no necesita sobrevivir a un reinicio del servidor. El único
efecto colateral aceptado (ver README, "Limitaciones conocidas") es que, igual que las
partidas en curso, el estado de presencia tampoco sobrevive a que el backend se
reinicie — se resuelve solo en cuanto cada cliente reconecta.

## ADR-020: Dos mecanismos de chat distintos, a propósito

**Decisión:** el chat de partida (`ChatMessage`/`ChatSendMessage`,
`GameWebSocketController`) y la mensajería privada entre amigos (`DirectMessage`,
`DirectMessageController`) son dos sistemas separados, sin código compartido entre
ellos más allá de convenciones de estilo.

**Motivo:** tienen semánticas opuestas. El chat de partida es puramente efímero — vive
mientras dura la partida y desaparece con ella, igual que las flechas dibujadas en el
tablero (nunca se guarda en base de datos). La mensajería privada es exactamente lo
contrario: tiene que sobrevivir a que el destinatario esté desconectado, need
historial completo, y necesita saber si se ha leído o no (`DirectMessage.read`, con
confirmación de lectura en vivo al remitente). Forzar los dos casos por el mismo
componente habría significado o bien persistir el chat de partida sin necesidad, o
complicar la mensajería privada con la lógica de "solo mientras estás en la partida"
que no le pega. Separarlos deja cada uno tan simple como el problema que resuelve.

## ADR-021: Apariencia del tablero — solo en el cliente, nunca sincronizada

**Decisión:** el tema de color del tablero y el estilo de las piezas se guardan
enteramente en `localStorage` del navegador (`board-theme.js`) — el backend no sabe
nada de esto, no hay ningún campo en `User` ni ningún endpoint.

**Motivo:** es una preferencia puramente cosmética y personal — cómo ves TÚ el tablero
no debería obligar a tu rival a verlo igual, ni falta que hace guardarlo en ningún
sitio compartido. Guardarlo en el servidor habría significado tocar `User`, un
endpoint nuevo, y sincronizar el dato entre dispositivos — coste real para una
preferencia que ya funciona perfectamente bien viviendo solo en el navegador donde se
eligió. Si en el futuro hiciera falta que la preferencia viajara entre dispositivos del
mismo usuario, ahí sí compensaría moverlo al servidor — no antes.

## ADR-022: Rating separado por modalidad — una fila por (usuario, modalidad), no un campo único en `User`

**Decisión:** el rating Glicko-2 dejó de vivir en `User` — ahora cada combinación
(usuario, modalidad) tiene su propia fila en `UserRating` (`rating`, `ratingDeviation`,
`volatility`), con **creación perezosa**: un usuario recién registrado no tiene ninguna
fila hasta que de verdad se le graba el resultado de una partida jugada en esa
modalidad (`GameResultRecorder`) — nunca se crean las cuatro de golpe al registrarse.
Consultar el rating sin que eso exista todavía (`UserRatingService.findOrDefault()`,
usado por matchmaking y por el perfil) devuelve los valores por defecto sin persistir
nada — la fila solo se guarda de verdad cuando GameResultRecorder actualiza un rating
tras una partida real.

**Motivo:** jugar mucho bullet no debería inflar tu rating de clásicas, ni al revés —
son habilidades relacionadas pero no idénticas, y mezclarlas en un único número (como
hacía el diseño original) escondía esa diferencia. La creación perezosa evita dos
problemas: alguien que nunca ha jugado bullet no aparece en su ranking (no tendría
sentido que sí apareciera con un rating por defecto que nunca demostró tener), y
`MatchmakingController` no necesita persistir nada solo por entrar a la cola —
`findOrDefault()` sin guardar sirve de sobra para saber con qué rating buscar rival, y
si al final ni siquiera llega a jugar, no queda ninguna fila huérfana en la base de
datos por el intento.

## ADR-023: Logros calculados al vuelo, con persistencia mínima solo para lo irreconstruible

**Decisión:** el catálogo de logros (`AchievementCatalog`, actualmente 38) vive fijo en
código, no en una tabla — cada uno es una condición sobre una "foto" de estadísticas del
usuario (`UserStatsSnapshot`) calculada en el momento a partir de datos que ya existen
(partidas, amigos, mensajes, rating). Nada de esto se guarda... **excepto una cosa**:
`UserAchievementUnlock` sí persiste la fecha exacta en que cada usuario desbloqueó cada
logro, porque eso concreto no se puede reconstruir después del hecho si no se captura en
el momento en que pasa.

Esa captura ocurre en `AchievementUnlockService.checkAndNotify()`, enganchado
explícitamente en los cuatro sitios donde algo relevante puede cambiar el progreso de
alguien: fin de partida (`GameEndNotifier`), aceptar una amistad
(`FriendshipController`), mandar un mensaje directo (`DirectMessageController`) y editar
el perfil (`UserController`) — a propósito NUNCA en un sitio de lectura (ver el perfil,
el ranking), porque detectar el desbloqueo "cuando alguien mira" en vez de "cuando pasa
de verdad" habría hecho inútiles tanto el aviso en directo (llegaría tarde, o nunca) como
"quién fue el primero" (la fecha sería de cuando se comprobó, no de cuando se consiguió).

**Motivo:** el criterio general de derivar en vez de persistir (mismo que ya usa
`UserController.toProfileResponse()` para victorias/derrotas) se mantiene para todo lo
que SÍ se puede recalcular sin pérdida de información — pero "cuándo" es, por
naturaleza, un dato que solo existe una vez, en el instante en que ocurre. Guardar TODO
el sistema de logros habría sido más simple de entender pero mucho más caro de
mantener sincronizado; guardar SOLO la fecha, enganchada justo donde hace falta, da lo
mejor de los dos mundos.

**Coste asumido a propósito:** tanto el ranking global de logros como la rareza de cada
logro (qué % de cuentas activas lo tiene) recalculan sobre todos los usuarios activos en
cada petición — ver "Limitaciones conocidas" en el README.

## ADR-024: Proxy propio para la búsqueda de GIFs, nunca la clave de API en el cliente

**Decisión:** `GifSearchController`/`GiphyClient` son el único sitio que habla con la
API de Giphy — el cliente web solo llama a `/api/gifs/search`, nunca a Giphy
directamente, y requiere identidad (cae en el `.anyRequest().authenticated()` por
defecto). Sin clave configurada (`GIPHY_API_KEY` vacía), el buscador devuelve una lista
vacía en vez de fallar.

**Motivo:** cualquier clave de API puesta en código JavaScript servido al navegador es,
en la práctica, pública — cualquiera podría leerla del código fuente y agotar la cuota,
o usarla para sus propios fines. Exigir identidad además de esto protege esa misma
cuota de abuso anónimo, ya que consultar este endpoint consume un recurso externo con
límite, a diferencia del resto de lecturas públicas de la plataforma (partidas,
perfiles, rankings) que no dependen de ningún servicio de terceros.

## ADR-025: Retar a un amigo — colores sorteados, no intercambiados como en la revancha

**Decisión:** `ChallengeController` reutiliza casi todo el patrón de `RematchController`
(oferta pendiente en memoria, aviso por `/topic/user/{userId}`, aceptar crea la
partida) pero con una diferencia: los colores se sortean al aceptar, igual que en el
emparejamiento normal — no se intercambian respecto a nada, porque no hay ninguna
partida anterior de la que partir.

**Motivo:** la revancha intercambia colores a propósito (quien perdió con negras juega
con blancas la siguiente) porque existe un contexto previo que lo justifica. Un reto
directo no tiene ese contexto — es la primera partida entre estas dos personas en ese
momento, así que no hay ninguna razón para que uno de los dos "deba" un color en
concreto. Sortear, como en el emparejamiento aleatorio, es lo justo por defecto.

## ADR-026: Imágenes y GIFs en el chat — la URL es el mensaje, sin cambiar el protocolo

**Decisión:** ni `ChatMessage` (chat de partida) ni `DirectMessage` (mensajería
directa) ganaron ningún campo nuevo para soportar imágenes o GIFs. El cliente detecta
si el texto completo de un mensaje es, él solo, una URL reconocible como imagen
(`isChatImageUrl()` en `chat-media.js`) y lo pinta como `<img>` en vez de texto plano.
Elegir un GIF del buscador de Giphy simplemente inserta su URL como si se hubiera
pegado a mano.

**Motivo:** añadir un campo `imageUrl`/`type` a los dos sistemas de mensajería (que ya
eran deliberadamente distintos entre sí, ver ADR-020) habría significado tocar el
protocolo de mensajes STOMP, la entidad `DirectMessage` persistida, y las cuatro rutas
de renderizado — para una función que, en esencia, es "algunos mensajes de texto
resultan ser un enlace a una imagen". Detectarlo en el cliente, solo al pintar, consigue
lo mismo con cero cambios de protocolo y cero migración de base de datos.