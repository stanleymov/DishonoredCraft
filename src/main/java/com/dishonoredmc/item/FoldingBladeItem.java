package com.dishonoredmc.item;

import com.dishonoredmc.ModSounds;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ToolItem;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * Corvo's folding blade: a sword that also cuts blocks, one per swing.
 * Hold right-click to parry like a shield. Unbreakable, with a 4 block reach against creatures.
 */
public class FoldingBladeItem extends ToolItem {
	public static final ToolMaterial MATERIAL = new ToolMaterial() {
		@Override public int getDurability() { return 1400; }
		@Override public float getMiningSpeedMultiplier() { return 8.0F; }
		@Override public float getAttackDamage() { return 3.0F; }
		@Override public int getMiningLevel() { return 2; }
		@Override public int getEnchantability() { return 14; }
		@Override public Ingredient getRepairIngredient() { return Ingredient.ofItems(Items.IRON_INGOT); }
	};

	private static final UUID REACH_ID = UUID.fromString("6f3c1a52-2d1e-4b1a-9b8e-6d4b0f6c4a11");
	private final Multimap<EntityAttribute, EntityAttributeModifier> modifiers;

	public FoldingBladeItem(Settings settings) {
		super(MATERIAL, settings);
		this.modifiers = ImmutableMultimap.<EntityAttribute, EntityAttributeModifier>builder()
			// Same numbers as a sword of this material: 4 + 3 extra damage, -2.2 attack speed.
			.put(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(ATTACK_DAMAGE_MODIFIER_ID, "Weapon modifier", 7.0, EntityAttributeModifier.Operation.ADDITION))
			.put(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(ATTACK_SPEED_MODIFIER_ID, "Weapon modifier", -2.2, EntityAttributeModifier.Operation.ADDITION))
			// Corvo's lunge: 4 blocks of reach against creatures (vanilla is 3).
			.put(ForgeMod.ENTITY_REACH.get(), new EntityAttributeModifier(REACH_ID, "Blade reach", 1.0, EntityAttributeModifier.Operation.ADDITION))
			.build();
	}

	/** Pistol, crossbow, grenade and spring razor: held in the left hand, so the blade stays out in the right. */
	public static boolean isLeftHandTool(ItemStack stack) {
		return stack.isOf(ModItems.PISTOL) || stack.isOf(ModItems.WRIST_BOW) || stack.isOf(ModItems.GRENADE) || stack.isOf(ModItems.SPRING_RAZOR);
	}

	/**
	 * Corvo's blade is out: held, or (as in Dishonored, where the sword never leaves the right hand) carried
	 * anywhere in the inventory while a gun or gadget is in hand.
	 */
	public static boolean bladeOut(PlayerEntity player) {
		ItemStack main = player.getMainHandStack();
		if (main.getItem() instanceof FoldingBladeItem) return true;
		if (!isLeftHandTool(main)) return false;
		var inv = player.getInventory();
		for (int i = 0; i < inv.size(); i++) if (inv.getStack(i).getItem() instanceof FoldingBladeItem) return true;
		return false;
	}

	private static final UUID TOOL_DAMAGE_ID = UUID.fromString("0b6e7f4c-8a51-4c7e-9f0d-3b2a51c1e7a1");
	private static final UUID TOOL_SPEED_ID = UUID.fromString("5d0a9e2b-17c4-4f63-8e55-c2d41f8a9b02");
	private static final UUID TOOL_REACH_ID = UUID.fromString("a4e1c3d7-6b2f-4e98-b0a5-7f3e9d1c2b03");

	/**
	 * Every tick, both sides: with a gun or gadget in hand and the blade out, left click strikes with the blade
	 * (its damage, attack speed and reach). The held item itself has none of these.
	 */
	public static void tickLeftHandTool(PlayerEntity player) {
		boolean on = !(player.getMainHandStack().getItem() instanceof FoldingBladeItem) && bladeOut(player);
		toolModifier(player, EntityAttributes.GENERIC_ATTACK_DAMAGE, TOOL_DAMAGE_ID, 7.0, on);
		toolModifier(player, EntityAttributes.GENERIC_ATTACK_SPEED, TOOL_SPEED_ID, -2.2, on);
		toolModifier(player, ForgeMod.ENTITY_REACH.get(), TOOL_REACH_ID, 1.0, on);
	}

	private static void toolModifier(PlayerEntity player, EntityAttribute attribute, UUID id, double amount, boolean on) {
		var inst = player.getAttributeInstance(attribute);
		if (inst == null) return;
		boolean has = inst.getModifier(id) != null;
		if (on && !has) inst.addTemporaryModifier(new EntityAttributeModifier(id, "Folding blade (left-hand tool)", amount, EntityAttributeModifier.Operation.ADDITION));
		else if (!on && has) inst.removeModifier(id);
	}

	@Override
	public Multimap<EntityAttribute, EntityAttributeModifier> getAttributeModifiers(EquipmentSlot slot) {
		return slot == EquipmentSlot.MAINHAND ? modifiers : super.getAttributeModifiers(slot);
	}

	/** Enchantable like a sword (Sharpness, Smite, Looting, Fire Aspect, Knockback...) and an axe (Efficiency). */
	@Override
	public boolean canApplyAtEnchantingTable(ItemStack stack, net.minecraft.enchantment.Enchantment enchantment) {
		return enchantment.target == net.minecraft.enchantment.EnchantmentTarget.WEAPON
			|| enchantment.target == net.minecraft.enchantment.EnchantmentTarget.DIGGER
			|| super.canApplyAtEnchantingTable(stack, enchantment);
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return true;
	}

		/** Unbreakable. */
	@Override
	public boolean isDamageable(ItemStack stack) {
		return false;
	}

	@Override
	public float getMiningSpeedMultiplier(ItemStack stack, BlockState state) {
		if (state.isOf(Blocks.COBWEB)) return 15.0F;
		if (state.isIn(BlockTags.LEAVES)) return 12.0F;
		if (state.isIn(BlockTags.AXE_MINEABLE)) return 9.0F;
		return 1.0F;
	}

	@Override
	public boolean isSuitableFor(BlockState state) {
		return state.isOf(Blocks.COBWEB);
	}

	@Override
	public boolean canMine(BlockState state, World world, BlockPos pos, PlayerEntity miner) {
		return !miner.isCreative();
	}

	@Override
	public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		attacker.getWorld().playSound(null, attacker.getX(), attacker.getY(), attacker.getZ(), ModSounds.SWORD_WHOOSH, SoundCategory.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** Forge only treats an item as a shield (damage blocked from the front) when it can perform SHIELD_BLOCK. */
	@Override
	public boolean canPerformAction(ItemStack stack, net.minecraftforge.common.ToolAction action) {
		return action == net.minecraftforge.common.ToolActions.SHIELD_BLOCK || super.canPerformAction(stack, action);
	}

	@Override
	public UseAction getUseAction(ItemStack stack) {
		return UseAction.BLOCK;
	}

	@Override
	public int getMaxUseTime(ItemStack stack) {
		return 72000;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		user.setCurrentHand(hand);
		return TypedActionResult.consume(user.getStackInHand(hand));
	}
}
