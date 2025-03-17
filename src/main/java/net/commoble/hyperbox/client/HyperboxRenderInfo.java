package net.commoble.hyperbox.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

public class HyperboxRenderInfo extends DimensionSpecialEffects
{

	public HyperboxRenderInfo()
	{	// args are:
		// cloud height -- NaN for nether and end, only overworld has clouds
		// has ground -- affects sky color (true for overworld and nether, false for end)
		// fog type -- overworld has NORMAL, nether has NONE, end has END
		// force bright lightmap -- affects *color* of lighting -- only true for end
		// constant ambient light -- makes bottom of blocks appear brighter, tops appear dimmer -- only true for nether
        super(128.0f, true, SkyType.NORMAL, true, false);
	}

	// get brightness dependent fog color
	// overworld does some math, nether just returns the input, end scales it toward black
	@Override
	public Vec3 getBrightnessDependentFogColor(Vec3 colorIn, float brightness)
	{

		return new Vec3(
				colorIn.x() * brightness,
				colorIn.y() * brightness,
				colorIn.z() * brightness
		);
	}

	// is foggy -- only nether returns true
	@Override
	public boolean isFoggyAt(int x, int z)
	{
		return false;
	}

	public boolean renderSky(int ticks, float partialTick, PoseStack poseStack, ClientLevel level, Minecraft minecraft)
	{
		// Return false to use default sky rendering with sun/moon
		return false;
	}

}
