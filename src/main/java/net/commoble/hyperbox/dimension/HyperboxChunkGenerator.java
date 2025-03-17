package net.commoble.hyperbox.dimension;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import javax.annotation.Nullable;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;

import net.commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.jetbrains.annotations.NotNull;

public class HyperboxChunkGenerator extends ChunkGenerator
{
	public static final ChunkPos CHUNKPOS = new ChunkPos(0,0);
	public static final long CHUNKID = CHUNKPOS.toLong();
	public static final BlockPos CORNER = CHUNKPOS.getWorldPosition();
	public static final BlockPos CENTER = CORNER.offset(79, 79, 79);
	public static final BlockPos MIN_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(80,80,80);
	public static final BlockPos MAX_SPAWN_CORNER = HyperboxChunkGenerator.CORNER.offset(80,1,80);

	private final Holder<Biome> biome; public Holder<Biome> biome() { return biome; }

	public static MapCodec<HyperboxChunkGenerator> makeCodec()
	{
		return Biome.CODEC.fieldOf("biome")
				.xmap(HyperboxChunkGenerator::new, HyperboxChunkGenerator::biome);
	}

	public int getHeight() { return 128; }

	public HyperboxChunkGenerator(@NotNull MinecraftServer server)
	{
		this(server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Hyperbox.BIOME_KEY));
	}

	public HyperboxChunkGenerator(Holder<Biome> biome)
	{
		super(new FixedBiomeSource(biome));
		this.biome = biome;
	}

	@Override
	protected @NotNull MapCodec<? extends ChunkGenerator> codec()
	{
		return Hyperbox.modInstance.hyperboxChunkGeneratorCodec.get();
	}

	@Override
	public void applyCarvers(@NotNull WorldGenRegion world, long seed, @NotNull RandomState random, @NotNull BiomeManager biomeManager, @NotNull StructureManager structureManager, @NotNull ChunkAccess chunkAccess, GenerationStep.@NotNull Carving carvingStep)
	{
	}

	@Override
	public void buildSurface(@NotNull WorldGenRegion worldGenRegion, @NotNull StructureManager structureFeatureManager, @NotNull RandomState random, @NotNull ChunkAccess chunk) {
		ChunkPos chunkPos = chunk.getPos();
		int chunkX = chunkPos.x;
		int chunkZ = chunkPos.z;

		for (Heightmap.Types type : Heightmap.Types.values()) {
			chunk.getOrCreateHeightmapUnprimed(type);
		}

		if (chunkX >= 0 && chunkX < 10 && chunkZ >= 0 && chunkZ < 10) {
			BlockState wallState = Hyperbox.modInstance.hyperboxWall.get().defaultBlockState();
			BlockState spawnPointMarker = Blocks.DIAMOND_BLOCK.defaultBlockState(); // Block under spawn point
			BlockState chunkBoundaryMarker = Blocks.REDSTONE_BLOCK.defaultBlockState(); // Chunk boundary marker

			BlockPos.MutableBlockPos mutaPos = new BlockPos.MutableBlockPos();

			int startX = chunkX * 16;
			int startZ = chunkZ * 16;
			int endX = startX + 16;
			int endZ = startZ + 16;

			int maxHorizontal = 160;  // Box size
			int ceilingY = this.getHeight() - 1;

			for (int worldX = startX; worldX < endX; worldX++) {
				for (int worldZ = startZ; worldZ < endZ; worldZ++) {
					boolean isWall = (worldX == 0 || worldX == maxHorizontal - 1 || worldZ == 0 || worldZ == maxHorizontal - 1);
					boolean isChunkBorder = isOnChunkBorder(worldX, worldZ);

					mutaPos.set(worldX, 0, worldZ);

					boolean isSpawnBlock = (worldX == 79 || worldX == 80) && (worldZ == 79 || worldZ == 80);

					if (isSpawnBlock) {
						chunk.setBlockState(mutaPos, spawnPointMarker, false);
					}

					else if (isChunkBorder) {
						chunk.setBlockState(mutaPos, chunkBoundaryMarker, false);
					}

					else {
						chunk.setBlockState(mutaPos, wallState, false);
					}

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

					mutaPos.set(worldX, ceilingY, worldZ);
					if (isChunkBorder) chunk.setBlockState(mutaPos, chunkBoundaryMarker, false);
					else chunk.setBlockState(mutaPos, wallState, false);

					for (Heightmap.Types type : Heightmap.Types.values()) {
						Heightmap heightmap = chunk.getOrCreateHeightmapUnprimed(type);
						heightmap.update(worldX & 15, ceilingY, worldZ & 15, wallState);
					}
				}
			}
		}
	}

	private boolean isOnChunkBorder(int worldX, int worldZ) {
        return worldX % 16 == 0 || worldX % 16 == 15 || worldZ % 16 == 0 || worldZ % 16 == 15;
	}

	@Override
	public void spawnOriginalMobs(@NotNull WorldGenRegion region)
	{
	}

	@Override
	public int getGenDepth()
	{
		return 128;
	}

	@Override
	public @NotNull CompletableFuture<ChunkAccess> fillFromNoise(@NotNull Blender blender, @NotNull RandomState random, @NotNull StructureManager structures, @NotNull ChunkAccess chunk)
	{
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
	public int getBaseHeight(int x, int z, @NotNull Types heightmapType, @NotNull LevelHeightAccessor level, @NotNull RandomState random)
	{
		boolean isWall = (x == 0 || x == 159 || z == 0 || z == 159);

        return switch (heightmapType) {
            case WORLD_SURFACE, WORLD_SURFACE_WG -> this.getHeight() - 1;
            case MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES -> isWall ? this.getHeight() - 1 : 0;
            default -> 0;
        };
	}

	@Override
	public @NotNull NoiseColumn getBaseColumn(int x, int z, @NotNull LevelHeightAccessor level, @NotNull RandomState random)
	{
		BlockState wallState = Hyperbox.modInstance.hyperboxWall.get().defaultBlockState();

		boolean isWall = (x == 0 || x == 159 || z == 0 || z == 159);

		BlockState[] states = new BlockState[this.getHeight()];

		states[0] = wallState;

		for (int y = 1; y < this.getHeight() - 1; y++) {
			states[y] = isWall ? wallState : Blocks.AIR.defaultBlockState();
		}

		states[this.getHeight() - 1] = wallState;

		return new NoiseColumn(0, states);
	}

	@Override
	public void addDebugScreenInfo(@NotNull List<String> stringsToRender, @NotNull RandomState random, @NotNull BlockPos pos)
	{
		stringsToRender.add("Hyperbox Dimension");
		stringsToRender.add("Height: " + this.getHeight());
		stringsToRender.add("MinY: " + this.getMinY());
	}

	@Nullable
	@Override
	public Pair<BlockPos, Holder<Structure>> findNearestMapStructure(@NotNull ServerLevel level, @NotNull HolderSet<Structure> structures, @NotNull BlockPos pos, int range, boolean skipKnownStructures)
	{
		return null;
	}

	@Override
	public void applyBiomeDecoration(@NotNull WorldGenLevel world, @NotNull ChunkAccess chunkAccess, @NotNull StructureManager structures)
	{
		// noop
	}

	@Override
	public int getSpawnHeight(@NotNull LevelHeightAccessor level)
	{
		return 1;
	}

	@Override
	public void createReferences(@NotNull WorldGenLevel world, @NotNull StructureManager structures, @NotNull ChunkAccess chunk)
	{
	}
}