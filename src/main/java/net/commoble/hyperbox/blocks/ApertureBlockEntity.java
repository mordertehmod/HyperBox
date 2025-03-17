package net.commoble.hyperbox.blocks;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ApertureBlockEntity extends BlockEntity
{
	public static final String WEAK_POWER = "weak_power";
	public static final String STRONG_POWER = "strong_power";
	public static final String COLOR = "color";
	
	private int weakPower = 0;
	private int strongPower = 0;
	
	private int color = Hyperbox.DEFAULT_COLOR;

	@Contract("_, _ -> new")
	public static @NotNull ApertureBlockEntity create(BlockPos pos, BlockState state)
	{
		return new ApertureBlockEntity(Hyperbox.modInstance.apertureBlockEntityType.get(), pos, state);
	}
	
	public ApertureBlockEntity(BlockEntityType<? extends ApertureBlockEntity> type, BlockPos pos, BlockState state)
	{
		super(type, pos, state);
	}

	@Nullable
	public <T> T getCapability(BlockCapability<T, Direction> sidedCap, Direction side)
	{
		if (this.level instanceof ServerLevel serverLevel)
		{
			MinecraftServer server = serverLevel.getServer();
			HyperboxSaveData data = HyperboxSaveData.getOrCreate(serverLevel);
			BlockPos parentPos = data.getParentPos();
			ResourceKey<Level> parentLevelKey = data.getParentWorld();
			ServerLevel parentLevel = server.getLevel(parentLevelKey);
			if (parentLevel != null)
			{
				BlockState parentState = parentLevel.getBlockState(parentPos);
				Block parentBlock = parentState.getBlock();
				if (parentBlock instanceof HyperboxBlock hyperboxBlock)
				{
					parentLevel.registerCapabilityListener(parentPos, () -> {
						serverLevel.invalidateCapabilities(this.getBlockPos());
						return false;
					});
					Direction hyperboxFace = hyperboxBlock.getCurrentFacing(parentState, side.getOpposite());
					BlockPos delegatePos = parentPos.relative(hyperboxFace);
					return parentLevel.getCapability(sidedCap, delegatePos, hyperboxFace.getOpposite());
				}
			}
		}
		return null;
	}
	
	public int getColor()
	{
		return this.color;
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
		}
	}
	
	public int getPower(boolean strong)
	{
		int output = (strong ? this.strongPower : this.weakPower) - 1;
		return Mth.clamp(output, 0, 15);
	}
	
	public void updatePower(ServerLevel parentWorld, BlockPos neighborPos, @NotNull BlockState neighborState, Direction directionToNeighbor)
	{
		int privateWeakPower = neighborState.getSignal(parentWorld, neighborPos, directionToNeighbor);
		int privateStrongPower = neighborState.getDirectSignal(parentWorld, neighborPos, directionToNeighbor);
		this.updatePower(privateWeakPower,privateStrongPower);
	}
	
	public void updatePower(int weakPower, int strongPower)
	{
		if (this.weakPower != weakPower || this.strongPower != strongPower)
		{
			this.weakPower = weakPower;
			this.strongPower = strongPower;
			BlockState thisState = this.getBlockState();
			this.setChanged();
            assert this.level != null;
            this.level.sendBlockUpdated(this.worldPosition, thisState, thisState, 3);
			Direction outputSide = thisState.getValue(ApertureBlock.FACING);
			if (EventHooks.onNeighborNotify(this.level, this.worldPosition, thisState, java.util.EnumSet.of(outputSide), true).isCanceled())
				return;
			BlockPos adjacentPos = this.worldPosition.relative(outputSide);
			Block thisBlock = thisState.getBlock();
			this.level.neighborChanged(adjacentPos, thisBlock, this.worldPosition);
			this.level.updateNeighborsAtExceptFromFacing(adjacentPos, thisBlock, outputSide.getOpposite());
		}
	}
	
	@Override
	public void saveAdditional(@NotNull CompoundTag compound, HolderLookup.@NotNull Provider registries)
	{
		super.saveAdditional(compound, registries);
		this.writeClientSensitiveData(compound);
	}

	@Override
	public void loadAdditional(@NotNull CompoundTag nbt, HolderLookup.@NotNull Provider registries)
	{
		super.loadAdditional(nbt, registries);
		this.readClientSensitiveData(nbt);
	}
	
	public void writeClientSensitiveData(@NotNull CompoundTag nbt)
	{
		nbt.putInt(WEAK_POWER, this.weakPower);
		nbt.putInt(STRONG_POWER, this.strongPower);
		if (this.color != Hyperbox.DEFAULT_COLOR)
		{
			nbt.putInt(COLOR, this.color);
		}
	}
	
	public void readClientSensitiveData(@NotNull CompoundTag nbt)
	{
		this.weakPower = nbt.getInt(WEAK_POWER);
		this.strongPower = nbt.getInt(STRONG_POWER);
		if (nbt.contains(COLOR))
		{
			this.color = nbt.getInt(COLOR);
		}
	}

	@Override
	public @NotNull CompoundTag getUpdateTag(HolderLookup.@NotNull Provider registries)
	{
		CompoundTag nbt = super.getUpdateTag(registries);
		this.writeClientSensitiveData(nbt);
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
		this.readClientSensitiveData(pkt.getTag());
	}

	@Override
	public void handleUpdateTag(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries)
	{
		this.readClientSensitiveData(tag);
	}
}
