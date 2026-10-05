# Arsenic - The Unified Optimisation

## GREEDY MESHING - PLAIN TEXT EXPLANATION

### WHAT IT DOES
Vanilla Minecraft draws one quad (two triangles) for every visible face of every block. In a flat 16x16 area of grass, that's 256 separate quads all using the same texture, same orientation, same lighting. Greedy meshing merges all those identical adjacent faces into one giant quad with a repeating texture. The GPU sees one draw call instead of 256.

### THE ALGORITHM

#### Step 1 - Sweep along one axis
Pick one axis, for example the X axis. For each 16x16 slice of the chunk along that axis, build a 2D mask. This mask tracks which faces are visible and still need to be meshed.

#### Step 2 - Walk the mask
Scan through the mask looking for runs of identical faces. "Identical" means same block type, same facing direction, and same lighting.

#### Step 3 - Greedily expand
When you find a run, expand it in one direction until you hit a face that doesn't match. Then expand in the perpendicular direction as long as every row in that direction also matches. You end up with the largest possible rectangle of identical faces.

#### Step 4 - Emit one quad
Create a single quad for the rectangle you just built. Mark every cell in that rectangle as done so you never mesh them again.

#### Step 5 - Repeat
Keep walking the mask until every visible face has been consumed. Then repeat the whole process for all 6 face directions: positive and negative X, positive and negative Y, positive and negative Z.

### THE KEY DATA STRUCTURE
A 2D mask array. It tracks which faces are visible and not yet meshed. As you consume faces into merged quads, you erase them from the mask. This guarantees you never mesh the same face twice.

### THE HARD PARTS

#### Texture tiling
A merged quad needs a texture that repeats across its surface. Vanilla's terrain shader assumes one block per quad. You need a custom shader that takes the quad's size and tiles the texture accordingly.

#### Lighting seams
Minecraft's smooth lighting interpolates per-vertex. A merged quad only has 4 vertices, so you lose the intermediate lighting data. Merged quads end up with flatter lighting than vanilla.

#### Random texture rotation
Grass blocks randomize their top texture rotation per-block. Greedy meshing forces a single orientation across the merged quad, so you lose that variation.

#### Transparency
Water, glass, and other transparent blocks can't merge with opaque faces. You need a second pass for transparent geometry with separate shaders.

### IMPLEMENTATION PATH

Start with opaque full-cube blocks only. Grass, stone, dirt, planks. Skip water and glass for now.
Find the vanilla chunk meshing method in LevelRenderer or RenderChunk. That's your Mixin target.
Write the 2D mask algorithm for one face direction. Get it working for a flat plane of stone.
Write a custom terrain shader that tiles textures based on quad size.
Add the remaining 5 face directions.
Handle lighting. Accept that merged quads will have flat lighting, or figure out how to pass lighting data through.