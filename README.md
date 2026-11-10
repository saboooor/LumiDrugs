# LumiDrugs Plugin

Build LumiDrugs with `./gradlew clean build`; the plugin JAR is `build/libs/LumiDrugs.jar`.
LumiFollow is maintained and built independently at https://github.com/saboooor/LumiFollow.

LumiDrugs Plugin is a heavily expanded survival/PvP gameplay system built around **powerful consumables**, **tolerance**, **addiction**, **withdrawal**, **overdose risk**, **cures**, **cannabis strains**, **bong/cart durability**, and **player achievements**.

---

## What’s New / Current Feature Set

Compared to a basic consumable-effects plugin, this version includes:

- Persistent, drug-specific tolerance and addiction tracking.
- Configurable overdose behavior (global, per-drug, staged, and random modes).
- Recovery systems (Suboxone, CBD, sleep-based cure logic, enchanted golden apple).
- Cannabis-focused systems:
  - Large strain registry with rarity and mutation weighting.
  - Plant growth timing controls.
  - `/strains` GUI index.
- Bong support:
  - Craftable bong item.
  - Configurable cooldown.
  - Configurable durability/uses.
- Cart durability/uses mechanic.
- Tobacco and nicotine products: `tobacco`, `nicotine`, `cigarette`, `cigar`, `chewing_tobacco`, and `vape`. All have crafting recipes, Haste/Hunger effects, tolerance, addiction, and nausea/weakness overdose penalties. Vapes provide 32 puffs by default (`drugs/nicotine.yml` → `drugs.vape.mechanics.durability-uses`).
- Lacing: hold one drug in your main hand and a different drug in your offhand, then sneak-right-click an empty or water cauldron. One offhand item is consumed and its dose is stored only as persistent item data: no name, lore, model or other visible lacing marker is added. The added drug keeps its own strain, effects, tolerance, addiction and overdose behavior. Single-use products apply both drugs when consumed; vapes/carts apply the added dose on their next puff only, preserving remaining puffs. Each item can carry one added drug; already-laced items cannot be combined again or used as additives. Flavor infusion preserves the hidden data.
- Purity: ordinary production is neutral at **90%**. Sneak-right-click an empty/water cauldron with one drug in your main hand and vanilla quartz in your offhand to refine it by **5 points**, up to **100%**, consuming one quartz. A visible purity line shows the grade; hidden lacing is never revealed. Effect duration and sale value scale by `purity / 90` (100% is about 11.1% above baseline), before dealer preference bonuses. Grade carries through crafting, brewing, cooking, and automatic crafting from the mean grade of consumed drug/harvest/intermediate slots; stack size does not weight a one-item recipe input. Flavoring, refining and lacing preserve the host's grade; an added drug retains its own hidden purity. Production refuses laced inputs that would otherwise strip their added dose. Configure `config.yml` → `purity.enabled`, `refining-ingredient` and `refining-points`; disabling the mechanic makes duration/pricing neutral and stops refining. No random rerolls or unique batch IDs are used, so equal-grade products stack normally.
- Flavored products: sneak-right-click an empty or water cauldron with one unflavored product in your main hand and a matching flavor ingredient in your offhand. One ingredient is consumed; strain, effects, and remaining puffs are preserved, including on partly used vapes/carts. `/drugs` groups flavors under each base product. Click a product to browse its flavors, open infusion instructions, or view the unflavored recipe. Ingredients: sweet berries (Berry), apple (Apple), melon slice (Melon), honeycomb (Honey), cocoa beans (Chocolate), sugar (Vanilla), glow berries (Cherry), orange dye (Citrus), kelp (Mint), brown dye (Cinnamon), harvested Coffee Beans (Coffee), purple dye (Grape), pink dye (Peach). Configure names, ingredients, and products under `config.yml` → `flavors`; reload with `/drugs reload`. `flavors: {}` disables infusion. Flavors add no crafting recipes or recipe-book entries.


  Configure this family in `drugs/nicotine.yml` and apply edits with `/drugs reload`. Tobacco products require custom Tobacco Leaves: craft seeds from wheat seeds + dried kelp, plant on hydrated farmland with light level 9+, and harvest after 600 seconds of active growth. Ordinary dried kelp cannot substitute for harvested leaves. See `/drugs growing tobacco`.
- Native Minecraft advancements + configurable triggers.
- PlaceholderAPI expansion for tolerance/effectiveness placeholders.
- Admin tooling for reloading configs, purging player data, listing drugs, and resetting overdose counters.
- Villager Farm Workers (Farm Controller automation with worker villagers).
- Hallucinogen drugs: LSD (a client-side nether-portal trip + nausea) and Salvia (a dirt-block disguise).
---

## Quick Start (Server Admin)

1. Build or download the plugin JAR.
2. Put the JAR in `plugins/`.
3. Start the server once to generate config files.
4. (Recommended) Install `FlyironDrugv1.3.1RP.zip` for custom textures.
5. Review generated config files and adjust balance if needed.
6. Use `/drugs reload` after config edits.

Plugin enable initializes one phase per server tick; commands show an initializing message until services are ready. Console logs name startup/shutdown phases and warn when a phase exceeds 50 ms. PlugMan still performs synchronous plugin unloading, class loading and final data saves, so a hot reload may pause ticks; use these timing logs and a watchdog thread dump to identify any remaining long pause.

---

## How to Use the Plugin (Gameplay + Admin Guide)

## Player Flow

1. Craft or receive a drug item.
2. Consume it to gain short-term buffs and tradeoff effects.
3. Repeated use increases tolerance; potency drops over time.
4. Continued use can cause addiction.
5. If addicted, withdrawal effects begin after the configured delay.
6. Recover with the matching cure, sleep (for configured stimulants), or golden apple reset.
7. Track your status with `/tolerance`.
8. Browse cannabis strain info with `/strains`.
9. Automate drug crop farms with Farm Controllers and worker villagers.
10. Track progression through `/drugs achievements`.


## Admin Flow

1. Give test items (including `farm-controller`) with `/drugs give`.
2. Validate config edits with `/drugs reload`.
3. Audit loaded drugs with `/drugs list`.
4. Reset problem accounts with `/drugs purge <player>`.
5. Reload overdose settings independently with `/drugs overdose reload`.
6. Clear overdose counters only with `/drugs overdose reset <player>`.

---

## Commands

## Player Commands

- `/drugs`  
  Opens the main drug menu GUI.

- `/drugs help`  
  Shows context-sensitive help based on permissions.

- `/tolerance`  
  Displays each registered drug tolerance level, max, and current potency %.

- `/strains`  
  Opens the cannabis strain index GUI.

- `/drugstatus` or `/drugs status` → Lists active drugs, withdrawal and other potion effects with remaining durations, strengths and LSD/salvia onset delays, plus an **Overdose Limits, Tolerance & Addiction** summary (current tolerance vs. max overdose limit, potency %, decay timer, overdose attempts vs. threshold, and addiction/withdrawal progress). Effects with no recorded source are labeled source unknown. Confirms you are fully sober only when no drug or potion effects remain. Tolerance and addiction alone do not count as being high.
- `/drugs achievements`  
  Explains how to open Minecraft’s Advancements screen (default key: L), then select the Drugs tab.
- `/drugs growing [crop]`  
  Opens the interactive growing guide for all crops or a specific crop.
- `/drugs purity`  
  Opens the interactive Purity & Refining guide detailing how growing conditions (hydration, light level, overhead grow lamps, nearby Composter/Moss Block, spacing) and cauldron refining increase purity up to 100%.
- `/drugs hud`  
  Toggles the live Actionbar HUD pulse showing heart rate (`BPM`), overdose limit alerts (`[Near OD]` / `[⚠ OD LIMIT]`), active high countdowns, and withdrawal timers.
- `/cartel locate` (or `/cartel hideout`)  
  Reveals the coordinates of your active Cartel Hideout (generated in a random cave or shaded location), points your compass there, and extends the travel timer so you can cut out the enforcer middleman and deliver the demanded drug directly for **3x the money**.

## Admin Commands

- `/drugs give <player> <drugId|cureId|bong|farm-controller|all> [amount]`
- Gives one specific item type or all registered types.

- `/drugs purge <player>`  
  Resets all tolerance and overdose counts for a player.

- `/drugs reload`  
  Reloads plugin configs and registries.

- `/drugs list`  
  Prints all loaded registered drugs and lore in chat.

- `/drugs overdose reload`  
  Reloads overdose settings file.

- `/drugs overdose reset <player>`  
  Resets overdose attempt counters for a player.

- `/drugs achievements toggle`  
  Admin helper command that instructs editing `achievements.yml` (`enabled`) and then using `/drugs reload`.

---

## Permissions

- `drugs.menu` (default: true)
- `drugs.give` (default: op)
- `drugs.tolerance` (default: true)
- `drugs.strains` (default: true)
- `drugs.achievements` (default: true)
- `drugs.admin.reload` (default: op)
- `drugs.admin.purge` (default: op)
- `drugs.admin.list` (default: op)
- `drugs.admin.achievements` (default: op)
- `drugs.admin.overdose` (default: op)

---

## Included Drug Categories

- Opioids / high-risk: `heroin`, `fent`, `glue`
- Stimulants / combat-mobility: `cocaine`, `meth`, `molly`
- Cannabis variants: `blunt`, `joint`, `edible`, `cart`
- Hallucinogens / psychedelic: `shrooms`, `lsd`, `salvia`

Each drug is configured in its family file under `drugs.<id>`. Its `item`, `recipe`, `tolerance`, `addiction`, `overdose`, `hallucinations`, and `mechanics` settings stay together. Omitted tolerance settings use the shared defaults.

---

## Configurable hallucinations

Assign named hallucinations under `drugs.<id>.hallucinations` in any family file. Names are arbitrary, so the same effect type can be used more than once. No entry means no hallucinations; use `hallucinations: {}` to remove all assignments or `enabled: false` to disable one. `/drugs reload` applies changes to subsequent uses; running effects finish with their original settings.

```yaml
drugs:
  nicotine:
    # Keep the drug's item, recipe, addiction, and other settings alongside this.
    hallucinations:
      strange-blocks:
        type: psychedelic
        chance: 0.25
        duration-seconds: 30
        delay-seconds: 2
        interval-ticks: 20
        blocks: [AMETHYST_BLOCK, SHROOMLIGHT, WARPED_NYLIUM]
      stalking-mob:
        type: phantom
        near-tolerance-limit: true
        chance: 0.5
        duration-seconds: 4
        mobs: [ENDERMAN, CREEPER]
        speed: 0.32
```

| Type | Behavior and options |
| --- | --- |
| `psychedelic` | Private block morphs, sky shifts, and particles; `blocks` chooses the palette and `interval-ticks` sets the pulse interval. |
| `paranoia` | Shadow figures and private stalking mobs; configure `interval-ticks`, `shadow-chance`, `phantom-chance`, `mobs`, `mob-lifetime-seconds`, and `speed`. |
| `phantom` | One private stalking mob on activation; configure `mobs`, `speed`, and `duration-seconds`. At most two hallucination mobs are active per player. |
| `portal` | A fake block follows the player, with nausea; the first `blocks` entry selects the block (default `NETHER_PORTAL`). |
| `block-disguise` | The original Salvia effect: hides the player and follows them with a block display; the first `blocks` entry selects the disguise (default `DIRT`). |

All types support `enabled` (default true), `chance` (0–1 per use, default 1), `duration-seconds` (1–3600, default 40), and `delay-seconds` (0–3600, default 0). `near-tolerance-limit: true` requires tolerance within one level of that drug’s maximum. `scale-with-potency: true` scales the duration with drug potency/tolerance (minimum five seconds). `interval-ticks` is 1–1200; `speed` is 0–2 blocks per stalking step. `mob-lifetime-seconds` is 1–60 (default 4), and shadow/phantom chances are 0–1. Block palettes must contain block materials; mob lists must contain spawnable mobs (players, non-mob entities, Withers, and Ender Dragons are excluded). Invalid values report the drug and entry name.

Defaults preserve psychedelic visuals and paranoia for shrooms, LSD, and Salvia; meth paranoia; LSD's delayed portal; Salvia's delayed disguise; and high-tolerance phantoms for every bundled drug. The administrator psychosis command remains independent of drug assignments.

---

## Recovery / Cure System

Defined under `cures` and `recipes` in the relevant drug family file:

- **Suboxone**: cures configured opioid-related addictions.
- **CBD**: cannabis-focused cure behavior.
- **Sleep**: configured as cure logic for selected drugs.
- **Enchanted Golden Apple**: full addiction reset behavior.

Withdrawal behavior (effect types, timings, point decay) is per-drug and configurable.

---

## Cannabis Systems

- `drugs/cannabis.yml` contains the strain tree, rarity, mutation weights, and cannabis growth settings.
- Each family’s `crops` section controls its ingredient plant models and active growth times.
- `config.yml → bong` controls bong enable state, base-drug behavior, cooldown, and bong item settings.
- Cart durability is in `drugs/cannabis.yml` → `drugs.cart.mechanics`; bong durability is in `config.yml` → `bong`.

---

## Villager Farm Workers

Automated drug crop farming is available through Farm Controllers:

- Craft/place a **Farm Controller (barrel)** and right-click it to open its management dialog.
- Define farm region using a selection wand (max region size: **16x16**).
- Start/Stop the farm from the controller dialog.
- Purchase work contracts with emeralds and assign up to **5 villagers** by holding a contract in your main hand and right-clicking a villager. Each contract is already linked to its farm; you do not need to look at the controller.
- Assigned villagers are set to **Farmer** profession and must be reachable to contribute output.
- Workers harvest mature cannabis, poppy, mushroom, ephedra, coca, sassafras, ergot, salvia, tobacco, and coffee crops. Plant the initial crops yourself; workers reuse harvested seeds within each cycle. All crops, including cannabis, need hydrated farmland and light level 9+ to grow. Use **Set Output Chest** in the controller dialog, then right-click a chest, to store harvests and unused seeds (double chests supported). **Clear Output Chest** restores drops at the controller. Overflow or output for a missing/unloaded chest drops at the controller. Players can toggle **Drop Chest Overflow: ON/OFF** in each farm controller menu. OFF discards chest overflow instead of dropping it; the choice survives restarts and moving the controller; missing/unloaded chests still drop output at the controller. The chest link survives restarts and remains at the same location when moving the controller. Vanilla wheat is left untouched.
- New controllers are barrels; existing composter controllers remain usable. Breaking a controller drops a barrel item containing its farm ID, area, worker links, and start/stop setting. Workers that cannot be found as living, valid villagers when the controller is placed again are removed (including workers in unloaded chunks). The farm pauses until placed again; its area moves by the same offset as the controller. Contracts remain valid while packed. Packed farms survive server restarts. Contracts for genuinely missing farms are exchanged for one emerald each when the player joins or an invalid contract is used.
- Dead/invalid villagers are removed from farm assignment data automatically.

Worker throughput scales by reachable workers per cycle (1 to 5 workers), based on `config.yml` farm settings.

---

## Overdose System

Each drug’s `overdose.effects` defines its overdose outcome. Shared `config.yml` → `overdose` supports:

- Global enable/disable.
- Attempt threshold.
- Per-drug vs global tracking mode.
- Attempt expiry windows.
- Broadcast toggles.
- Default fallback effects.
- Drug-specific effects take priority over shared default, staged, and random pools.
- Optional staged progression and random effect pools.

All 21 bundled drugs have explicit overdose effects. Cannabis, nicotine, and psychedelic products use status penalties; stimulants and inhalants can also deal damage. Opioids and caffeine products include fatal outcomes. These are configurable game effects.

---

## Achievement System

Drug milestones appear in the Drugs tab of Minecraft’s native Advancements screen, with vanilla unlock toasts and server-managed progress. Progress is stored in native advancements; Configure the system in `achievements.yml`: top-level `enabled` and `notifications` control the system and native announcements. Each achievement section contains its own `enabled`, title, description, icon and trigger settings.

The `notifications.chat_message` setting controls vanilla chat announcements, and `notifications.sound` controls native unlock toasts (including their sound). Achievement announcements use Minecraft’s native formatting.

Supported trigger styles include first use, all drugs used, max tolerance behavior, overdose outcomes, using addiction cures, hitting bongs, planting and harvesting crops (including harvesting all crop types), mutating and harvesting rare cannabis strains, automating farms (placing controllers and assigning workers), crafting drugs, refining purity (including reaching 100% purity), infusing flavors, lacing drugs, and cartel activities (street sales, fulfilling enforcer demands, and completing supply contracts).

---

## PlaceholderAPI Support

If PlaceholderAPI is installed, this plugin registers `%drugs_...%` placeholders:

- `%drugs_<drugid>%` → current tolerance
- `%drugs_<drugid>_max%` → max tolerance
- `%drugs_<drugid>_effectiveness%` → potency percentage

---

## Files and Configuration Map

| File | Contents |
| --- | --- |
| `drugs/cannabis.yml` | Joint, blunt, cart, edible, cannabis crops, strains, and CBD |
| `drugs/opioids.yml` | Heroin, fentanyl, poppy, and opioid cures |
| `drugs/stimulants.yml` | Meth, cocaine, molly, caffeine, energy drinks, and their crops |
| `drugs/psychedelics.yml` | Shrooms, LSD, salvia, mushrooms, and ergot |
| `drugs/nicotine.yml` | Tobacco, nicotine, cigarettes, cigars, chewing tobacco, vapes, and tobacco crops |
| `drugs/inhalants.yml` | Glue |
| `config.yml` | Shared farm, bong, purity, flavor, HUD, chat, addiction, tolerance, and overdose policy; shared item recipes |
| `prices.yml` | Drug sale prices and strain price overrides |
| `cartel.yml` | Citizens integration, enforcer encounters, and supply contracts |
| `achievements.yml` | Achievement definitions, toggles, and native notifications |
| `farms.yml` | Saved farm controller, region, and worker data |

Drug prices belong to `prices.<id>` in `prices.yml`. Strain price overrides are under `strain-prices.<drug>.<strain>` in the same file. Shared tolerance defaults are at `config.yml` → `tolerance.default`; addiction and overdose global settings are at `addiction.settings` and `overdose.settings`.

Run `/drugs reload` to apply family and shared settings, including cartel prices. Family files are installed on first startup. Existing family files are preserved; intentionally deleted family files are not recreated. Custom family `.yml` files are read from the same directory; duplicate drug IDs are rejected with the source filename. The former mechanic-specific files are no longer read or generated.

Example from `drugs/opioids.yml`:

```yaml
drugs:
  heroin:
    item:
      material: DRIED_KELP
      display-name: "&8Black Tar Heroin"
      effects: {}
    recipe:
      shape: [" S ", "GAG", " B "]
      ingredients: {S: POPPY, G: GUNPOWDER, A: WITHER_ROSE, B: CHARCOAL}
    tolerance:
      max-tolerance: 4
      decay-delay-minutes: 4
    addiction:
      addictive: true
      points_per_use: 1
      addicted_at_points: 1
    overdose:
      effects:
        - type: death
          message: "&4%player% died from a heroin overdose!"
crops:
  poppy:
    plant-block: POPPY
    growth-seconds: 600
```

---

## Compatibility

- API version: `1.21`
- Soft dependency: PlaceholderAPI

---

## Credits

- Original base plugin: **Norses Drugs**
- Fork + continued systems work/balance: **Flyiron**
- Fork + extra features for player interaction: **LumiDrugs**

## Growing crafting ingredients

Right-click an empty or water cauldron with vanilla resin clumps in your main hand to turn one clump into one Processed Resin. Brew Processed Resin with water bottles in a brewing stand (with blaze powder fuel) to make Glue. Ordinary sugar and raw resin clumps cannot substitute for Processed Resin. Resin is no longer a custom crop.

Crop-based drug recipes require custom harvested ingredients. Ordinary flowers, minerals, vines,
ferns, and planting stock cannot substitute for a harvest, including in automatic crafters.
Each drug’s family `recipe` section must include its required harvested crop.
Use `/drugs growing` to open the crop guide dialog, or `/drugs growing <crop>` for a specific crop. Click a drug in `/drugs` to view its current recipe, then click a harvested ingredient to open its growing dialog. The guide includes a Back to recipe button.

Craft one crop seed by combining wheat seeds with the starter below (shapeless).
Right click hydrated farmland to plant. Crops use distinct plant blocks configured by `crops.<id>.plant-block` in the family file. Right-click empty-handed to see a progress bar, percentage, remaining active growth time, and any paused conditions. Growth pauses in unloaded chunks, dry soil, or light below 9.
Break a fully mature crop to receive two harvested ingredients and one reusable seed.
Breaking early returns only its seed. Bone meal cannot skip growing.
Every mature crop, including cannabis, has a 10% chance to return one extra seed or
planting fern. This applies to player harvests and farm workers; immature plants never give bonus seeds.

| Crop | Starter | Used for | Default growth time |
| --- | --- | --- | --- |
| Poppy | Poppy | Heroin, fent | 10 minutes |
| Mushroom | Red mushroom | Shrooms | 8 minutes |
| Ephedra | Quartz | Meth | 15 minutes |
| Coca | Vine | Cocaine | 15 minutes |
| Sassafras | Magenta dye | Molly | 15 minutes |
| Ergot | Spore blossom | LSD | 20 minutes |
| Salvia | Lilac | Salvia | 10 minutes |

Craft a cannabis plant with 1 wheat seeds + 1 fern (shapeless), then plant it on
hydrated farmland. Ordinary ferns cannot be planted directly as cannabis. Wait for it to mature into a large fern and harvest with shears. Mature plants
yield two Cannabis Buds plus one strain cannabis plant for replanting. Joint, blunt, edible, cart, and CBD recipes require buds, and bongs accept buds.
Strain inheritance and mutations remain supported. Farm workers replant planting stock
and deliver the harvested buds at their controller.

Ingredient crop progress is saved in `ingredient_plants.yml` on planting, harvesting,
every minute, and shutdown. Configure crop durations in each family’s `crops` section; `/drugs reload`
applies changes. Cannabis uses the same saved growth timer, percentage readout, water/light checks, and farm worker path as other crops. Set `crops.cannabis.growth-seconds` in `drugs/cannabis.yml` (600 seconds by default).
Finished items remain available through the existing admin give command.

## Cartel NPC Buyers

Install **Citizens** (a build compatible with your Minecraft server), **Vault**, and an economy provider such as **EssentialsX Economy**, then restart the server. Buyers link to NPCs managed through Citizens; temporary enforcers use the signed skin configured in `cartel.yml` → `npc.skin`. Other drug features work without Citizens; cartel NPCs require it.

- `/cartel link <npc-id>` — link an existing Citizens NPC as a buyer.
- `/cartel unlink <npc-id>` — stop treating that NPC as a buyer; keep the NPC.
- `/cartel reload` — reload `prices.yml` and cartel settings.

Players **throw drug items within two blocks of the buyer** to sell the dropped stack. Right-clicking the buyer explains how to deliver; there is no selling menu. Create an NPC with Citizens, then run `/cartel link <npc-id>`. Move it, set its name and skin, or remove it through Citizens. The buyer link persists in Citizens metadata across restarts. Existing tagged buyers remain linked automatically. LumiDrugs does not create, respawn, teleport, rename, reskin, or delete linked buyers. Players must remain near the buyer to sell.

Review `prices.<id>` in `prices.yml` before opening sales. Missing, zero, negative, or non-finite prices disable a drug's sales. Optional `strain-prices.<drug>.<strain>` entries in `prices.yml` override its base unit price. Only registered drug items carrying the plugin's metadata are accepted; renamed vanilla items, planting stock, and partially used cartridges are rejected. Cartridges have no default sale price. Failed economy deposits leave the dropped items untouched on the ground. Only genuine player drops are accepted; tracked drops cannot merge with other stacks, preserving the seller attribution.

Each buyer now has a **rotating preference**. Right-click it to see the preferred drug (and optional strain), payout multiplier and time until rotation. The default is **1.5× the normal price**, rotating every **24 hours** on fixed time boundaries. Different buyers use their stable NPC UUID to select offers; restarting does not reroll them. Other products retain their normal prices. Preference bonuses apply after strain price overrides and never enable a disabled/unpriced item. Enforcer delivery contracts retain their agreed normal prices.

Configure `cartel.yml` → `preferences.enabled`, `rotation-hours` (1–8760), `price-multiplier` (finite and at least 1), and optional `preferences.strains.<drug>` lists of existing cannabis strain IDs. Leave `strains: {}` for drug-wide preferences. Only registered drugs with a positive finite base price enter the rotation. Run `/cartel reload` after edits; older configs use the defaults above.

Permissions: `drugs.cartel.sell` defaults to everyone; `drugs.cartel.admin` defaults to operators. NPC creation requires Citizens. Keep its NPC data when backing up the server. Daily limits and bulk delivery bonuses are not part of this version.

### BeautyQuests setup for cartel enforcers

Enforcers now require **BeautyQuests 2.0** (compiled and tested against its official release)
in addition to Citizens, Vault and an economy provider. BeautyQuests is an optional plugin
dependency for LumiDrugs as a whole: without it, enforcer encounters are disabled, while
stationary buyers and other drug features remain available.

1. Install BeautyQuests, LumiFollow, Citizens, Vault and your economy provider, then restart the server.
2. Leave `enforcer.beautyquests-quest-id: -1` in `plugins/LumiDrugs/cartel.yml`
   (this is also the default for existing configs without that setting).
3. Test with `/cartel enforcer`. No quest import or ID lookup is needed.

LumiDrugs creates `plugins/BeautyQuests/quests/lumidrugs-cartel.yml` automatically before
BeautyQuests loads its quests. It chooses an unused ID starting at 9000 and reuses that
file on future starts, preserving edits to its name, prerequisites and rewards. The selected
ID is printed in the startup log; use `/quests edit <id>` to customize it. A setup error is
logged clearly and disables encounters until fixed. The managed quest gains soliciting settings once, with its original saved as
`lumidrugs-cartel.yml.pre-soliciting.backup`; existing explicit settings are preserved.

For everyday tuning, change the quantity range, spawn chance, cooldown and delivery deadline
in `cartel.yml`, then use `/cartel reload`. Movement is controlled by the quest stage's
`options.lumi-follow` section; install the
standalone LumiFollow plugin for following, climbing and recovery. LumiDrugs only supplies
the NPC entity UUID as quest data and consumes LumiFollow solicitation status. It does
not bundle LumiFollow; the quest configuration selects the movement and solicitation provider. Old
`enforcer.wall-climbing` and `enforcer.open-doors` keys in `cartel.yml` are ignored; move any
custom settings into the quest option. See [LumiFollow setup](https://github.com/saboooor/LumiFollow#readme).

Advanced: to use a different quest, set `enforcer.beautyquests-quest-id` to its ID and run
`/cartel reload`. The quest must be valid, repeatable and cancellable, with individual player
progression, exactly one **Cartel delivery** regular stage, one branch and no ending stages.
The [example quest](examples/beautyquests/9000.yml) remains available for manual setup.
Set the ID back to `-1` and restart to return to automatic setup.

Players may also start this quest from the BeautyQuests menu: it sends an enforcer with a
random demand, subject to the normal enforcer cooldown, capacity and player eligibility.
Failed spawning cancels the quest with an explanation. Automatic and admin-triggered
encounters respect BeautyQuests start requirements, repeat timers and quest limits; the
player needs `beautyquests.start` as well as `drugs.cartel.sell`.

Use BeautyQuests to edit prerequisites and completion rewards. Drug-sale payments still
come from Vault at the existing cartel prices, so extra quest rewards are optional. Avoid
start/cancel rewards for this repeatable encounter: called-off or failed spawns can cancel
it without a delivery. Stage validation requirements are checked at the end; if they fail,
the sale remains paid but the quest is cancelled without a completion reward. Prefer start
requirements for gating access to a demand.

Expiry or rejection cancels the quest and keeps the existing refusal consequences. Death,
quit, reload, failed approach, cancelled quest or unavailable economy calls off the encounter
without counting a refusal. Successful partial sales remain paid. Temporary NPCs and delivery
progress do not resume after a restart; an abandoned saved cartel quest is cancelled on login.
Restart after adding/removing either plugin; runtime plugin hot-loading is unsupported.

### Roaming cartel enforcers

Cartel enforcers can spawn on nearby walkable ground, walk toward a survival/adventure player, and demand a specific drug quantity. Each encounter chooses a **random priced drug and a random quantity (8–32 by default)**, with a 90-second deadline. The timer begins when the enforcer gets close enough to speak, with warnings every 15 seconds. Throw the requested drug within two blocks of your enforcer to deliver it at the configured cartel prices, including strain price overrides. Smaller deliveries accumulate toward the demand and are paid as accepted. Excess items remain on the ground; wrong drugs and other players' drops do not count. Partial deliveries already sold remain paid even if the encounter later expires or is called off. Ordinary buyer sales do not count toward this demand.

Use `/cartel enforcer` to test a random demand on yourself, or `/cartel enforcer <player> <drug> <amount>` to choose the player, registered drug ID, and quantity. Both commands require `drugs.cartel.admin`. Manual encounters ignore the player's cooldown and the automatic-spawning toggle, but still require an eligible player, working economy, positive base price, safe spawn location, a startable configured BeautyQuests quest, and no existing encounter for that player.

Configure the `enforcer` section in `cartel.yml`, then run `/cartel reload`. Automatic checks default to every 300 seconds, with a 25% chance per eligible player, a 30-minute per-player cooldown after each encounter, and at most eight active NPCs. `min-amount` and `max-amount` set the inclusive random quantity range (clamped to 1–2304). Drugs are chosen uniformly from registered drugs with positive, finite base prices; missing or disabled prices exclude them. The chosen drug and quantity stay fixed for that encounter. Explicit admin commands still accept a chosen drug and quantity. Set `enabled: false` to disable automatic encounters. Existing config files use the same defaults when the section is missing; copy the `enforcer` section from the bundled [cartel.yml](src/main/resources/cartel.yml) to expose all settings.

Choose **Reject demand** in the enforcer dialogue, or run `/cartel reject`, to refuse immediately and make the enforcer leave. Rejection counts exactly like an expired demand: it adds one refusal, applies the configured failure consequences, and starts the normal cooldown. Partial deliveries already paid remain paid. Closing the dialogue does not reject a demand. This command only affects your own encounter and does not require admin permission.

Each expired demand adds one consecutive refusal for that player. At **15 refusals**, the enforcer uses angrier dialogue and caps new delivery deadlines at **45 seconds**; at **20**, he becomes furious and caps them at **30 seconds**. Completing a demand resets the streak; partial deliveries do not. Called-off encounters neither add to nor reset it. Streaks persist in player data across reconnects and server restarts. Configure thresholds and deadline caps under `enforcer.aggravation`, and dialogue with the `angry-*` / `furious-*` keys under `enforcer.messages`. `{refusals}` shows the current streak.

Use `{remaining}` in warnings for the quantity still needed. Customize the arrival, demand, warning, failure, and success dialogue under `enforcer.messages`. Failure produces threats only by default. Optional `failure-damage` is measured in HP, applies only within six blocks, and is capped at 20 HP and the player's health minus one before normal damage events/modifiers. Set it to `0` for dialogue only. Grant `drugs.cartel.enforcer.bypass` to exempt a player (not granted to operators automatically).

Enforcers use Citizens player NPCs and navigation. They cancel without a penalty if they cannot reach the player before the approach timeout, the player dies/disconnects/travels to another world or far away, or the economy becomes unavailable. Reloading cartel settings calls off active demands. NPCs also clean up on chunk unload and plugin shutdown; enforcers and cooldowns are temporary and reset on server restart. Stationary cartel buyers remain persistent.

### Cartel NPC skins

Linked buyer skins are configured directly through Citizens and remain unchanged on `/cartel reload`. Temporary enforcers use the bundled signed camouflage-style skin by default (a snapshot of the public Minecraft skin for `ElChapo`). It does not require a skin lookup at spawn. To use another Minecraft account's skin, add this to `cartel.yml` and run `/cartel reload`:

```yaml
npc:
  skin-name: "MinecraftUsername"
```

Leave `skin-name` blank to use the signed texture under `npc.skin` in `cartel.yml`. Reload calls off active enforcer demands; subsequent enforcers use the updated skin. Username-based skins require Citizens to fetch the account's skin from Mojang.

If `/cartel enforcer` cannot start, its error identifies the failed requirement: player mode/permissions, an existing encounter, pricing, economy, NPC provider, safe ground, or NPC spawning. For a Citizens spawn failure, check the console for the associated spawn rejection or exception. Enforcers now attempt navigation without rejecting a new NPC on Citizens' preliminary path check; unreachable players are handled by the approach timeout.

Farm data saves to `farms.yml` and farm settings are under `config.yml` → `farm`.

## Java source layout

Production code lives in `src/main/java/com/drugs`, grouped by feature: `farm`, `growing`, `strains`, `bong`, `cartel`, `drug`, `items`, `recipes`, `addiction`, `tolerance`, `overdose`, `effects`, `achievements`, `status`, `commands`, `ui`, `config`, `integration`, and `bootstrap`. Tests mirror these packages under `src/test/java/com/drugs`. `DrugsV2` remains at the root as the plugin entry point.

## Quest-controlled NPC following and dialogs

Cartel enforcer movement and cartel NPC click dialogs (`Cartel enforcer` and `Cartel buyer`) are owned by the independent [LumiFollow](https://github.com/saboooor/LumiFollow#readme)
plugin. LumiDrugs manages the delivery objective
and publishes its NPC entity UUID along with `{drug}`, `{amount}`, `{delivered}`, and `{remaining}` stage data. LumiFollow owns solicitation approach, arrival, and NPC click dialogs (`dialog: cartel-enforcer` on the quest stage option and `cartel-buyer` for linked buyer NPCs);
LumiDrugs consumes `APPROACHING`, `READY`, and `TIMED_OUT` from quest data. It does not bundle LumiFollow. Install
`LumiFollow.jar` from the [LumiFollow repository](https://github.com/saboooor/LumiFollow) to enable movement and NPC dialogs.
LumiFollow depends on Citizens and BeautyQuests and can be used without LumiDrugs.


### Overdose-risk warnings

Players receive private chat warnings when they approach the tolerance limit, reach
it, or develop withdrawal. A dangerous new dose warns again, with a one-second duplicate
guard for laced items. Active risk/withdrawal reminders repeat every 60 seconds by
default; escalating risk warns immediately. Messages do not name hidden added drugs.
Withdrawals warn that temporary relief from another dose is not proof that further use
is safe. The existing overdose calculation is unchanged. Use `/drugstatus` for current
effects. Configure `config.yml` → `overdose-warnings.enabled` and `reminder-seconds`.


### Withdrawal recovery timers

Withdrawal countdowns now show the actual time until addiction naturally falls below
the configured threshold. They decrease as time passes and are not restarted by the
withdrawal heartbeat or milk reapplication. Naturally decaying withdrawal ends once
that threshold is crossed. Drugs with `decay.enabled: false` (or no positive decay
rate) instead display **until treated (no natural recovery)**; their potion effects
use a true infinite duration.


## Production learning quests and voluntary supply contracts

With BeautyQuests 2.0+ installed, choose **Learn to make this** in an enforcer's dialogue,
use `/drugs learn <product>`, or open the **Production Journal** in `/drugs`. The journal
is also available through `/drugs journal`.

Each player can keep one learning quest and one supply contract at a time. Learning
quests follow the product's planting stock, harvest, processing, brewing, and assembly
chain. The journal offers the next recipe and growing guide. Progress checks the custom
items in the player's inventory, so existing supplies skip earlier steps. Tutorials
never consume items and have no deadline or extra item/currency rewards.

For example, the vape learning chain is Tobacco Seeds → Tobacco Leaves → Nicotine →
Nicotine Vape Liquid → Vape. Cannabis starts by crafting a cannabis plant from one
wheat seed and one fern; the journal links its recipe and growing instructions.

Learning quests are independent of an enforcer encounter: starting one does **not** pause
or extend an active enforcer's deadline. They remain available after that encounter ends,
after death, and across reconnects and restarts. Abandon a lesson in the journal without
adding to the cartel refusal streak. Cancelling it in BeautyQuests also clears the journal entry.

Right-click a stationary buyer and choose **Choose supply contract**, or use
`/drugs contracts`. Pick a registered drug with an enabled positive base price. The
bundled defaults require **16 items within 24 real hours**, allowing time for growing
and production. Deliver by throwing the selected product near any stationary buyer.
Only successful paid sales made after acceptance count; failed payments, other products,
and enforcer deliveries do not count. Partial sales accumulate, and extra items sell normally.
The normal sale prices, purity scaling, and applicable buyer preferences still apply;
there is no separate contract bonus. Existing stock is allowed.

The supply deadline keeps running while offline. Expiry and abandonment carry no refusal
penalty and do not undo paid sales. `/drugs journal` shows progress, time remaining, and
abandon buttons. The journal persists in `production-journal.yml`; it is separate from
temporary enforcer NPC state. Permission `drugs.quests` defaults to true; supply contracts
also require `drugs.cartel.sell`.

LumiDrugs registers the `lumidrugs-production-learning` and `lumidrugs-supply-contract`
stage types before BeautyQuests loads its saved quests. It automatically creates
`plugins/BeautyQuests/quests/lumidrugs-learning.yml` and `lumidrugs-supply.yml`, choosing
unused IDs starting at 9100. Existing quest files and admin edits are preserved. Both
quests require individual progression, repeatable/cancellable settings, and their original
single stage. BeautyQuests start limits and stage requirements are respected. Fully restart
after installing the update; plugin hot reloads do not register saved quest types reliably.
Without BeautyQuests, the journal can display choices but starting a quest explains the
missing dependency; ordinary drug and buyer features remain usable.

Configure `cartel.yml` → `supply-contracts.enabled`, `amount` (1–2304), and
`deadline-hours` (1–168); `/cartel reload` applies changes to new contracts. Accepted
contracts keep their original amount and deadline. Turning off new contracts does not
cancel accepted ones.

Cartel delivery quests must enable `options.lumi-follow.soliciting: true` with
`npc-data: npc` and `status-data: solicitation`. Set `solicitation-radius: 3.5`
and `approach-timeout-seconds: 60` there to tune the approach. The previous
`enforcer.approach-timeout-seconds` setting in `cartel.yml` is ignored. The managed
quest is upgraded automatically; custom quests must add these settings themselves.
LumiDrugs still owns demand selection, dispatch frequency, delivery deadlines,
payments, warnings, and refusal consequences. Approach timeout cancels without a
refusal penalty. Quests configured with the LumiFollow option need its updated JAR and a server restart.
