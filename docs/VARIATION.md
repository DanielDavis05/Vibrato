# Variation and new interaction forms

Where Vibrato's behavior is still too uniform, and what players do that the bots do not do yet. Ordered by value
for "ten bots that each play differently in a human way". Cost: S = a few classes, M = a workstream, L = research.

## 1. Where variation is thin today

The Director samples a flat sequence of independent activities (about 120 once all workstreams land) with
parameters, weighted by an 8-way persona and a coverage boost. That gives plenty of distinct plans, but they
read as a random list, not as someone pursuing something. The gaps:

| Gap | Today | Fix | Cost |
|---|---|---|---|
| Purpose | Steps are unrelated | **Macro scenarios**: templates that expand into a coherent arc with sampled parameters (settle down: shelter, farm, storage, bed; expedition prep: gear, food, torches, explore; enchanter's trip; trader's route; nether run; mining day; builder's project; sightseeing; social visit) | M |
| Personality | One weight per feature area | **Traits** that change *how* things are done, not just *what*: pace (turn speed, idle glances), caution (retreat thresholds, night behavior), tidiness (inventory organizing), sociability and chattiness (chat rate, greeting range), curiosity (detours to loot and structures), risk (fight vs flee) | M |
| Reactivity | A plan runs until a step ends | **Needs and events layer**: weather (seek shelter, sleep through thunder), night, low food, full inventory (stash trip), loot or mobs nearby, another player or bot greets, low tools. Interrupts the current step and returns to it | M |
| Technique | One way to do each goal | A `technique` parameter per activity: mining (branch, strip, staircase, cave-follow), crossing water (bridge, boat, swim), shelter (dig-in, hut, borrow a cave), food (hunt, farm, fish, trade), iron (caves, village loot, trade), wood (chop, strip, scavenge) | M |
| Memory | Each plan starts fresh | **Persistent base**: remember home, chests, farm, portal (WaypointMemory exists), return to them, extend them over plans | S |
| Time of day | Mostly ignored | Day work vs night routines, dusk wind-down, dawn start | S |
| Content | Few palettes and styles | Palettes by biome, build styles (cottage, tower, pier, lighthouse, well, market stall, windmill-style decor), generated sign text and item names | S |

## 2. New interaction forms (things players do that the bots do not)

**Written and decorative**
* Book and quill journal: after a plan the bot writes what it did that day (generated from the plan log).
* Signs: label chests, welcome sign at a base, directions to a farm or portal, shop signs.
* Name tags and anvil renaming for pets and tools; banners and patterns; item frames, paintings, armor stands.
* Maps and cartography table: make a map, explore to fill it, trade or gift it.

**Social between bots and players**
* Sign shop: chest plus sign ("8 bread for 1 emerald"); other bots trade through it.
* Mailbox and gifts: leave items in a labeled chest for a named bot, tell them by whisper.
* Treasure hunt: hide a chest with loot, post the clue in chat or a book, another bot follows it.
* Caravan: two bots travel together; one leads, one follows and shares food.
* Race: first to collect N of an item or reach a coordinate; announce start and winner.
* Friendly spar: duel with stated rules, stop at low health, shake hands (emote).
* Co-build: one bot gathers, another builds, coordinated by a small whisper protocol (request, offer, accept, done).
* Welcoming and goodbyes, asking for directions, group photo (gather and emote), party (fireworks, note blocks).

**Animals and farming**
* Tame and name wolves and cats; horse trails; leashing; pens and breeding programs; beekeeping and honey;
  shearing and dyeing; composting; replanting saplings and growing trees.

**Environment play**
* Climb a peak and look around, watch a sunrise, sit by a campfire and cook, picnic with a cake, boat trip down a
  river, minecart ride, bubble-column lift, ladder or water elevator, a short parkour line, lighthouse torches.

**Villagers and economy**
* Cure a zombie villager, browse the wandering trader, breed villagers with beds and food, build an iron golem,
  ring the bell, restock trades.

**Self-expression**
* An emote repertoire (wave, nod, shake head, celebrate, dance with a crouch pattern), sign art, small statues.

## 3. Recommended order

1. **Macro scenarios + traits + needs layer** (Director level, makes everything else look purposeful).
2. **Technique parameters** on the six most common goals.
3. **Interaction set A (written/decorative)**: journal, signs, name tags, item frames. Cheap, very human, low risk.
4. **Interaction set B (social protocols)**: sign shop, mailbox, treasure hunt, caravan, race, spar.
5. **Interaction set C (animals)**: tame and name pets, leash, horse trails, bees.
6. Redstone-lite toys and villager economy last (highest risk of getting stuck in game).

Each item must ship with: an `Activity` (with params and honest requires/provides), a `FeatureArea`, unit tests for
the pure parts, and a note in this file of what is unverified in game.
