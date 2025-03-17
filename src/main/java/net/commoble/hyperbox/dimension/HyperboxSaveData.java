package net.commoble.hyperbox.dimension;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.blocks.ApertureBlockEntity;
import net.commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class HyperboxSaveData extends SavedData
{
	public static final String DATA_KEY = Hyperbox.MODID;
	public static final String PARENT_WORLD_KEY = "parent_world";
	public static final String PARENT_POS_KEY = "parent_pos";
	public static final BlockPos DEFAULT_PARENT_POS = new BlockPos(0,65,0);
	
	public static final SavedData.Factory<HyperboxSaveData> FACTORY = new SavedData.Factory<>(
		HyperboxSaveData::create,
		HyperboxSaveData::load,
		null);
	
	private ResourceKey<Level> parentWorld = Level.OVERWORLD;
	public ResourceKey<Level> getParentWorld() { return this.parentWorld; }
	private BlockPos parentPos = DEFAULT_PARENT_POS;
	public BlockPos getParentPos() { return this.parentPos; }
	
	public static @NotNull HyperboxSaveData getOrCreate(@NotNull ServerLevel world)
	{
		return world.getDataStorage().computeIfAbsent(FACTORY, DATA_KEY);
	}
	
	public static @NotNull HyperboxSaveData load(@NotNull CompoundTag nbt, HolderLookup.Provider registries)
	{
		ResourceKey<Level> parentWorld = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString(PARENT_WORLD_KEY)));
		BlockPos parentPos = NbtUtils.readBlockPos(nbt, PARENT_POS_KEY).orElse(DEFAULT_PARENT_POS);
		return new HyperboxSaveData(parentWorld, parentPos);
	}
	
	@Contract(" -> new")
	public static @NotNull HyperboxSaveData create()
	{
		return new HyperboxSaveData(Level.OVERWORLD, DEFAULT_PARENT_POS);
	}

	protected HyperboxSaveData(ResourceKey<Level> parentWorld, BlockPos parentPos)
	{
		this.parentWorld = parentWorld;
		this.parentPos = parentPos;
	}
	
	public void setWorldPos(MinecraftServer server, ServerLevel thisWorld, ResourceKey<Level> thisWorldKey, ResourceKey<Level> parentWorldKey, BlockPos parentPos, int color)
	{
		ResourceKey<Level> oldParentWorld = this.parentWorld;
		BlockPos oldParentPos = this.parentPos;
		if (!oldParentWorld.equals(parentWorldKey) || !(oldParentPos.equals(parentPos)))
		{
			clearOldParent(server, thisWorldKey, oldParentWorld, oldParentPos);
		}
		this.parentWorld = parentWorldKey;
		this.parentPos = parentPos;
		for (Direction dir : Direction.values())
		{
			BlockPos aperturePos = HyperboxChunkGenerator.CENTER.relative(dir, 7);
			if (thisWorld.getBlockEntity(aperturePos) instanceof ApertureBlockEntity aperture)
			{
				aperture.setColor(color);
			}
		}
		this.setDirty();
	}
	
	protected static void clearOldParent(@NotNull MinecraftServer server, ResourceKey<Level> thisWorldKey, ResourceKey<Level> oldParentKey, BlockPos oldParentPos)
	{
		ServerLevel oldParentWorld = server.getLevel(oldParentKey);
		if (oldParentWorld != null
			&& oldParentWorld.getBlockEntity(oldParentPos) instanceof HyperboxBlockEntity hyperbox
			&& hyperbox.getLevelKey().filter(thisWorldKey::equals).isPresent())
		{
			oldParentWorld.removeBlock(oldParentPos, true);
		}
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag compound, HolderLookup.@NotNull Provider registries)
	{
		compound.putString(PARENT_WORLD_KEY, this.parentWorld.location().toString());
		compound.put(PARENT_POS_KEY, NbtUtils.writeBlockPos(this.parentPos));
		return compound;
	}

}
