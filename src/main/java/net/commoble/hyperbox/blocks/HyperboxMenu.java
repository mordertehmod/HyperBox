package net.commoble.hyperbox.blocks;

import java.util.Optional;

import net.commoble.hyperbox.Hyperbox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class HyperboxMenu extends AbstractContainerMenu
{
	private final Optional<HyperboxBlockEntity> hyperbox;
	
	@Contract("_ -> new")
	public static @NotNull HyperboxMenu makeClientMenu(int id) {
		return new HyperboxMenu(Hyperbox.modInstance.hyperboxMenuType.get(), id, Optional.empty());
	}
	
	@Contract("_ -> new")
	public static @NotNull MenuProvider makeServerMenu(HyperboxBlockEntity hyperbox)
	{
		return new SimpleMenuProvider(
			(id, inventory, player) -> new HyperboxMenu(Hyperbox.modInstance.hyperboxMenuType.get(), id, Optional.ofNullable(hyperbox)),
			Component.translatable("menu.hyperbox"));
	}

	protected HyperboxMenu(MenuType<?> type, int id, Optional<HyperboxBlockEntity> hyperbox)
	{
		super(type, id);
		this.hyperbox = hyperbox;
	}

	@Override
	public @NotNull ItemStack quickMoveStack(@NotNull Player player, int id) { return ItemStack.EMPTY; }

	@Override
	public boolean stillValid(@NotNull Player player)
	{
		return this.hyperbox.map(box ->
		{
			Level level = box.getLevel();
			BlockPos pos = box.getBlockPos();
			double returnX = pos.getX() + 0.50;
			double returnY = pos.getY() + 0.50;
			double returnZ = pos.getZ() + 0.50;

            return level != null && level == player.level() && level.getBlockEntity(pos) == box && (player.distanceToSqr(returnX, returnY, returnZ) <= 64.0D);
        })
		.orElse(false);
	}
	
	public Optional<HyperboxBlockEntity> hyperbox()
	{
		return this.hyperbox;
	}
}
