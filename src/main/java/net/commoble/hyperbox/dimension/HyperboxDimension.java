package net.commoble.hyperbox.dimension;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class HyperboxDimension
{	
	@Contract("_ -> new")
	public static @NotNull LevelStem createDimension(MinecraftServer server)
	{
		return new LevelStem(getDimensionTypeHolder(server), new HyperboxChunkGenerator(server));
	}
	
	public static @NotNull Holder<DimensionType> getDimensionTypeHolder(@NotNull MinecraftServer server)
	{
		return server.registryAccess() // get dynamic registries
			.registryOrThrow(Registries.DIMENSION_TYPE)
			.getHolderOrThrow(Hyperbox.DIMENSION_TYPE_KEY);
	}
	
	public static @NotNull DimensionType getDimensionType(MinecraftServer server)
	{
		return getDimensionTypeHolder(server).value();
	}

	public static IterationResult getHyperboxIterationDepth(MinecraftServer server, ServerLevel targetWorld, ServerLevel hyperboxWorld)
	{
		if (hyperboxWorld == null || targetWorld == null)
			return IterationResult.FAILURE;
		
		if (hyperboxWorld == targetWorld)
		{
			return IterationResult.NONE;
		}
		
		Set<ResourceKey<Level>> foundKeys = new HashSet<>();
		
		ServerLevel nextWorld = hyperboxWorld;
		ResourceKey<Level> nextKey = nextWorld.dimension();
		int iterations = 0;
		DimensionType hyperboxDimensionType = getDimensionType(server);
		while (nextWorld.dimensionType() == hyperboxDimensionType && !foundKeys.contains(nextKey))
		{
			foundKeys.add(nextKey);
			HyperboxSaveData data = HyperboxSaveData.getOrCreate(nextWorld);
			ResourceKey<Level> parentKey = data.getParentWorld();
			ServerLevel parentWorld = server.getLevel(parentKey);
			iterations++;
			if (parentWorld == targetWorld)
				return new IterationResult(iterations, data.getParentPos());
			if (parentWorld == null)
				return IterationResult.FAILURE;
			nextKey = parentKey;
			nextWorld = parentWorld;
		}
		
		return IterationResult.FAILURE;
	}
	
	public record IterationResult(int iterations, @Nullable BlockPos parentPos)
	{
		public static final IterationResult FAILURE = new IterationResult(-1, null);
		public static final IterationResult NONE = new IterationResult(0, null);
	}

	public static @NotNull ResourceLocation generateId(Player player, @NotNull String displayName)
	{
		String sanitizedName = displayName
			.replace(" ", "_")
			.replaceAll("\\W", "");
		if (sanitizedName.isBlank())
		{
			long time = player.level().getGameTime();
			long randLong = player.level().getRandom().nextLong();
			UUID uuid = new UUID(time, randLong);
			sanitizedName = uuid.toString();
		}
		String path = String.format("%s/%s", player.getStringUUID(), sanitizedName).toLowerCase(Locale.ROOT);
		return Hyperbox.id(path);
	}
}
