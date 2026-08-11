# Cliente Android — pendiente de scaffold

Este módulo se aborda como siguiente paso, una vez backend + cliente web se hablen de
extremo a extremo (WebSocket, join a una partida, primera jugada aplicada).

**Por qué no se generó ya en este primer scaffold:** un proyecto Android real necesita el
wrapper de Gradle (binarios) y se integra mejor generándolo directamente desde Android
Studio (`File > New > Project`), con las versiones de SDK/AGP de tu máquina, en vez de un
scaffold de texto que luego haya que reconciliar a mano.

## Plan para cuando lo abordemos

- Kotlin, reutilizando los patrones ya usados en tu app de anime: Retrofit para REST
  (`/api/auth/**`) y un cliente STOMP sobre WebSocket para la partida en vivo.
- Un `ChessBoardView` (Canvas) o Jetpack Compose (a decidir) que consuma el mismo
  `GameStateSyncMessage` que ya consume el cliente web.
- Reutilización de los DTOs de `backend/.../realtime/dto` como contrato — mismo JSON para
  ambos clientes, así evitamos que diverjan.

Dímelo cuando quieras que generemos este módulo y seguimos desde aquí.
