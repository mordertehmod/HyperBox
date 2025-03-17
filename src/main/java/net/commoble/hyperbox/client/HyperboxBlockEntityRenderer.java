package net.commoble.hyperbox.client;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;

import net.commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

public class HyperboxBlockEntityRenderer implements BlockEntityRenderer<HyperboxBlockEntity>
{

	public HyperboxBlockEntityRenderer(BlockEntityRendererProvider.Context context) { }

	@Override
	public void render(@NotNull HyperboxBlockEntity hyperbox, float partialTicks, @NotNull PoseStack matrixStackIn, @NotNull MultiBufferSource bufferIn, int combinedLightIn, int combinedOverlayIn)
	{
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Component customName = hyperbox.getCustomName();
		if (player != null && customName != null && shouldRenderNameplate(hyperbox, player))
		{
			renderName(mc, customName, matrixStackIn);
		}
	}

	public static boolean shouldRenderNameplate(HyperboxBlockEntity hyperbox, @NotNull LocalPlayer player)
	{
		double radius = player.isCrouching() ? ClientProxy.clientConfig.nameplateSneakingRenderDistance.get() : ClientProxy.clientConfig.nameplateRenderDistance.get();

		if (radius <= 0)
			return false;

		BlockPos boxPos = hyperbox.getBlockPos();
		double boxX = boxPos.getX() + 0.5D;
		double boxY = boxPos.getY() + 0.5D;
		double boxZ = boxPos.getZ() + 0.5D;
		double distanceSquared = player.distanceToSqr(boxX, boxY, boxZ);
		return distanceSquared < radius * radius;
	}

	public static void renderName(@NotNull Minecraft mc, Component displayNameIn, @NotNull PoseStack matrixStackIn)
	{
		Font fontRenderer = mc.font;
		Quaternionf cameraRotation = mc.getEntityRenderDispatcher().cameraOrientation();


        matrixStackIn.pushPose();
		matrixStackIn.translate(0.5F, 1.4F, 0.5F);
		matrixStackIn.mulPose(cameraRotation);
		matrixStackIn.scale(-0.025F, -0.025F, 0.025F);
		Matrix4f matrix4f = matrixStackIn.last().pose();
		float backgroundOpacity = mc.options.getBackgroundOpacity(0.25F);
		int alpha = (int) (backgroundOpacity * 255.0F) << 24;
		float textOffset = (float) -fontRenderer.width(displayNameIn) / 2;
        MultiBufferSource bufferSource = Minecraft.getInstance().renderBuffers().outlineBufferSource();
		fontRenderer.drawInBatch(displayNameIn, textOffset, 0F, 553648127, false, matrix4f, bufferSource, Font.DisplayMode.SEE_THROUGH, alpha, 0xFFFFFF);
		fontRenderer.drawInBatch(displayNameIn, textOffset, 0F, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0, 0xFFFFFF);
		
		matrixStackIn.popPose();
	}

}
