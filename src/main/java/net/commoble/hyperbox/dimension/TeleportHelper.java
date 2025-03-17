package net.commoble.hyperbox.dimension;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class TeleportHelper
{
	private static final double EXACT_SPAWN_X = 80.00;
	private static final double EXACT_SPAWN_Y = 1.00;
	private static final double EXACT_SPAWN_Z = 80.00;

	public static void sendPlayerToDimension(ServerPlayer serverPlayer, @NotNull ServerLevel targetLevel, Vec3 targetVec)
	{
		if (targetLevel.dimension().location().toString().contains("hyperbox")) {
			BlockPos targetPos = new BlockPos((int)targetVec.x(), (int)targetVec.y(), (int)targetVec.z());
			BlockPos centerPos = new BlockPos(80, 1, 80);

			if (targetPos.closerThan(centerPos, 3)) {
				targetLevel.getChunk(new BlockPos(80, 1, 80));

				serverPlayer.teleportTo(targetLevel,
						EXACT_SPAWN_X, EXACT_SPAWN_Y, EXACT_SPAWN_Z,
						serverPlayer.getYRot(), serverPlayer.getXRot());
				return;
			}
		}

		targetLevel.getChunk(new BlockPos((int)targetVec.x(), (int)targetVec.y(), (int)targetVec.z()));
		serverPlayer.teleportTo(targetLevel, targetVec.x(), targetVec.y(), targetVec.z(),
				serverPlayer.getYRot(), serverPlayer.getXRot());
	}

	public static void ejectPlayerFromDeadWorld(ServerPlayer serverPlayer)
	{
		// get best world to send the player to
		ReturnPoint.getReturnPoint(serverPlayer)
				.evaluate((targetLevel,pos) ->
				{
					if (targetLevel instanceof ServerLevel serverLevel)
					{
						// For ejection, just use the regular method
						sendPlayerToDimension(serverPlayer, serverLevel, Vec3.atCenterOf(pos));
					}
					return Optional.empty();	// make the worldposcallable happy
				});
	}
}