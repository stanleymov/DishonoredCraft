package com.dishonoredmc.item;

import com.dishonoredmc.DishonoredMod;
import com.dishonoredmc.entity.GrenadeEntity;
import com.dishonoredmc.entity.SpringRazorEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Rarity;

import java.util.ArrayList;
import java.util.List;

public final class ModItems {
	private static final List<Item> ALL = new ArrayList<>();

	public static final Item FOLDING_BLADE = register("folding_blade", new FoldingBladeItem(new Item.Settings().rarity(Rarity.RARE)));
	public static final Item PISTOL = register("pistol", new PistolItem(new Item.Settings().maxCount(1).maxDamage(600)));
	public static final Item WRIST_BOW = register("wrist_bow", new WristBowItem(new Item.Settings().maxCount(1).maxDamage(800)));
	public static final Item PISTOL_SHOT = register("pistol_shot", new Item(new Item.Settings()));
	public static final Item CROSSBOW_BOLT = register("crossbow_bolt", new Item(new Item.Settings()));
	public static final Item SLEEP_DART = register("sleep_dart", new Item(new Item.Settings()));
	public static final Item INCENDIARY_BOLT = register("incendiary_bolt", new Item(new Item.Settings()));
	public static final Item GRENADE = register("grenade", new ThrowableItem(new Item.Settings().maxCount(16), 1.5F, GrenadeEntity::new));
	public static final Item SPRING_RAZOR = register("spring_razor", new ThrowableItem(new Item.Settings().maxCount(16), 0.9F, SpringRazorEntity::new));
	public static final Item SOKOLOVS_ELIXIR = register("sokolovs_elixir", new ElixirItem(new Item.Settings().maxCount(16), ElixirItem.Kind.HEALTH));
	/** Redirective Blink bone charm: equipped, time stops while aiming Blink without moving (see RedirectiveBlink). */
	public static final Item BONE_CHARM = register("bone_charm_redirective_blink", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "redirective_blink"));
	/** The other bone charms (effects: see BoneCharms and the places that check them). */
	public static final Item CHARM_FALLING_STAR = register("bone_charm_falling_star", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "falling_star"));
	public static final Item CHARM_SCAVENGER = register("bone_charm_scavenger", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "scavenger"));
	public static final Item CHARM_SPIRITUAL_POOL = register("bone_charm_spiritual_pool", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "spiritual_pool"));
	public static final Item CHARM_STRONG_ARMS = register("bone_charm_strong_arms", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "strong_arms"));
	public static final Item CHARM_SWIFT_SHADOW = register("bone_charm_swift_shadow", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "swift_shadow"));
	public static final Item CHARM_WELCOMING_HOST = register("bone_charm_welcoming_host", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "welcoming_host"));
	public static final Item CHARM_WHIRLWIND = register("bone_charm_whirlwind", new BoneCharmItem(new Item.Settings().maxCount(1).rarity(Rarity.RARE), "whirlwind"));
	public static final List<Item> BONE_CHARMS = List.of(BONE_CHARM, CHARM_FALLING_STAR, CHARM_SCAVENGER, CHARM_SPIRITUAL_POOL, CHARM_STRONG_ARMS, CHARM_SWIFT_SHADOW, CHARM_WELCOMING_HOST, CHARM_WHIRLWIND);
	public static final Item PIEROS_REMEDY = register("pieros_remedy", new ElixirItem(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON), ElixirItem.Kind.MANA));

	public static ItemGroup GROUP;

	private ModItems() {}

	private static Item register(String name, Item item) {
		ALL.add(item);
		return Registry.register(Registries.ITEM, DishonoredMod.id(name), item);
	}

	public static void init() {}

	/** Called from the item-group RegisterEvent (after items exist). */
	public static void registerGroup() {
		GROUP = Registry.register(Registries.ITEM_GROUP, DishonoredMod.id("dishonored"),
			ItemGroup.builder()
				.icon(() -> new ItemStack(FOLDING_BLADE))
				.displayName(Text.translatable("itemGroup.dishonored"))
				.entries((context, entries) -> ALL.forEach(entries::add))
				.build());
	}
}
