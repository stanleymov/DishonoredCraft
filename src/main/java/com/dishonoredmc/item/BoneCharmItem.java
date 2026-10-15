package com.dishonoredmc.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A bone charm: a passive Dishonored trait while it is equipped in a bone charm slot (BoneCharms). Its effect is described in its tooltip. */
public class BoneCharmItem extends Item {
	private final String charm;

	public BoneCharmItem(Settings settings, String charm) {
		super(settings);
		this.charm = charm;
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.dishonored.bone_charm." + charm).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.dishonored.bone_charm.equip").formatted(Formatting.DARK_GRAY));
	}
}
