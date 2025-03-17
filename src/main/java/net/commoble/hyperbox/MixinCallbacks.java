package net.commoble.hyperbox;

import java.nio.file.Path;
import java.util.function.Consumer;
import net.commoble.hyperbox.dimension.HyperboxRegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;

public class MixinCallbacks
{	
	public static void onIOWorkerConstruction(RegionStorageInfo info, Path path, boolean sync, Consumer<RegionFileStorage> cacheConsumer)
	{
		String s = path.toString();
		if (s.contains("dimensions/hyperbox") || s.contains("dimensions\\hyperbox"))
		{
			cacheConsumer.accept(new HyperboxRegionFileStorage(info, path,sync));
		}
	}
}
