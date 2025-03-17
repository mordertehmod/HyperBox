package net.commoble.hyperbox;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class RotationHelper
{
	protected static final Direction[][] OUTPUT_TABLE = {
		{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST},
		{Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST},
		{Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST},
		{Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST},
		{Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH},
		{Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH}
	};

	protected static final int[] ROTATION_INDEX_BY_HORIZONTAL_DIRECTION_INDEX = {2,3,0,1};
	
	@Contract(pure = true)
	public static int getRotationIndexForHorizontal(@NotNull Direction horizontalDirection)
	{
		return ROTATION_INDEX_BY_HORIZONTAL_DIRECTION_INDEX[horizontalDirection.get2DDataValue()];
	}
	
	@Contract(pure = true)
	public static Direction getOutputDirection(@NotNull Direction attachmentDirection, int rotationIndex)
	{
		return OUTPUT_TABLE[attachmentDirection.ordinal()][rotationIndex];
	}
	
	public static Direction getInputDirection(Direction attachmentDirection, int outputRotationIndex, int rotationsFromOutput)
	{
		return getOutputDirection(attachmentDirection, (outputRotationIndex + rotationsFromOutput) % 4);
	}
	
	public static int getRotatedRotation(@NotNull Direction attachmentFace, int rotationIndex, Rotation rotation)
	{
		return switch (attachmentFace) {
			case Direction.DOWN -> (rotationIndex + rotation.ordinal()) % 4;
			case Direction.UP -> (rotationIndex + 4 - rotation.ordinal()) % 4;
			default -> rotationIndex;
		};
	}
	
	public static int getMirroredRotation(Direction attachmentFace, int rotationIndex, Mirror mirror)
	{
		if (mirror == Mirror.NONE)
		{
			return rotationIndex;
		}
		
		boolean specialCase = (mirror == Mirror.LEFT_RIGHT && attachmentFace.getAxis() == Axis.Y);
		boolean rotationIsEven = (rotationIndex % 2 == 0);
		if ((specialCase && rotationIsEven) || (!specialCase && !rotationIsEven))
		{
			return (rotationIndex+2) % 4;
		}
		else
		{
			return rotationIndex;
		}
	}
	
	@Contract(pure = true)
	public static int getRotationIndexForDirection(@NotNull Direction attachmentFace, Direction outputDirection)
	{		
		Direction[] rotatedOutputs = OUTPUT_TABLE[attachmentFace.ordinal()];
		int size = rotatedOutputs.length;
		for (int i=0; i<size; i++)
		{
			if (rotatedOutputs[i] == outputDirection)
			{
				return i;
			}
		}
		
		return 0;
	}
	
	public static @NotNull Direction getOutputDirectionFromRelativeHitVec(Vec3 hitVec, @NotNull Direction directionTowardBlockAttachedTo)
	{
		Axis axis = directionTowardBlockAttachedTo.getAxis();
		float x = (float) (axis == Axis.X ? 0F : hitVec.x*2 - 1);
		float y = (float) (axis == Axis.Y ? 0F : hitVec.y*2 - 1);
		float z = (float) (axis == Axis.Z ? 0F : hitVec.z*2 - 1);
		
		return Direction.getNearest(x, y, z);
	}
}