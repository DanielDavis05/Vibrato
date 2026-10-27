# Vibrato — Feature List

Vibrato is a Minecraft **1.21.1** mod (Fabric) that runs autonomous bots. It builds on ideas from
[AltoClef](https://github.com/gaucho-matt/altoclef) / [UnionClef](https://github.com/3ndetz/unionclef)
(UnionClef leans toward combat, but has features worth borrowing) and uses
[Baritone](https://github.com/cabaletta/baritone) (1.21.1 branch) for pathfinding, vendored as a submodule.

## Goal

Bots that can **reliably play Minecraft in unique ways**. Roughly 10 bots can be dispatched at once, each with
random tasks, and together they take part in essentially every aspect of the game:

- building, gathering materials, crafting
- exploring, sleeping, trading, enchanting
- gathering gear, leveling up, advancements
- killing mobs, transportation
- items, inventory and chests
- and so on

They must do this in a **human, realistic manner** and be able to **interact in all the ways players can**
(including chat and command use).

## Requirements

### 0. Project basics
- Target Minecraft **1.21.1** only.
- The mod is named **Vibrato**. The name must be applied everywhere: mod id, package names, mod metadata,
  resources, build files, docs, command prefixes, log tags.
- Baritone is vendored as a **git submodule** pinned to its 1.21.1 branch (rather than relying on a jar),
  so it can be patched.
- Baritone lacks human movement. Disabling jitter and enabling smooth movement already helps and works, but
  Vibrato should go further.

### 1. Implement every feature already discussed
Everything mentioned in the goal above and in the notes below must be present, including features worth
porting from UnionClef / AltoClef: task system, resource gathering, crafting, smelting, trading, combat,
food/health management, inventory management, chests, beds/sleeping, enchanting, advancements,
transportation (boats, minecarts, elytra, nether portals), building, exploration.

### 2. Random task composition
There must be a way to pick a random task. Not one fixed random task, but **random chaining of many tasks** so
that hundreds of millions of distinct paths are possible and every aspect of the game is hit, including
**command use** and similar interactions.

- A catalog of atomic and composite tasks with prerequisites and effects.
- A planner/sampler that strings tasks together, respecting preconditions and the world state.
- Per-bot seeds, so ~10 bots diverge from each other.
- The combinatorial space (task choice x parameters x ordering) must be large enough to reach 10^8+ distinct
  paths, and coverage of every feature area should be measurable.

### 3. Robustness: no getting stuck, lost, or wasting time
Bots need the sense not to get stuck, get lost, or waste time. AltoClef specifically struggles with:

- corners and edges
- jumping onto the edge of blocks
- traveling across lots of water without swimming

Vibrato needs stuck detection and recovery, progress/timeout watchdogs per task, sane swimming and water
traversal, safe edge and corner handling, and abandonment of tasks that are not making progress.

### 4. One integrated, fluid, performant machine
Plan and integrate everything into one solid working system that feels fluid, moves fast, performs well, and
plays the game like a human:

- smooth, human-like movement and camera (no snap-turning or jitter)
- low tick overhead, even with ~10 bots
- a single coherent architecture rather than a pile of loosely connected parts
- tested and verifiable behavior

## Working notes
- Use Haiku 5.5 (medium effort) subagents for grunt work.
- Write the plan before implementing; then delegate.
