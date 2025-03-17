package net.commoble.hyperbox.dimension;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.blocks.ApertureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	public static final ChunkPos CHUNKPOS = new ChunkPos(0,0);
	public static final long CHUNKID = CHUNKPOS.toLong();
	public static final BlockPos CORNER = CHUNKPOS.getWorldPosition();
	public static final BlockPos CENTER = CORNER.offset(79, 79, 79);
	public static final BlockPos MIN_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(80,80,80);
	// don't want to spawn with head in the ceiling
	public static final BlockPos MAX_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(80,1,80);

	private final Holder<Biome> biome; public Holder<Biome> biome() { return biome; }

	/** get from Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get(); **/
	public static MapCodec<HyperboxChunkGenerator> makeCodec()
	{
		return Biome.CODEC.fieldOf("biome")
				.xmap(HyperboxChunkGenerator::new, HyperboxChunkGenerator::biome);
	}

	// Updated to match dimension definition
	public int getHeight() { return 128; }

	// create chunk generator at runtime when dynamic dimension is created
	public HyperboxChunkGenerator(MinecraftServer server)
	{
		this(server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Hyperbox.BIOME_KEY));
	}

	// create chunk generator when dimension is loaded from the dimension registry on server init
	public HyperboxChunkGenerator(Holder<Biome> biome)
	{
		super(new FixedBiomeSource(biome));
		this.biome = biome;
	}

	// get codec
	@Override
	protected MapCodec<? extends ChunkGenerator> codec()
	{
		return Hyperbox.INSTANCE.hyperboxChunkGeneratorCodec.get();
	}

	// apply carvers
	@Override
	public void applyCarvers(WorldGenRegion world, long seed, RandomState random, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunkAccess, GenerationStep.Carving carvingStep)
	{
		// noop
	}

	@Override
	public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structureFeatureManager, RandomState random, ChunkAccess chunk) {
		ServerLevel serverLevel = worldGenRegion.getLevel();

		ChunkPos chunkPos = chunk.getPos();
		int chunkX = chunkPos.x;
		int chunkZ = chunkPos.z;

		// Pre-initialize heightmaps for this chunk
		for (Heightmap.Types type : Heightmap.Types.values()) {
			chunk.getOrCreateHeightmapUnprimed(type);
		}

		if (chunkX >= 0 && chunkX < 10 && chunkZ >= 0 && chunkZ < 10) {
			BlockState wallState = Hyperbox.INSTANCE.hyperboxWall.get().defaultBlockState();
			BlockPos.MutableBlockPos mutaPos = new BlockPos.MutableBlockPos();

			int startX = chunkX * 16; // World X position of this chunk
			int startZ = chunkZ * 16; // World Z position of this chunk
			int endX = startX + 16;
			int endZ = startZ + 16;

			int maxHorizontal = 160;  // Box size
			int ceilingY = this.getHeight() - 1;

			// Fill each block in the chunk
			for (int worldX = startX; worldX < endX; worldX++) {
				for (int worldZ = startZ; worldZ < endZ; worldZ++) {
					// Process each column
					boolean isWall = (worldX == 0 || worldX == maxHorizontal - 1 || worldZ == 0 || worldZ == maxHorizontal - 1);

					// Set floor
					mutaPos.set(worldX, 0, worldZ);
					chunk.setBlockState(mutaPos, wallState, false);

					// Set walls
					if (isWall) {
						for (int y = 1; y < ceilingY; y++) {
							mutaPos.set(worldX, y, worldZ);
							chunk.setBlockState(mutaPos, wallState, false);
						}
					} else {
						for (int y = 1; y < ceilingY; y++) {
							mutaPos.set(worldX, y, worldZ);
							chunk.setBlockState(mutaPos, Blocks.AIR.defaultBlockState(), false);
						}
					}

					// Set ceiling
					mutaPos.set(worldX, ceilingY, worldZ);
					chunk.setBlockState(mutaPos, wallState, false);

					// Update heightmaps manually for this column
					for (Heightmap.Types type : Heightmap.Types.values()) {
						Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
						heightmap.update(worldX & 15, ceilingY, worldZ & 15, wallState);
					}
				}
			}

			// Only place apertures if this is in the correct chunk
			// Calculate CENTER chunk coordinates
			int centerChunkX = CENTER.getX() >> 4; // Divide by 16
			int centerChunkZ = CENTER.getZ() >> 4; // Divide by 16

			if (chunkX == centerChunkX && chunkZ == centerChunkZ) {
				BlockState aperture = Hyperbox.INSTANCE.apertureBlock.get().defaultBlockState();
				Consumer<Direction> apertureSetter = dir -> {
					chunk.setBlockState(mutaPos, aperture.setValue(ApertureBlock.FACING, dir), false);

					// Update heightmaps for the aperture position
					for (Heightmap.Types type : Heightmap.Types.values()) {
						Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
						heightmap.update(mutaPos.getX() & 15, mutaPos.getY(), mutaPos.getZ() & 15, aperture);
					}
				};

				int centerX = CENTER.getX();
				int centerY = CENTER.getY();
				int centerZ = CENTER.getZ();

				int west = centerX - 7;
				int east = centerX + 7;
				int down = centerY - 7;
				int up = centerY + 7;
				int north = centerZ - 7;
				int south = centerZ + 7;

				mutaPos.set(centerX, up, centerZ);
				apertureSetter.accept(Direction.DOWN);
				mutaPos.set(centerX, down, centerZ);
				apertureSetter.accept(Direction.UP);
				mutaPos.set(centerX, centerY, south);
				apertureSetter.accept(Direction.NORTH);
				mutaPos.set(centerX, centerY, north);
				apertureSetter.accept(Direction.SOUTH);
				mutaPos.set(east, centerY, centerZ);
				apertureSetter.accept(Direction.WEST);
				mutaPos.set(west, centerY, centerZ);
				apertureSetter.accept(Direction.EAST);
			}
		}
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region)
	{
		// NOOP
	}

	@Override
	public int getGenDepth() // total number of available y-levels (between bottom and top)
	{
		return 128;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk)
	{
		// Initialize all heightmaps without using section.unlock()
		for (Heightmap.Types type : Heightmap.Types.values()) {
			chunk.getOrCreateHeightmapUnprimed(type);
		}

		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public int getSeaLevel()
	{
		return 0;
	}

	@Override
	public int getMinY()
	{
		return 0;
	}

	@Override
	public int getBaseHeight(int x, int z, Types heightmapType, LevelHeightAccessor level, RandomState random)
	{
		// Calculate the heightmap value at the specified position
		boolean isWall = (x == 0 || x == 159 || z == 0 || z == 159);

		switch (heightmapType) {
			case WORLD_SURFACE:
			case WORLD_SURFACE_WG:
				return this.getHeight() - 1; // Ceiling height
			case OCEAN_FLOOR:
			case OCEAN_FLOOR_WG:
				return 0; // Floor is at y=0
			case MOTION_BLOCKING:
			case MOTION_BLOCKING_NO_LEAVES:
				return isWall ? this.getHeight() - 1 : 0;
			default:
				return 0;
		}
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random)
	{
		BlockState wallState = Hyperbox.INSTANCE.hyperboxWall.get().defaultBlockState();

		// Check if position is at the wall
		boolean isWall = (x == 0 || x == 159 || z == 0 || z == 159);

		// Generate a column of blocks
		BlockState[] states = new BlockState[this.getHeight()];

		// Fill the column appropriately
		states[0] = wallState; // Floor

		// Walls or empty space
		for (int y = 1; y < this.getHeight() - 1; y++) {
			states[y] = isWall ? wallState : Blocks.AIR.defaultBlockState();
		}

		// Ceiling
		states[this.getHeight() - 1] = wallState;

		return new NoiseColumn(0, states);
	}

	@Override
	public void addDebugScreenInfo(List<String> stringsToRender, RandomState random, BlockPos pos)
	{
		// Add hyperbox debug info
		stringsToRender.add("Hyperbox Dimension");
		stringsToRender.add("Height: " + this.getHeight());
		stringsToRender.add("MinY: " + this.getMinY());
	}

	// let's make sure some of the default chunk generator methods aren't doing
	// anything we don't want them to either

	// get structure position
	@Nullable
	@Override
	public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(ServerLevel level, HolderSet<Structure> structures, BlockPos pos, int range, boolean skipKnownStructures)
	{
		return null;
	}

	// decorate biomes with features
	@Override
	public void applyBiomeDecoration(WorldGenLevel world, ChunkAccess chunkAccess, StructureManager structures)
	{
		// noop
	}

	@Override
	public int getSpawnHeight(LevelHeightAccessor level)
	{
		return 1;
	}

	// create structure references
	@Override
	public void createReferences(WorldGenLevel world, StructureManager structures, ChunkAccess chunk)
	{
		// no structures
	}
}