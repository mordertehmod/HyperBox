package net.commoble.hyperbox.client;

import net.commoble.hyperbox.ConfigHelper;
import net.commoble.hyperbox.Hyperbox;
import net.commoble.hyperbox.RotationHelper;
import net.commoble.hyperbox.blocks.HyperboxBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import org.jetbrains.annotations.NotNull;

public class ClientProxy
{
	public static ClientConfig clientConfig = null;

	public static void doClientModInit(IEventBus modBus, IEventBus forgeBus)
	{
		clientConfig = ConfigHelper.register(Hyperbox.MODID, ModConfig.Type.CLIENT, ClientConfig::new);

		modBus.addListener(ClientProxy::onRegisterScreens);
		modBus.addListener(ClientProxy::onRegisterDimensionSpecialEffects);
		modBus.addListener(ClientProxy::onRegisterRenderers);
		modBus.addListener(ClientProxy::onRegisterBlockColors);
		modBus.addListener(ClientProxy::onRegisterItemColors);
		forgeBus.addListener(ClientProxy::onHighlightBlock);
	}

	private static void onRegisterScreens(@NotNull RegisterMenuScreensEvent event)
	{
		event.register(Hyperbox.modInstance.hyperboxMenuType.get(), HyperboxScreen::new);
	}
	
	private static void onRegisterRenderers(@NotNull RegisterRenderers event)
	{
		event.registerBlockEntityRenderer(Hyperbox.modInstance.hyperboxBlockEntityType.get(), HyperboxBlockEntityRenderer::new);
	}
	
	private static void onRegisterDimensionSpecialEffects(@NotNull RegisterDimensionSpecialEffectsEvent event)
	{
		event.register(Hyperbox.HYPERBOX_ID, new HyperboxRenderInfo());
	}
	
	private static void onRegisterBlockColors(RegisterColorHandlersEvent.@NotNull Block event)
	{
		event.register((state, level, pos, tintIndex) -> ColorHandlers.getHyperboxBlockColor(level, pos, tintIndex), Hyperbox.modInstance.hyperboxBlock.get());
		event.register((state, level, pos, tintIndex) -> ColorHandlers.getHyperboxPreviewBlockColor(tintIndex), Hyperbox.modInstance.hyperboxPreviewBlock.get());
		event.register((state, level, pos, tintIndex) -> ColorHandlers.getApertureBlockColor(level, pos, tintIndex), Hyperbox.modInstance.apertureBlock.get());
	}
	
	private static void onRegisterItemColors(RegisterColorHandlersEvent.@NotNull Item event)
	{
		event.register(ColorHandlers::getHyperboxItemColor, Hyperbox.modInstance.hyperboxItem.get());
	}
	
	private static void onHighlightBlock(RenderHighlightEvent.Block event)
	{
		if (Boolean.TRUE.equals(clientConfig.showPlacementPreview.get()))
		{
			LocalPlayer player = Minecraft.getInstance().player;
            assert player != null;
            Level level = player.level();

            InteractionHand hand = player.getUsedItemHand();
			Item item = player.getItemInHand(hand).getItem();
			if (item instanceof BlockItem blockItem)
			{
				Block block = blockItem.getBlock();
				if (block instanceof HyperboxBlock)
				{
					BlockHitResult rayTrace = event.getTarget();
					Direction directionAwayFromTargetedBlock = rayTrace.getDirection();
					BlockPos placePos = rayTrace.getBlockPos().relative(directionAwayFromTargetedBlock);

					BlockState existingState = level.getBlockState(placePos);
					if (existingState.isAir() || existingState.canBeReplaced())
					{

						Vec3 hitVec = rayTrace.getLocation();

						Direction attachmentDirection = directionAwayFromTargetedBlock.getOpposite();
						Vec3 relativeHitVec = hitVec.subtract(Vec3.atLowerCornerOf(placePos));

						Direction outputDirection = RotationHelper.getOutputDirectionFromRelativeHitVec(relativeHitVec, attachmentDirection);
						RotationHelper.getRotationIndexForDirection(attachmentDirection, outputDirection);
						BlockState state = HyperboxBlock.getStateForPlacement(Hyperbox.modInstance.hyperboxPreviewBlock.get().defaultBlockState(), attachmentDirection, relativeHitVec);

						BlockPreviewRenderer.renderBlockPreview(placePos, state, level, event.getCamera().getPosition(), event.getPoseStack(), event.getMultiBufferSource());

					}
				}
			}
		}
	}
}
