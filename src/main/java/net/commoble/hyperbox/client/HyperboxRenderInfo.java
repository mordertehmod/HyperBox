package net.commoble.hyperbox.client;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class HyperboxRenderInfo extends DimensionSpecialEffects
{
	public HyperboxRenderInfo()
	{
        super(128.0f, true, SkyType.NORMAL, true, false);
	}

	@Override
	public @NotNull Vec3 getBrightnessDependentFogColor(@NotNull Vec3 colorIn, float brightness)
	{

		return new Vec3(
				colorIn.x() * brightness,
				colorIn.y() * brightness,
				colorIn.z() * brightness
		);
	}


	@Override
	public boolean isFoggyAt(int x, int z)
	{
		return false;
	}

}
