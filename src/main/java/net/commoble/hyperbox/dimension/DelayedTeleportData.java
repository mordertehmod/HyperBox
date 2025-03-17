package net.commoble.hyperbox.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class DelayedTeleportData extends SavedData
{
	public static final String DATA_KEY = Hyperbox.MODID + "_delayed_events";
	
	public static final SavedData.Factory<DelayedTeleportData> FACTORY = new SavedData.Factory<>(
		DelayedTeleportData::create,
		DelayedTeleportData::load,
		null);
	
	private List<TeleportEntry> delayedTeleports = new ArrayList<>();
	
	public static @NotNull DelayedTeleportData getOrCreate(@NotNull ServerLevel level)
	{
		return level.getDataStorage().computeIfAbsent(FACTORY, DATA_KEY);
	}
	
	@Contract("_, _ -> new")
	public static @NotNull DelayedTeleportData load(CompoundTag nbt, HolderLookup.Provider registries)
	{
		return DelayedTeleportData.create();
	}
	
	@Contract(" -> new")
	public static @NotNull DelayedTeleportData create()
	{
		return new DelayedTeleportData();
	}

	protected DelayedTeleportData()
	{
	}

	public static void tick(@NotNull ServerLevel level)
	{
		MinecraftServer server = level.getServer();
		DelayedTeleportData eventData = getOrCreate(level);
		
		List<TeleportEntry> teleports = eventData.delayedTeleports;
		eventData.delayedTeleports = new ArrayList<>();
		for (TeleportEntry entry : teleports)
		{
			@Nullable ServerPlayer player = server.getPlayerList().getPlayer(entry.playerUUID);
			@Nullable ServerLevel targetWorld = server.getLevel(entry.targetLevel);
			if (player != null && targetWorld != null && player.level() == level)
			{
				TeleportHelper.sendPlayerToDimension(player, targetWorld, entry.targetVec);
			}
		}
	}
	
	public void schedulePlayerTeleport(@NotNull Player player, ResourceKey<Level> destination, Vec3 targetVec)
	{
		this.delayedTeleports.add(new TeleportEntry(player.getGameProfile().getId(), destination, targetVec));
	}

	@Override
	public @NotNull CompoundTag save(@NotNull CompoundTag compound, HolderLookup.@NotNull Provider registries)
	{
		return compound;
	}

	private record TeleportEntry(UUID playerUUID, ResourceKey<Level> targetLevel, Vec3 targetVec)
	{
	}
}
