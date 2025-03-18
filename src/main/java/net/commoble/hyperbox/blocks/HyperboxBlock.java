package net.commoble.hyperbox.blocks;

import java.util.Optional;

import javax.annotation.Nullable;

import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.RotationHelper;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class HyperboxBlock extends Block implements EntityBlock
{	
	public static final DirectionProperty ATTACHMENT_DIRECTION = BlockStateProperties.FACING;
	public static final IntegerProperty ROTATION = IntegerProperty.create("rotation", 0,3);

	public HyperboxBlock(Properties properties)
	{
		super(properties);
		this.registerDefaultState(this.stateDefinition.any()
			.setValue(ATTACHMENT_DIRECTION, Direction.DOWN)
			.setValue(ROTATION, 0));
	}

	@Override
	protected void createBlockStateDefinition(@NotNull Builder<Block, BlockState> builder)
	{
		super.createBlockStateDefinition(builder);
		builder.add(ATTACHMENT_DIRECTION, ROTATION);
	}

	@Override
	public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state)
	{
		return Hyperbox.modInstance.hyperboxBlockEntityType.get().create(pos, state);
	}

	@Override
	public BlockState getStateForPlacement(@NotNull BlockPlaceContext context)
	{
		BlockState defaultState = this.defaultBlockState();
		BlockPos placePos = context.getClickedPos();
		Direction faceOfAdjacentBlock = context.getClickedFace();
		Direction directionTowardAdjacentBlock = faceOfAdjacentBlock.getOpposite();
		Vec3 relativeHitVec = context.getClickLocation().subtract(Vec3.atLowerCornerOf(placePos));
		return getStateForPlacement(defaultState, directionTowardAdjacentBlock, relativeHitVec);
	}
	
	public static @NotNull BlockState getStateForPlacement(@NotNull BlockState defaultState, Direction directionTowardAdjacentBlock, Vec3 relativeHitVec)
	{
		Direction outputDirection = RotationHelper.getOutputDirectionFromRelativeHitVec(relativeHitVec, directionTowardAdjacentBlock);
		int rotationIndex = RotationHelper.getRotationIndexForDirection(directionTowardAdjacentBlock, outputDirection);
		
		if (defaultState.hasProperty(ATTACHMENT_DIRECTION) && defaultState.hasProperty(ROTATION))
		{
			return defaultState.setValue(ATTACHMENT_DIRECTION, directionTowardAdjacentBlock).setValue(ROTATION, rotationIndex);
		}
		else
		{
			return defaultState;
		}
	}

	@Override
	public @NotNull InteractionResult useWithoutItem(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull BlockHitResult hit)
	{
		if (player instanceof ServerPlayer serverPlayer
			&& level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox)
		{
			hyperbox.teleportPlayerOrOpenMenu(serverPlayer);
		}
		
		return InteractionResult.SUCCESS;
	}

	@Override
	public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack)
	{
		if (level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox)
		{
			hyperbox.setColor(DyedItemColor.getOrDefault(stack, Hyperbox.DEFAULT_COLOR));

			if (!level.isClientSide)
			{
				@Nullable Component name = stack.get(DataComponents.CUSTOM_NAME);

				if (name != null) hyperbox.setName(name);

				if (hyperbox.getLevelKey().isPresent()) hyperbox.updateDimensionAfterPlacingBlock();
			}
		}
	}

	@Override
	public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean isMoving) {
		if (state.getBlock() == newState.getBlock()) {
			super.onRemove(state, level, pos, newState, isMoving);
			notifyNeighborsOfStrongSignalChange(state, level, pos);
			return;
		}

		if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox) {
			MinecraftServer server = serverLevel.getServer();

			hyperbox.getLevelKey().ifPresent(dimensionKey -> {
				ServerLevel hyperboxDimension = server.getLevel(dimensionKey);

				if (hyperboxDimension != null) {
					int chunkRadius = 10;
					for (int chunkX = 0; chunkX < chunkRadius; chunkX++) {
						for (int chunkZ = 0; chunkZ < chunkRadius; chunkZ++) {
							hyperboxDimension.setChunkForced(chunkX, chunkZ, false);
						}
					}

					InfiniverseAPI.get().markDimensionForUnregistration(server, dimensionKey);
				}
			});
		}

		super.onRemove(state, level, pos, newState, isMoving);
		notifyNeighborsOfStrongSignalChange(state, level, pos);
	}

	public BlockPos getPosAdjacentToAperture(BlockState state, Direction worldSpaceFace)
	{
		Direction originalFace = this.getOriginalFace(state, worldSpaceFace);
		return HyperboxChunkGenerator.CENTER.relative(originalFace, 6);
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
		Direction originalFace = this.getOriginalFace(blockState, sideOfAdjacentBlock.getOpposite());
		return level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox
			? hyperbox.getPower(false, originalFace)
			: 0;
	}

	@Override
	@Deprecated(forRemoval = false)
	public int getDirectSignal(@NotNull BlockState blockState, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull Direction sideOfAdjacentBlock)
	{
		Direction originalFace = this.getOriginalFace(blockState, sideOfAdjacentBlock.getOpposite());
		return level.getBlockEntity(pos) instanceof HyperboxBlockEntity hyperbox
			? hyperbox.getPower(true, originalFace)
			: 0;
	}

	@Override
	@Deprecated(forRemoval = false)
	public void neighborChanged(@NotNull BlockState thisState, @NotNull Level level, @NotNull BlockPos thisPos, @NotNull Block fromBlock, @NotNull BlockPos fromPos, boolean isMoving)
	{
		this.onNeighborUpdated(thisState, level, thisPos, level.getBlockState(fromPos), fromPos);
		super.neighborChanged(thisState, level, thisPos, fromBlock, fromPos, isMoving);
	}

	@Override
	public void onNeighborChange(@NotNull BlockState thisState, @NotNull LevelReader level, @NotNull BlockPos thisPos, @NotNull BlockPos neighborPos)
	{
		this.onNeighborUpdated(thisState, level, thisPos, level.getBlockState(neighborPos), neighborPos);
		super.onNeighborChange(thisState, level, thisPos, neighborPos);
	}
	
	protected void onNeighborUpdated(BlockState thisState, BlockGetter level, BlockPos thisPos, BlockState neighborState, BlockPos neighborPos)
	{
		if (level instanceof ServerLevel serverLevel)
		{
			BlockPos offsetToNeighbor = neighborPos.subtract(thisPos);
			@Nullable Direction directionToNeighbor = Direction.fromDelta(offsetToNeighbor.getX(), offsetToNeighbor.getY(), offsetToNeighbor.getZ());
			if (directionToNeighbor != null)
			{
				this.getApertureTileEntityForFace(thisState, serverLevel,thisPos,directionToNeighbor).ifPresent(te -> {
					te.updatePower(serverLevel, neighborPos, neighborState, directionToNeighbor);
					te.setChanged(); // invokes onNeighborChanged on adjacent blocks, so we can propagate neighbor changes, update capabilities, etc
				});
			}
		}
		
	}
	
	public static void notifyNeighborsOfStrongSignalChange(BlockState state, Level world, BlockPos pos)
	{
		for (Direction direction : Direction.values())
		{
			world.updateNeighborsAt(pos.relative(direction), state.getBlock());
		}
	}
	
	public Optional<ApertureBlockEntity> getApertureTileEntityForFace(BlockState thisState, @NotNull ServerLevel world, BlockPos thisPos, Direction directionToNeighbor)
	{
		Direction originalFace = this.getOriginalFace(thisState, directionToNeighbor);
		return world.getBlockEntity(thisPos) instanceof HyperboxBlockEntity hyperbox
			? hyperbox.getAperture(world.getServer(), originalFace)
			: Optional.empty();
	}
	
	public Direction getOriginalFace(@NotNull BlockState thisState, Direction worldSpaceFace)
	{
		Direction downRotated = thisState.getValue(ATTACHMENT_DIRECTION);
		if (downRotated == worldSpaceFace)
		{
			return Direction.DOWN;
		}
		else if (downRotated.getOpposite() == worldSpaceFace)
		{
			return Direction.UP;
		}
		else
		{
			int rotationIndex = thisState.getValue(ROTATION);
			Direction newNorth = RotationHelper.getOutputDirection(downRotated, rotationIndex);
			if (newNorth == worldSpaceFace)
			{
				return Direction.NORTH;
			}
			else if (newNorth.getOpposite() == worldSpaceFace)
			{
				return Direction.SOUTH;
			}
			else
			{
				Direction newEast = RotationHelper.getInputDirection(downRotated, rotationIndex, 1);
				return newEast == worldSpaceFace ? Direction.EAST : Direction.WEST;
			}
		}
	}
	
	public Direction getCurrentFacing(@NotNull BlockState thisState, Direction originalFace)
	{
		Direction currentDown = thisState.getValue(ATTACHMENT_DIRECTION);
		int rotation = thisState.getValue(ROTATION);
		return originalFace == Direction.DOWN
			? currentDown
			: originalFace == Direction.UP
				? currentDown.getOpposite()
				: RotationHelper.getInputDirection(currentDown, rotation, RotationHelper.getRotationIndexForHorizontal(originalFace));
	}

	@Override
	@Deprecated(forRemoval = false)
	public @NotNull BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation)
	{
		if (state.hasProperty(ATTACHMENT_DIRECTION) && state.hasProperty(ROTATION))
		{
			Direction attachmentDirection = state.getValue(ATTACHMENT_DIRECTION);
			int rotationIndex = state.getValue(ROTATION);

			Direction newAttachmentDirection = rotation.rotate(attachmentDirection);
			int newRotationIndex = RotationHelper.getRotatedRotation(attachmentDirection, rotationIndex, rotation);

			return state.setValue(ATTACHMENT_DIRECTION, newAttachmentDirection).setValue(ROTATION, newRotationIndex);
		}
		else
		{
			return state;
		}
	}

	@Override
	@Deprecated(forRemoval = false)
	public @NotNull BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror)
	{
		if (state.hasProperty(ATTACHMENT_DIRECTION) && state.hasProperty(ROTATION))
		{
			Direction attachmentDirection = state.getValue(ATTACHMENT_DIRECTION);
			int rotationIndex = state.getValue(ROTATION);

			Direction newAttachmentDirection = mirror.mirror(attachmentDirection);
			int newRotationIndex = RotationHelper.getMirroredRotation(attachmentDirection, rotationIndex, mirror);

			return state.setValue(ATTACHMENT_DIRECTION, newAttachmentDirection).setValue(ROTATION, newRotationIndex);
		}
		else
		{
			return state;
		}
	}
}
