Blueprint allows you to create and export GregTech:Modern multiblocks from your world into code.

It supports kubejs, java, gtm7, gtm8, and 1.21.1 and 1.20.1 (soon).

## Builder System
You can build and edit up to 14 independent shapes per multiblock and tweak values so your shapes 
look however you want.

The current shapes are disc, ring, half disc, sphere, ellipsoid, cylinder, cone, frustum, 
hyperboloid, pyramid, prism, dome, capsule, box, helix, spiral, torus knot, wave ring, star ring, 
infinity, arch, semicircle, polygon ring, line.

The current editable values are radius, size, tubes, thickness. 
Each shape has different combinations of these values. Polygon ring is a bit different as it has Sides.

You also have offset controls, per part block control, repeating aisle options, pinning blocks, 
making the actual placement override blocks, and finally sending the completed multiblock to export system!

## Exporter System
You can also export your completed build as kubejs or java multiblock data!

The export half lets you set which block is the controller, 
whether it exports kubejs or java style code, whether it exports code for gtm8 or gtm7, set I/O limits per block type, selecting an existing multiblock to then edit in build mode, and whether you want capital letters to be used before lowercase ones/vice versa.

## GregTech versions (1.20.1)

On 1.20.1 GregTech Modern comes in two API lines, and Blueprint runs with either: the older one (7.x and the 8.0 release: LDLib, `api.pattern`, `.aisle`, `FactoryBlockPattern`) and the newer 8.0 snapshots (ModularUI, `api.multiblock`, `.slice`, `MultiblockPatternBuilder`). It uses only what both have, finds the rest by reflection (custom predicates, recipe types and modifiers, block fields), and draws the hologram with the vanilla block renderer, so it needs neither LDLib nor ModularUI itself. The GregTech chip on the export page starts on the installed line (`.aisle` or `.slice`, and the imports, `Predicates.controller(...)` form and registration to match) and can be switched. On 1.20.1 `.register()` gives the definition itself, so the machine output is `public static final MultiblockMachineDefinition X = REGISTRATE...register();`.

Building: it compiles against the older line (`gtceu_version`, default 7.5.3) and runs with `./gradlew runClient` (7.x), `./gradlew runClient -Pgtm=8` (the 8.0.0-SNAPSHOT line, with ModularUI) or `-Pgtm=8.0.0` (the release). `-Pgtmcompile=<version>` compiles against another version to check it.

## Credits

The selection, controller orientation, and KubeJS export
started as the Copy Tool of Structure To KubeJs Aisles by Oktawia (MIT).
