package net.commoble.hyperbox.blocks;

import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.DelayedTeleportData;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.commoble.hyperbox.dimension.HyperboxDimension;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.commoble.hyperbox.dimension.ReturnPoint;
import net.commoble.hyperbox.dimension.SpawnPointHelper;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Nameable;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class HyperboxBlockEntity extends BlockEntity implements Nameable
{
	public static final String WORLD_KEY = "world_key";
	public static final String NAME = "CustomName"; // consistency with vanilla custom name data
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String COLOR = "color";
	private Optional<ResourceKey<Level>> levelKey = Optional.empty();
	private Optional<Component> name = Optional.empty();
	private int color = Hyperbox.DEFAULT_COLOR;
	private int[] weakPowerDUNSWE = {0,0,0,0,0,0};
	private int[] strongPowerDUNSWE = {0,0,0,0,0,0};
	
	@Contract("_, _ -> new")
	public static @NotNull HyperboxBlockEntity create(BlockPos pos, BlockState state)
	{
		return new HyperboxBlockEntity(Hyperbox.modInstance.hyperboxBlockEntityType.get(), pos, state);
	}
	
	public HyperboxBlockEntity(BlockEntityType<? extends HyperboxBlockEntity> type, BlockPos pos, BlockState state)
	{
		super(type, pos, state);
	}
	
	public void updateDimensionAfterPlacingBlock()
	{
		if (this.level instanceof ServerLevel thisServerLevel)
		{
			MinecraftServer server = thisServerLevel.getServer();
			ServerLevel childLevel = this.getLevelIfKeySet(server);
			if (childLevel == null)
				return;
			if (Boolean.TRUE.equals(Hyperbox.modInstance.commonConfig.autoForceHyperboxChunks.get()))
			{
				int chunkRadius = 10;

				for (int chunkX = 0; chunkX < chunkRadius; chunkX++) {
					for (int chunkZ = 0; chunkZ < chunkRadius; chunkZ++) {
						ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

						childLevel.getChunk(chunkPos.x, chunkPos.z);
						childLevel.setChunkForced(chunkPos.x, chunkPos.z, true);
						childLevel.getChunkSource().updateChunkForced(chunkPos, true);
					}
				}
			}

			BlockState thisState = this.getBlockState();
			Direction[] dirs = Direction.values();
			for (Direction dir : dirs)
			{
				thisState.onNeighborChange(this.level, this.worldPosition, this.worldPosition.relative(dir));
			}
			this.level.updateNeighbourForOutputSignal(this.worldPosition, thisState.getBlock());
			HyperboxBlock.notifyNeighborsOfStrongSignalChange(thisState, childLevel, this.worldPosition);
			for (Direction sideOfChildLevel : dirs)
			{
				this.getAperture(server, sideOfChildLevel).ifPresent(aperture ->{
					BlockPos aperturePos = aperture.getBlockPos();
					aperture.getBlockState().onNeighborChange(Objects.requireNonNull(aperture.getLevel()), aperturePos, aperturePos.relative(sideOfChildLevel.getOpposite()));
				});
			}
			
		}
	}
	
	public void setColor(int color)
	{
		if (this.color != color)
		{
			this.color = color;
			this.setChanged();
			BlockState state = this.getBlockState();
            assert this.level != null;
            this.level.sendBlockUpdated(this.worldPosition, state, state, Block.UPDATE_ALL);
			this.level.setBlocksDirty(this.worldPosition, state, state);
		}
	}
	
	public int getColor()
	{
		return this.color;
	}

	public Optional<ResourceKey<Level>> getLevelKey()
	{
		return this.levelKey;
	}
	
	public void setLevelKey(ResourceKey<Level> key)
	{
		this.levelKey = Optional.ofNullable(key);
		if (this.level instanceof ServerLevel level)
		{
			this.getLevelIfKeySet(level.getServer());
		}
		this.setChanged();
	}

	@Override
	public @NotNull Component getName()
	{
		return this.name.orElse(Component.translatable("block.hyperbox.hyperbox"));
	}

	@Override
	@Nullable
	public Component getCustomName()
	{
		return this.name.orElse(null);
	}
	
	public void setName(@Nullable Component name)
	{
		this.name = Optional.ofNullable(name);
		this.setChanged();
        assert this.level != null;
        this.level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_ALL);
	}
	
	@Nullable
	public ServerLevel getLevelIfKeySet(MinecraftServer server)
	{
		return this.levelKey.map(key ->
		{
			ServerLevel targetWorld = this.getChildWorld(server, key);
            assert this.level != null;
            HyperboxSaveData.getOrCreate(targetWorld).setWorldPos(server, targetWorld, targetWorld.dimension(), this.level.dimension(), this.worldPosition, this.getColor());
			return targetWorld;
		})
			.orElse(null);
	}
	
	public ServerLevel getChildWorld(MinecraftServer server, ResourceKey<Level> key)
	{
		return InfiniverseAPI.get().getOrCreateLevel(server, key, () -> HyperboxDimension.createDimension(server));
	}
	
	public int getPower(boolean strong, @NotNull Direction originalFace)
	{
		int output = (strong ? this.strongPowerDUNSWE : this.weakPowerDUNSWE)[originalFace.get3DDataValue()] - 1;
		return Mth.clamp(output,0,15);
	}

	@Nullable
	public <T> T getCapability(BlockCapability<T, Direction> sidedCap, Direction worldSpaceFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		if (thisBlock instanceof HyperboxBlock hyperboxBlock && this.level instanceof ServerLevel serverLevel)
		{
			ServerLevel targetLevel = this.getLevelIfKeySet(serverLevel.getServer());
			if (targetLevel != null)
			{
				BlockPos targetPos = hyperboxBlock.getPosAdjacentToAperture(this.getBlockState(), worldSpaceFace);
				Direction rotatedDirection = hyperboxBlock.getOriginalFace(thisState, worldSpaceFace);
				targetLevel.registerCapabilityListener(targetPos, () -> {
					serverLevel.invalidateCapabilities(this.getBlockPos());
					return false;
				});
				return targetLevel.getCapability(sidedCap, targetPos, rotatedDirection);
			}
		}
		return null;
	}
	
	public Optional<ApertureBlockEntity> getAperture(MinecraftServer server, Direction sideOfChildLevel)
	{
		BlockPos aperturePos = HyperboxChunkGenerator.CENTER.relative(sideOfChildLevel, 7);
		ServerLevel targetLevel = this.getLevelIfKeySet(server);
		return targetLevel == null
			? Optional.empty()
			: targetLevel.getBlockEntity(aperturePos) instanceof ApertureBlockEntity aperture
				? Optional.of(aperture)
				: Optional.empty();
	}
	
	public void updatePower(int weakPower, int strongPower, Direction originalFace)
	{
		BlockState thisState = this.getBlockState();
		Block thisBlock = thisState.getBlock();
		if (thisBlock instanceof HyperboxBlock hyperboxBlock)
		{
			Direction worldSpaceFace = hyperboxBlock.getCurrentFacing(thisState, originalFace);
			int originalFaceIndex = originalFace.get3DDataValue();
			int oldWeakPower = this.weakPowerDUNSWE[originalFaceIndex];
			int oldStrongPower = this.strongPowerDUNSWE[originalFaceIndex];
			if (oldWeakPower != weakPower || oldStrongPower != strongPower)
			{
				this.weakPowerDUNSWE[originalFaceIndex] = weakPower;
				this.strongPowerDUNSWE[originalFaceIndex] = strongPower;
				this.setChanged();	// mark te as needing its data saved
                assert this.level != null;
                this.level.sendBlockUpdated(this.worldPosition, thisState, thisState, 3); // mark te as needing data synced
				if (EventHooks.onNeighborNotify(this.level, this.worldPosition, thisState, java.util.EnumSet.of(originalFace), true).isCanceled())
					return;
				BlockPos adjacentPos = this.worldPosition.relative(worldSpaceFace);
				this.level.neighborChanged(adjacentPos, thisBlock, this.worldPosition);
				this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, worldSpaceFace.getOpposite());
			}
		}
	}

	public void teleportPlayerOrOpenMenu(@NotNull ServerPlayer serverPlayer)
	{
		ServerLevel level = serverPlayer.serverLevel();
		MinecraftServer server = level.getServer();
		ServerLevel targetLevel = this.getLevelIfKeySet(server);

		if (targetLevel == null)
		{
			serverPlayer.openMenu(HyperboxMenu.makeServerMenu(this));
		}

		else
		{
			BlockPos pos = this.getBlockPos();
			DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);

			if (hyperboxDimensionType != level.dimensionType())
			{
				ReturnPoint.setReturnPoint(serverPlayer, level.dimension(), pos);
			}

			Vec3 exactSpawnPos = SpawnPointHelper.getExactSpawnPosition(targetLevel);
			DelayedTeleportData.getOrCreate(serverPlayer.serverLevel()).schedulePlayerTeleport(
					serverPlayer, targetLevel.dimension(), exactSpawnPos);
		}
	}

	@Override
	public void saveAdditional(@NotNull CompoundTag compound, HolderLookup.@NotNull Provider registries)
	{
		super.saveAdditional(compound, registries);
		this.levelKey.ifPresent(key -> compound.putString(WORLD_KEY, key.location().toString()));
		this.writeClientSensitiveData(compound, registries);
	}

	@Override
	public void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries)
	{
		super.loadAdditional(nbt, registries);
		this.levelKey = nbt.contains(WORLD_KEY)
			? Optional.of(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(nbt.getString(WORLD_KEY))))
			: Optional.empty();
		this.readClientSensitiveData(nbt, registries);
	}
	
	protected void writeClientSensitiveData(CompoundTag nbt, HolderLookup.Provider registries)
	{
		this.name.ifPresent(theName ->
                nbt.putString(NAME, Component.Serializer.toJson(theName, registries)));
		if (this.color != Hyperbox.DEFAULT_COLOR)
		{
			nbt.putInt(COLOR, this.color);
		}
		nbt.putIntArray(WEAK_POWER, this.weakPowerDUNSWE);
		nbt.putIntArray(STRONG_POWER, this.strongPowerDUNSWE);
	}
	
	protected void readClientSensitiveData(@NotNull CompoundTag nbt, HolderLookup.Provider registries)
	{
		this.name = nbt.contains(NAME)
			? Optional.ofNullable(Component.Serializer.fromJson(nbt.getString(NAME), registries))
			: Optional.empty();
		this.color = nbt.contains(COLOR)
			? nbt.getInt(COLOR)
			: Hyperbox.DEFAULT_COLOR;
		this.weakPowerDUNSWE = nbt.getIntArray(WEAK_POWER);
		this.strongPowerDUNSWE = nbt.getIntArray(STRONG_POWER);
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries)
	{
		CompoundTag nbt = super.getUpdateTag(registries);
		this.writeClientSensitiveData(nbt, registries);
		return nbt;
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket()
	{
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public void onDataPacket(@NotNull Connection net, @NotNull ClientboundBlockEntityDataPacket pkt, HolderLookup.@NotNull Provider registries)
	{
		this.readClientSensitiveData(pkt.getTag(), registries);
	}
	
	@Override
	public void handleUpdateTag(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries)
	{
		this.readClientSensitiveData(nbt, registries);
	}
	
	@Override
    protected void applyImplicitComponents(BlockEntity.@NotNull DataComponentInput input) {
		super.applyImplicitComponents(input);
		this.name = Optional.ofNullable(input.get(DataComponents.CUSTOM_NAME));
		this.color = input.getOrDefault(DataComponents.DYED_COLOR, new DyedItemColor(Hyperbox.DEFAULT_COLOR, true)).rgb();
		this.levelKey = Optional.ofNullable(input.get(Hyperbox.modInstance.worldKeyDataComponent.get()));
	}
	
	@Override
    protected void collectImplicitComponents(DataComponentMap.@NotNull Builder builder) {
    	this.name.ifPresent(n -> builder.set(DataComponents.CUSTOM_NAME, n));
    	if (this.color != Hyperbox.DEFAULT_COLOR)
    	{
    		builder.set(DataComponents.DYED_COLOR, new DyedItemColor(this.color, true));
    	}
    	this.levelKey.ifPresent(key -> builder.set(Hyperbox.modInstance.worldKeyDataComponent.get(), key));
    }
}
