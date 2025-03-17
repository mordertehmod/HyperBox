package net.commoble.hyperbox.dimension;

import java.io.IOException;
import java.nio.file.Path;

import javax.annotation.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.jetbrains.annotations.NotNull;

public class HyperboxRegionFileStorage extends RegionFileStorage
{

	public HyperboxRegionFileStorage(RegionStorageInfo info, Path path, boolean sync)
	{
		super(info, path, sync);
	}

	@Override
	@Nullable
	public CompoundTag read(@NotNull ChunkPos pos) throws IOException
	{
		return super.read(pos);
	}
}
