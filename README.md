# Poker Trainer

Play full no-limit hold'em hands against bots, at a 6-max table or heads-up, and get feedback on
every decision you make. Feedback combines solver-style numbers (what a balanced strategy does)
with opponent-aware advice (what wins the most against this particular bot).

This repo is the first working slice: a real game engine, an HTTP API, and the web table from the
Felt v2 designs. Feedback is a placeholder heuristic for now (see "What is real and what is a
placeholder" below).

## Layout

```
engine/   Pure Java, no dependencies. Cards, hand evaluator, equity, ranges, the 2-6 player
          game state machine (positions, min-raises, side pots), placeholder bots, feedback.
server/   Javalin HTTP API. Sessions, table lineups, JSON views. Thin on purpose.
web/      React + TypeScript + Vite. Lobby, table, feedback sheet, session summary.
```

## Running it locally

Requirements: JDK 21, Gradle 8.x (only once, to create the wrapper), Node 20+.

```bash
# one time: create the Gradle wrapper
gradle wrapper

# terminal 1: API on http://localhost:7070
./gradlew :server:run

# terminal 2: web app on http://localhost:5173 (proxies /api to 7070)
cd web
npm install
npm run dev
```

Tests:

```bash
./gradlew test          # engine + server
cd web && npm run build # typecheck + production build
```

## The coach

Every decision you make gets a verdict (good, inaccuracy, mistake), a headline, one or two
sentences of explanation and a tip. The table keeps moving while the coach thinks, and the
feedback sheet fills in when the answer arrives.

- **AI coach.** Set `ANTHROPIC_API_KEY` before starting the server and each decision is sent to
  Claude with the full table state: positions, stacks, cards, board, the action so far, your
  options, the bots' known styles, and the background numbers below. The reply is forced through
  a tool call with a fixed schema at temperature 0, which keeps the format and tone consistent.
  `COACH_MODEL` picks the model (default: a fast Haiku model, since this runs on every decision).
- **Rules coach.** Without a key, or if a call fails or takes longer than 20 seconds, a
  deterministic rules coach answers instead. You always get feedback.
- **The numbers stay in the review.** Equity, pot odds, stack-to-pot ratio, the opening chart and
  the pot-odds check are computed for every decision. They go to the coach as context and are
  shown in the end-of-hand review, not in the live sheet.

```bash
export ANTHROPIC_API_KEY=sk-ant-...
./gradlew :server:run
```

The key only lives on the server. The browser never sees it.

## API

| Method | Path | What it does |
| --- | --- | --- |
| GET | `/api/lobby` | Bots and lineup presets |
| POST | `/api/sessions` | `{format: "SIX_MAX" \| "HEADS_UP", preset?, lineup?, stackBb?, seed?}`. Creates a session and deals the first hand |
| GET | `/api/sessions/{id}` | Current session and hand |
| POST | `/api/sessions/{id}/actions` | `{type, amount}` copied from one of `hand.legalActions`. Returns the new state and a `decision` handle |
| GET | `/api/sessions/{id}/hands/{hand}/decisions/{index}/coach` | The coach's feedback for one decision. Waits until it is ready |
| GET | `/api/sessions/{id}/hands/{hand}/review` | Every decision in a hand with the coach's verdict and the background numbers |
| POST | `/api/sessions/{id}/hands` | Deals the next hand |

The server runs bots until it is the hero's turn or the hand ends, so every response is a state
where the hero has something to do (or the hand is over).

## Design decisions worth knowing

- **Chips are ints.** 100 chips is one big blind. No floating point in game logic. The API sends
  big blinds as doubles for display, but actions carry chip amounts copied from `legalActions`.
- **Fixed bet sizes.** 33%, 75%, 150% of pot, plus all-in; preflop opens of 2.5bb; raises of 3x or
  pot. This keeps hands on trees a solver can cover, and it is also the clean UI in the designs.
- **Cards are ints 0-51** (`rank * 4 + suit`) and ranges are weights over all 1326 combos, so card
  removal is automatic.
- **Stacks reset each hand** to the chosen depth, so every spot is at a known stack depth.
- **The hero is always seat 0** and the button moves, so you play every position.
- **The coach explains, the math decides the facts.** The language model sees the background
  numbers and is told not to contradict the pot-odds check or quote figures, and the live sheet
  shows no numbers at all.

## What is real and what is a placeholder

Real and tested: hand evaluation, equity (exact on the turn, Monte Carlo otherwise), range parsing,
the full betting state machine for 2-6 players including side pots and uncalled bets, sessions and
the API. A fuzz test plays thousands of random hands at every table size and checks every chip.

Placeholders, clearly marked in code:

- `ProfileBot` decides from equity against random hands plus a few personality knobs.
- The background numbers use equity against random hands, which runs high when opponents have
  shown strength, and approximate opening charts (`PreflopCharts`). Solver lines come later.
- Sessions live in memory.

See the development plan for the order these get replaced.
