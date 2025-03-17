package net.commoble.hyperbox.dimension;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class SpawnPointHelper
{
	public static final Vec3 SPAWN_PAD_CENTER = new Vec3(80.02, 1.00, 80.02);

	public static BlockPos getBestSpawnPosition(BlockGetter world, BlockPos target, BlockPos minSpawnCorner, BlockPos maxSpawnCorner)
	{
		BlockPos centerPos = new BlockPos(79, 1, 79); // Bottom-right corner of the 4-block area
		if (isPosAllowed(world, centerPos, minSpawnCorner, maxSpawnCorner) &&
				isPosAllowed(world, centerPos.offset(1, 0, 0), minSpawnCorner, maxSpawnCorner) &&
				isPosAllowed(world, centerPos.offset(0, 0, 1), minSpawnCorner, maxSpawnCorner) &&
				isPosAllowed(world, centerPos.offset(1, 0, 1), minSpawnCorner, maxSpawnCorner)) {
			return new BlockPos(80, 1, 80);
		}

		BlockPos clampedTarget = clamp(target, minSpawnCorner, maxSpawnCorner);
		BlockPos bestPos = clampedTarget;
		int bestPosViability = -1;
		Set<BlockPos> visited = new HashSet<>();
		LinkedList<BlockPos> remaining = new LinkedList<>();
		remaining.add(clampedTarget);
		while(!remaining.isEmpty())
		{
			BlockPos nextPos = remaining.removeFirst();
			int viability = getViability(world, nextPos);
			if (viability == 3)
			{
				return nextPos;
			}
			else
			{
				if (viability > bestPosViability)
				{
					bestPos = nextPos;
					bestPosViability = viability;
				}

				Direction[] dirs = Direction.values();
                for (Direction dir : dirs) {
                    BlockPos nextPosToVisit = nextPos.relative(dir);
                    if (!visited.contains(nextPosToVisit)
                            && isPosAllowed(world, nextPosToVisit, minSpawnCorner, maxSpawnCorner)) {
                        remaining.add(nextPosToVisit);
                        visited.add(nextPosToVisit);
                    }
                }
			}
		}

		return bestPos;
	}

	public static Vec3 getExactSpawnPosition(BlockGetter world) {
		BlockPos centerPos = new BlockPos(79, 1, 79);
		if (isPosAllowed(world, centerPos, HyperboxChunkGenerator.MIN_SPAWN_CORNER, HyperboxChunkGenerator.MAX_SPAWN_CORNER) &&
				isPosAllowed(world, centerPos.offset(1, 0, 0), HyperboxChunkGenerator.MIN_SPAWN_CORNER, HyperboxChunkGenerator.MAX_SPAWN_CORNER) &&
				isPosAllowed(world, centerPos.offset(0, 0, 1), HyperboxChunkGenerator.MIN_SPAWN_CORNER, HyperboxChunkGenerator.MAX_SPAWN_CORNER) &&
				isPosAllowed(world, centerPos.offset(1, 0, 1), HyperboxChunkGenerator.MIN_SPAWN_CORNER, HyperboxChunkGenerator.MAX_SPAWN_CORNER)) {
			return SPAWN_PAD_CENTER;
		}

		BlockPos bestPos = getBestSpawnPosition(world,
				new BlockPos(80, 1, 80),
				HyperboxChunkGenerator.MIN_SPAWN_CORNER,
				HyperboxChunkGenerator.MAX_SPAWN_CORNER);

		return Vec3.atCenterOf(bestPos);
	}

	public static @NotNull BlockPos clamp(@NotNull BlockPos pos, @NotNull BlockPos min, @NotNull BlockPos max)
	{
		int x = Mth.clamp(pos.getX(), min.getX(), max.getX());
		int y = Mth.clamp(pos.getY(), min.getY(), max.getY());
		int z = Mth.clamp(pos.getZ(), min.getZ(), max.getZ());
		return new BlockPos(x,y,z);
	}

	public static boolean isPosAllowed(BlockGetter world, BlockPos pos, BlockPos min, BlockPos max)
	{
		return isPosWithinBounds(pos, min, max)
				&& world.getBlockState(pos).getDestroySpeed(world, pos) >= 0; // don't search through the indestructible walls
	}

	public static boolean isPosWithinBounds(@NotNull BlockPos pos, @NotNull BlockPos min, BlockPos max)
	{
		int x = pos.getX();
		int y = pos.getY();
		int z = pos.getZ();
		return x >= min.getX()
				&& x <= max.getX()
				&& y >= min.getY()
				&& y <= max.getY()
				&& z >= min.getZ()
				&& z <= max.getZ();
	}

	public static int getViability(BlockGetter world, @NotNull BlockPos target)
	{
		return doesBlockBlockHead(world, target.above())
				? 0
				: doesBlockBlockFeet(world, target)
				? 1
				: doesBlockBlockFeet(world,target.below())
				? 3
				: 2;
	}

	// return true if the block has no interaction shape (doesn't block cursor interactions)
	public static boolean doesBlockBlockHead(@NotNull BlockGetter world, BlockPos pos)
	{
		return !world.getBlockState(pos).getShape(world,pos).isEmpty();
	}

	// return true if the block has no collision shape (doesn't prevent movement)
	public static boolean doesBlockBlockFeet(@NotNull BlockGetter world, BlockPos pos)
	{
		return !world.getBlockState(pos).getCollisionShape(world,pos).isEmpty();
	}
}