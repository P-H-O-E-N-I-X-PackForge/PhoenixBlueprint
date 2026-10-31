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

## Credits

The selection, controller orientation, and KubeJS export
started as the Copy Tool of Structure To KubeJs Aisles by Oktawia (MIT).
