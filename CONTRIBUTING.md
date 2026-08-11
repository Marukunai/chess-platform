# Flujo de trabajo Git

Aunque trabajes solo en este proyecto, seguir disciplina de Git sirve para tener un
historial legible y útil (encontrar cuándo se introdujo un bug, generar un changelog,
volver a un punto conocido) y para tener el hábito bien afianzado de cara a trabajo en
equipo.

## Ramas

- `main` — siempre en estado funcional/desplegable. No se commitea directamente aquí,
  salvo fixes triviales (typos en README, etc.).
- Feature branches — una rama por tarea. Nunca se trabaja directamente en `main`.

Si más adelante quieres separar "trabajo en curso" de "listo para release", puedes añadir
`develop` como rama de integración. Para un proyecto en solitario no es necesario de
partida — `main` + feature branches es suficiente.

### Convención de nombres de rama

```
<tipo>/<descripción-corta-en-kebab-case>
```

| Tipo | Uso | Ejemplo |
|---|---|---|
| `feature/` | Nueva funcionalidad | `feature/motor-reglas-enroque` |
| `fix/` | Corrección de bug | `fix/deteccion-jaque-mate-en-passant` |
| `refactor/` | Cambio de código sin alterar comportamiento | `refactor/extraer-board-a-clase-inmutable` |
| `chore/` | Mantenimiento (config, dependencias, CI) | `chore/setup-docker-compose` |
| `docs/` | Solo documentación | `docs/actualizar-readme-fase-2` |
| `test/` | Añadir/mejorar tests sin tocar lógica | `test/cobertura-enroque` |

Más ejemplos reales del roadmap de Fase 1:
```
feature/websocket-game-session
feature/glicko2-rating-service
feature/matchmaking-por-rating
fix/reconexion-jugador-timeout
chore/dockerfile-backend
```

## Commits — Conventional Commits

Formato ([conventionalcommits.org](https://www.conventionalcommits.org/)):

```
<tipo>(<scope opcional>): <descripción en imperativo, minúscula, sin punto final>

[cuerpo opcional explicando el porqué, no el qué — el diff ya dice el qué]

[footer opcional: BREAKING CHANGE, referencias a issues]
```

Tipos permitidos: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `style`, `ci`.

Ejemplos buenos:
```
feat(engine): implementar validación de enroque corto y largo

fix(realtime): evitar condición de carrera al reconectar durante el reloj

refactor(engine): extraer generación de movimientos de alfil/torre/dama a método común

test(engine): cubrir casos de tablas por ahogado

docs(readme): documentar variables de entorno para Docker Compose

chore: configurar .gitignore inicial y estructura de módulos
```

Reglas prácticas:

- **Un commit = un cambio lógico coherente.** Si el mensaje necesita una "y" para
  describirlo, probablemente sean dos commits.
- **Imperativo**: "añade", "corrige" — no "añadido", "corrigiendo".
- El `scope` entre paréntesis referencia el módulo afectado (`engine`, `realtime`,
  `matchmaking`, `rating`, `auth`, `persistence`, `web`, `android`). Te permite filtrar el
  historial después con `git log --grep="engine"` o `git log -- backend/.../engine`.
- Evita commits tipo `fix`, `wip`, `cambios` a secas — no aportan nada al buscar en el
  historial dentro de 6 meses.
- Commits pequeños y frecuentes > un commit gigante al final del día. Si algo no compila
  todavía pero quieres guardar progreso, un commit `wip` local está bien, pero
  *squashea/reescribe el mensaje* antes de mergear a `main` (`git commit --amend` o
  `git rebase -i`).

## Qué sí y qué no se sube a GitHub

### Nunca se commitea (ya cubierto por `.gitignore`)

- `.env` y cualquier variante `.env.*` (excepto `.env.example`) — contiene contraseñas de
  base de datos, secreto JWT, etc.
- Carpetas de build: `target/` (Maven), `build/` (Gradle/Android), `node_modules/`
- Configuración de IDE: `.idea/`, `*.iml`, `.vscode/`
- `local.properties` de Android (rutas absolutas del SDK de tu máquina — no tiene sentido
  en otra máquina)
- Claves y certificados (`*.pem`, `*.key`, keystores de firma de la APK)
- Logs (`*.log`)

### Sí se commitea

- `.env.example` — plantilla de las variables necesarias, **sin valores reales**
- Todo el código fuente y su configuración de build (`pom.xml`, `build.gradle` cuando
  exista el módulo Android)
- `docker-compose.yml`, `Dockerfile`
- Documentación (`README.md`, este archivo, `docs/`)

**Regla general:** si al clonar el repo en una máquina limpia hace falta ese archivo para
que el proyecto compile o arranque, se sube. Si es específico de tu entorno local
(credenciales, rutas absolutas, caché de build), no.

## Flujo típico de trabajo

```bash
git checkout main
git pull
git checkout -b feature/nombre-de-la-tarea

# ... trabajo, commits pequeños y frecuentes ...

git push -u origin feature/nombre-de-la-tarea

# Cuando la feature esté completa y compilando/testeada:
git checkout main
git merge --no-ff feature/nombre-de-la-tarea
git push
git branch -d feature/nombre-de-la-tarea
```

`--no-ff` conserva la rama como un bloque visible en el historial (útil para ver de un
vistazo qué commits pertenecen a qué feature) en vez de aplanarlos contra `main`.

## Tags de fase

Cuando cierres una fase completa, márcalo con un tag anotado:

```bash
git tag -a v0.1.0-fase1 -m "MVP: motor de reglas, partidas 1v1, rating, matchmaking, web + android básicos"
git push origin v0.1.0-fase1
```

Te da puntos de referencia claros en el historial y checkpoints por si necesitas volver a
un estado conocido y funcional.
