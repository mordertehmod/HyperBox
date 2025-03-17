package net.commoble.hyperbox.blocks;

import java.util.Optional;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.dimension.DelayedTeleportData;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.commoble.hyperbox.dimension.SpawnPointHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class ApertureBlock extends Block implements EntityBlock
{
	// direction of facing of aperture
	public static final DirectionProperty FACING = BlockStateProperties.FACING;

	public ApertureBlock(Properties properties)
	{
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	@Override
	protected void createBlockStateDefinition(@NotNull Builder<Block, BlockState> builder)
	{
		super.createBlockStateDefinition(builder);
		builder.add(FACING);
	}

	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state)
	{
		return Hyperbox.modInstance.apertureBlockEntityType.get().create(pos, state);
	}
	
	@Override
	public @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit)
	{
		if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel)
		{
			MinecraftServer server = serverPlayer.server;
			
			HyperboxSaveData data = HyperboxSaveData.getOrCreate(serverLevel);
			ResourceKey<Level> parentKey = data.getParentWorld();
			BlockPos parentPos = data.getParentPos();
			BlockPos targetPos = parentPos;
			ServerLevel destinationLevel = server.getLevel(parentKey);
			if (destinationLevel == null)
			{
				destinationLevel = server.getLevel(Level.OVERWORLD);
			}
			Direction apertureFacing = state.getValue(FACING);
            assert destinationLevel != null;
            BlockState parentState = destinationLevel.getBlockState(parentPos);
			Block parentBlock = parentState.getBlock();
			if (parentBlock instanceof HyperboxBlock hyperboxBlock)
			{
				Direction hyperboxFacing = hyperboxBlock.getCurrentFacing(parentState, apertureFacing.getOpposite()); 
				targetPos = parentPos.relative(hyperboxFacing);
				if (destinationLevel.getBlockState(targetPos).getDestroySpeed(destinationLevel, targetPos) < 0)
				{
					targetPos = parentPos;
				}
				targetPos = SpawnPointHelper.getBestSpawnPosition(destinationLevel, targetPos, targetPos.offset(-3,-3,-3), targetPos.offset(3,3,3));
			}

			DelayedTeleportData.getOrCreate(serverPlayer.serverLevel()).schedulePlayerTeleport(serverPlayer, destinationLevel.dimension(), Vec3.atCenterOf(targetPos));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@Deprecated(forRemoval = false)
	public void neighborChanged(@NotNull BlockState thisState, @NotNull Level level, @NotNull BlockPos thisPos, @NotNull Block fromBlock, @NotNull BlockPos fromPos, boolean isMoving)
	{
		this.onNeighborUpdated(thisState, level, level.getBlockState(fromPos), fromPos);
		super.neighborChanged(thisState, level, thisPos, fromBlock, fromPos, isMoving);
	}

	@Override
	public void onNeighborChange(@NotNull BlockState thisState, @NotNull LevelReader level, @NotNull BlockPos thisPos, @NotNull BlockPos neighborPos)
	{
		this.onNeighborUpdated(thisState, level, level.getBlockState(neighborPos), neighborPos);
		super.onNeighborChange(thisState, level, thisPos, neighborPos);
	}
	
	protected void onNeighborUpdated(BlockState thisState, BlockGetter level, BlockState neighborState, BlockPos neighborPos)
	{
		if (level instanceof ServerLevel serverLevel)
		{
			Direction directionToNeighbor = thisState.getValue(FACING);
			int weakPower = neighborState.getSignal(level, neighborPos, directionToNeighbor);
			int strongPower = neighborState.getDirectSignal(level, neighborPos, directionToNeighbor);
			getLinkedHyperbox(serverLevel).ifPresent(hyperbox -> {
				hyperbox.updatePower(weakPower, strongPower, directionToNeighbor.getOpposite());
				hyperbox.setChanged();
			});
		}
		
	}
	
	@Override
	@Deprecated(forRemoval = false)
	public boolean isSignalSource(@NotNull BlockState state)
	{
		return true;
	}

	@Override
	@Deprecated(forRemoval = false)
	public int getSignal(@NotNull BlockState blockState, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction sideOfAdjacentBlock)
	{
		return level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture
			? aperture.getPower(false)
			: 0;
	}

	@Override
	@Deprecated(forRemoval = false)
	public int getDirectSignal(@NotNull BlockState blockState, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction sideOfAdjacentBlock)
	{
		return sideOfAdjacentBlock.getOpposite() == blockState.getValue(FACING)
			&& level.getBlockEntity(pos) instanceof ApertureBlockEntity aperture
				? aperture.getPower(true)
				: 0;
	}

	public static Optional<HyperboxBlockEntity> getLinkedHyperbox(@NotNull ServerLevel level)
	{
		MinecraftServer server = level.getServer();
		HyperboxSaveData data = HyperboxSaveData.getOrCreate(level);
		BlockPos parentPos = data.getParentPos();
		ResourceKey<Level> parentLevelKey = data.getParentWorld();
		ServerLevel parentLevel = server.getLevel(parentLevelKey);
        assert parentLevel != null;
        BlockEntity blockEntity = parentLevel.getBlockEntity(parentPos);
		return blockEntity instanceof HyperboxBlockEntity hyperbox ? Optional.of(hyperbox) : Optional.empty();
	}
}
