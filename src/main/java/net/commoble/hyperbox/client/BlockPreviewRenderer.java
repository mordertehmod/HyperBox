package net.commoble.hyperbox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

public class BlockPreviewRenderer extends ModelBlockRenderer
{
	private static BlockPreviewRenderer modInstance;
	
	public static BlockPreviewRenderer getInstance(ModelBlockRenderer baseRenderer)
	{
		if (modInstance == null || modInstance.blockColors != baseRenderer.blockColors)
		{
			modInstance = new BlockPreviewRenderer(baseRenderer);
		}
		
		return modInstance;
	}
	public BlockPreviewRenderer(@NotNull ModelBlockRenderer baseRenderer)
	{
		super(baseRenderer.blockColors);
	}

	public static void renderBlockPreview(@NotNull BlockPos pos, BlockState state, Level level, @NotNull Vec3 currentRenderPos, @NotNull PoseStack matrix, @NotNull MultiBufferSource renderTypeBuffer)
	{
		matrix.pushPose();
	
		double offsetX = pos.getX() - currentRenderPos.x();
		double offsetY = pos.getY() - currentRenderPos.y();
		double offsetZ = pos.getZ() - currentRenderPos.z();
		matrix.translate(offsetX, offsetY, offsetZ);
	
		BlockRenderDispatcher blockDispatcher = Minecraft.getInstance().getBlockRenderer();
		ModelBlockRenderer renderer = getInstance(blockDispatcher.getModelRenderer());
		RenderType bufferType = Sheets.translucentCullBlockSheet();
		renderer.tesselateWithoutAO(
			level,
			blockDispatcher.getBlockModel(state),
			state,
			pos,
			matrix,
			renderTypeBuffer.getBuffer(bufferType),
			false,
			level.random,
			state.getSeed(pos),
			OverlayTexture.NO_OVERLAY,
			ModelData.EMPTY,
                null);
	
		matrix.popPose();
	}

	@Override
	public void putQuadData(@NotNull BlockAndTintGetter level, @NotNull BlockState state, @NotNull BlockPos pos, @NotNull VertexConsumer vertexConsumer, PoseStack.@NotNull Pose pose, @NotNull BakedQuad quad,
							float tintA, float tintB, float tintC, float tintD, int brightness0, int brightness1, int brightness2, int brightness3, int combinedOverlayIn)
	{
		float r=1F;
		float g=1F;
		float b=1F;
		float alpha = ClientProxy.clientConfig.placementPreviewOpacity.get().floatValue();
		if (quad.isTinted())
		{
			int i = this.blockColors.getColor(state, level, pos, quad.getTintIndex());
			r = (i >> 16 & 255) / 255.0F;
			g = (i >> 8 & 255) / 255.0F;
			b = (i & 255) / 255.0F;
		}
		vertexConsumer.putBulkData(pose, quad, new float[]{tintA, tintB, tintC, tintD}, r, g, b, alpha, new int[]{brightness0, brightness1, brightness2, brightness3}, combinedOverlayIn, true);
	}
}