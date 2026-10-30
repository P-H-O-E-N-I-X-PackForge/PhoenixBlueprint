# Phoenix Blueprint

Design multiblock shapes in the world, build them, and export the finished machine as GregTech Modern KubeJS or Java.
Minecraft 1.21.1, NeoForge, GregTech Modern 8 (required). Phantasia is optional and adds a 3D preview.

## The wand

Builds shapes out of the block in your other hand, with a hologram of where it will go. For designing multiblocks:
rings in any orientation, spheres, helices, knots, and several of them put together.

Creative mode or operator permission only (it builds without cost). While it is in a hand it adds 120 blocks to your reach,
so you can aim and build from a distance.

## Using it

| Action | What it does |
|---|---|
| Right-click a block | Builds the plan with its anchor on the block next to the face you clicked |
| Left-click (block or air) | Undoes the last build (the last 8 are kept) |
| Sneak + right-click | Opens the editor |
| Block in the **other hand** | The block it builds with (the hologram uses glass if there is none) |

The hologram follows where you aim and is rebuilt only when the plan changes. While the editor is open it stays where it
was last aimed, so you can watch it change as you edit.

## The editor

Drawn like the machine screens. Click a shape chip to switch the current part (the mouse wheel over the shape chips steps through
them); drag a slider or use the wheel over it (shift for bigger steps) to change a number.

A plan is one or more **parts**; each part is a shape with its own sizes, rotation and offset, and they are combined.
`+` copies the current part, `-` removes it. Three rings turned 90 degrees apart make a gyroscope.

- **Shape**: ring, disc, sphere, ellipsoid, cylinder, cone, pyramid, dome, capsule, box, helix, spiral, torus knot,
  wave ring, star ring, infinity, arch. Each names its own sizes.
- **Hollow / Wall**: a shell of that thickness (for a tube, the tube is hollow).
- **Yaw / Pitch / Roll**: any angle, in degrees (steps of 15; shift-click the buttons for 45). **Flat**, **Facing Z**,
  **Facing X** and **Tilt 45** are shortcuts.
- **Offset X / Y / Z**: where the part's centre is from the anchor.
- **No diagonal gaps** (on by default): blocks that only touch along an edge or corner get a block between them, so a thin ring
  or a hollow shell is joined face to face. Turn it off for the plain rasterised shape (thinner, but corner-to-corner).
- **Pin part / Pin all**: pins the part (or every part) at the spot you are aiming at, in the current dimension. A pinned part stays there however you aim afterwards (its offset is from the pin) and shows as a second hologram; the other parts keep following the aim, so you can fix a core in place and move the rest around it. A pinned part is marked `*` on its tab, can still be edited, and is left out (with a message) if you build in another dimension. **Unpin** lets it follow the aim again.
- **Sit on the base**: the plan's lowest block is at the anchor's height instead of its centre.
- **Overwrite blocks**: replace existing blocks (otherwise only air and replaceable blocks are replaced). Blocks with block
  entities (chests, machines) are never replaced.

Lengths count blocks; an odd length is exact (13 gives 13 blocks), an even one comes out one block longer.
A plan may cover at most 60,000 blocks.

## Where it lives

- `common/`: `ShapeKind`, `ShapeSpec`, `ShapePlan` (what is stored on the wand), `ShapeGenerator`
  (turns a plan into blocks; tested in `ShapeGeneratorTest`), `ShapeActions` (placing and undo), `BlueprintWandItem`.
- `client/ShapeHologram`: the preview, drawn by `client/render/GhostRenderer`.
- `client/gui/BlueprintScreen`: the editor, a plain vanilla screen laid out at 380 x 308 and scaled to fit the window. Changes are applied to the wand in the hand at once and sent to the server (`common/WandEdits`, `C2SWandEditPacket`), so the editor and the hologram follow as you click.

## Export mode (multiblock export to KubeJS)

The wand is also the Copy Tool from the Structure To KubeJS Aisles mod. Switch with the BUILD / EXPORT chips in the editor or
the mode key (`[`). In export mode:

| Action | What it does |
| --- | --- |
| Sneak + right-click a block | Corner 1 |
| Right-click a block | Corner 2 |
| `]` or MARK CONTROLLER | Right-clicking a block now marks the **controller** (sneak + click unmarks it) |
| Right-click air, `\` or COPY KUBEJS | Copy the selection to the clipboard |
| Sneak + right-click air | Open the editor |
| SELECT LAST BUILD | Selects the box around what the wand last built |

The selection is outlined in green (the controller in orange), also through walls, and the info above the hotbar shows the
corners and size. The output is GTM 8 `.slice(...)` and `.where(...)` lines; with a controller marked the pattern is turned to
read relative to the controller's facing and the controller is written as `K`. GTM hatches and buses become
`Predicates.abilities(PartAbility.X)` (blocks that provide the same ability share a letter). Options in the editor: letters
(a-z first or A-Z first), how air is written (space, `Predicates.air()` or a plain block), hatches as abilities or exact
blocks, and the fragment or the full `.pattern(definition => ...)` call. A result too long for the clipboard is written to
`packet.txt` in the game folder and its path is copied. The limits are 91 kinds of block and 2,000,000 blocks.

**Substructures:** a GTM 8 machine can have named patterns besides its default one (`.pattern("name", definition -> ...)`). The PARTS row on the export page holds the structures: `M` is the default, `+` adds a substructure (up to 6), `-` removes the selected one. Each has its own corners and controller. `/blueprint name <name>` names the selected substructure (the default is always `main`). Copy writes every structure that has both corners; the others of the machine are outlined in blue in the world.

**Preview:** with Phantasia installed, PREVIEW (PHANTASIA) on the export page opens the selected structure in Phantasia's 3D viewer as if it were a registered multiblock: the blocks are read from the world, drawn by Phantasia's scene renderer, and start with their `formed` flag on; its working toggle switches the blocks that have an active flag. It shows the world as it is (the controller's own structure check is not run), and a selection over 200,000 blocks is refused.

**Machine definition:** MACHINE DEFINITION on the export page sets what a full machine needs: the id (typed), rotation state, recipe types and recipe modifiers (picked from every registered one, found in the recipe type registry and in classes called `*RecipeTypes*` / `*RecipeModifiers*`), the appearance block, and the casing and overlay textures (picked in a grid of what each texture looks like, animated textures playing from their .mcmeta). The class is always `WorkableElectricMultiblockMachine`. Set the output to the full machine definition and COPY OUTPUT writes the whole registration (`REGISTRATE.multiblock(...)` in Java, `GTCEuStartupEvents.registry('gtceu:machine', ...)` in KubeJS) with every structure as a pattern. Blocks equal to the appearance block, and hatches and buses written as abilities, become one casing predicate that also allows maintenance and `autoAbilities` of the recipe types. If no appearance block is picked it falls back to the block marked as the controller, and if no casing texture is picked the model uses the texture of the appearance block (worked out by the client from its model and stored on the wand); the overlay has no fallback. A pick is applied at once and you come back to the editor; closing a picker without picking also comes back to it.

The **Output** chip cycles through just the pattern (slice and where lines), the whole `.pattern(...)` call, and the full machine definition. The **GregTech** chip picks the names: 8 writes `.slice(...)` (and `MultiblockPatternBuilder` in Java), 7 writes `.aisle(...)` with `FactoryBlockPattern` and `definition.getBlock()` for the controller.

The **Language** chip switches the output to Java for a machine definition: `'a'` char literals, `Predicates.controller(definition)`, and blocks written as their registry field (`PhoenixBlocks.X.get()`, `GTBlocks.X.get()`, found by reading those classes) with a `BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath(...))` lookup for any block that has no field there. A trailing `// imports:` comment lists what to import. Holder classes are found automatically: the block's namespace is a mod id, and that mod's classes with "Blocks" or "Casing" in their name are read for block fields.

So a design goes: shape it in BUILD mode, build it, switch to EXPORT, SELECT LAST BUILD, mark the controller, COPY.

## Export extras

- **Warnings** on the export page, checked against the blocks in the selection: no corners, no controller (or one outside the selection or on air), more than 91 kinds of block, no energy or maintenance hatch, and for the machine output no appearance block, recipe type or overlay texture.
- **SELECT FROM CONTROLLER** selects the box around everything built that touches the marked controller (not counting natural terrain: stone, dirt, sand, gravel, leaves, logs; at most 60,000 blocks and 64 from the controller), so a finished multiblock is selected in one click.
- **Saved exports:** every copy is also written to `blueprint/exports/` in the game folder as `<date>-<id>.js` or `.java` (the newest 50 are kept); OPEN EXPORTS FOLDER opens it.
- **Orientation arrows** at each marked controller show how the pattern is read: red = slices, green = rows (up), blue = characters.
- The machine output adds `.langValue("Machine Name")` made from the id.

## Limits per block

LIMITS PER BLOCK on the export page lists the kinds of predicate in the selected multiblock (each block, each group of hatches written as an ability, and the machine's casing), most common first, with how many of each there are. For each one the mode button cycles any number, at least, at most and exactly; `-` and `+` change the number (shift for 5); HATCH lets the recipe types' hatches stand in for the block (`.or(Predicates.autoAbilities(definition.getRecipeTypes()))`) and MAINT. lets one maintenance hatch (`.or(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))`). CTRL lets the controller stand there (`.or(Predicates.controller(definition))`, written the way the controller is written elsewhere in the output), as when it is built into the casing. They are written when you copy, as `.setMinGlobalLimited(n)`, `.setMaxGlobalLimited(n)` or `.setExactLimit(n)` on the predicate. The machine's casing starts with hatches and maintenance allowed; every other block starts with nothing. Limits are kept on the wand by predicate, so they apply to every structure of the machine.

## Import (editing an existing multiblock)

Copy a pattern from a KubeJS script or a Java file (the `.slice(...)` or `.aisle(...)` lines and the `.where(...)` lines, ours or hand-written), then IMPORT FROM CLIPBOARD on the export page shows it as a hologram where the wand is aimed, with the controller's spot as a gold block, facing you. TURN IT rotates it in quarter turns, BUILD IT builds it (undo with left-click), CLEAR IMPORT drops it.

What it understands: `Predicates.blocks("id")`, `Predicates.blocks(GTBlocks.X.get())` / `Blocks.X` / a bare `X.get()` (found by the same field scan the Java export uses), `ResourceLocation` lookups, `Predicates.abilities(PartAbility.X)` (shown as the lowest-tier hatch or bus), `Predicates.controller(...)`, `Predicates.air()`, `Predicates.any()`, `sliceRepeatable(min, max, ...)` (the minimum repeats). Only the first predicate of an `.or(...)` chain is used. Anything else (a `ChemicalHelper.getBlock(...)` frame, tags, auto abilities) is left out and reported. Blocks are placed with their default state, so facing and other properties are not copied.

## Credits

The selection, controller orientation and KubeJS export started as the Copy Tool of Structure To KubeJs Aisles by Oktawia (MIT).
