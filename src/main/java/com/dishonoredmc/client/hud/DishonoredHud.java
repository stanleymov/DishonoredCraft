package com.dishonoredmc.client.hud;

import com.dishonoredmc.DishonoredMod;
import com.dishonoredmc.client.ClientPowerState;
import com.dishonoredmc.client.DishonoredClient;
import com.dishonoredmc.item.FoldingBladeItem;
import com.dishonoredmc.item.ModItems;
import com.dishonoredmc.item.WristBowItem;
import com.dishonoredmc.power.Power;
import com.dishonoredmc.power.Takedowns;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

/**
 * Dishonored's HUD, drawn with the game's own sprites from the private pack: slanted red health and blue mana
 * strokes top left beside the power medallion, round hotbar slots, takedown prompts by the crosshair and the
 * crouch silhouette bottom left.
 */
public final class DishonoredHud {
	private static final float STROKE_TILT = 18f;
	private static Power lastPower;
	private static int lastSlot = -1;
	private static long hotbarUntil;
	private static long nameUntil;

	private DishonoredHud() {}

	public static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity p = client.player;
		if (p == null) return;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		TextRenderer font = client.textRenderer;
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();

		float hp = MathHelper.clamp(p.getHealth() / p.getMaxHealth(), 0f, 1f);
		boolean survival = !p.isCreative() && !p.isSpectator();
		if (survival && hp < 0.35f) {
			float pulse = 0.5f + 0.5f * MathHelper.sin((p.age + tickDelta) * 0.25f);
			ctx.setShaderColor(1f, 0.15f, 0.1f, (0.35f - hp) / 0.35f * (0.45f + 0.25f * pulse));
			HudArt.drawVignette(ctx, w, h);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		}

		Power power = ClientPowerState.selectedPower();
		boolean affordable = p.isCreative() || ClientPowerState.mana >= power.minMana;
		PlayerInventory inv = p.getInventory();

		// Top left: health and mana as slanted crystal strokes, with the left hand's power or ammo in a
		// small ring at their foot (proportions taken from Dishonored's HUD).
		float mana = MathHelper.clamp(ClientPowerState.mana / ClientPowerState.maxMana, 0f, 1f);
		// Size and distance from the corner (live: hudscale / hudpad in dishonored_hands.properties).
		com.dishonoredmc.client.HandTuning.maybeReload();
		ctx.getMatrices().push();
		ctx.getMatrices().translate(HudArt.hudPadLeft, HudArt.hudPadTop, 0);
		ctx.getMatrices().scale(HudArt.hudScale, HudArt.hudScale, 1f);
		if (survival) stroke(ctx, "vial_red", 10, 8, 11, 56, hp);
		stroke(ctx, "vial_blue", 20, 23, 9, 43, mana);
		ItemStack held = p.getMainHandStack();
		int ammo = ammoFor(inv, held);
		boolean showAmmo = ammo >= 0 && !held.isOf(ModItems.SOKOLOVS_ELIXIR) && !held.isOf(ModItems.PIEROS_REMEDY);
		int ix = 31, iy = 42, isz = 20;
		disc(ctx, ix, iy, isz, 0.08f);
		HudArt.draw(ctx, showAmmo ? "ring_grey" : affordable ? "ring_blue" : "ring_grey", ix - 1, iy - 1, isz + 2, isz + 1);
		if (showAmmo) {
			icon(ctx, ammoIcon(held), ix + 2, iy + 2, isz - 4);
			badge(ctx, font, ammo, ix - 3, iy + isz - 7);
		} else {
			if (!affordable) ctx.setShaderColor(0.4f, 0.4f, 0.45f, 0.75f);
			ctx.drawTexture(DishonoredMod.id("textures/gui/power/" + power.key + ".png"), ix + 2, iy + 2, isz - 4, isz - 4, 0, 0, 64, 64, 64, 64);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		}
		if (power != lastPower) {
			lastPower = power;
			nameUntil = System.currentTimeMillis() + 2000;
		}
		if (System.currentTimeMillis() < nameUntil) ctx.drawTextWithShadow(font, power.displayName(), ix + isz + 4, iy + 6, 0xFFE9E4D4);
		ctx.getMatrices().pop();
		// Breath under water: a thin bar just below the corner (it follows the corner's size and padding).
		if (p.getAir() < p.getMaxAir()) {
			float s = HudArt.hudScale;
			int ax = Math.round(HudArt.hudPadLeft + 8 * s), ay = Math.round(HudArt.hudPadTop + 70 * s);
			ctx.setShaderColor(0.6f, 0.95f, 1f, 0.9f);
			HudArt.drawClipped(ctx, "bar_grey", ax, ay, Math.round(40 * s), Math.max(3, Math.round(3 * s)), Math.max(0, p.getAir()) / (float) p.getMaxAir());
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		}

		// Bottom left: the crouch silhouette while sneaking.
		if (p.isInSneakingPose()) HudArt.draw(ctx, "stance_crouch", 10, h - 44, 25, 36);

		// Bottom right, only while the weapon wheel is open (as in Dishonored): the two elixirs in red and
		// blue rings, with their quick-drink keys and counts.
		int hn = inv.count(ModItems.SOKOLOVS_ELIXIR), mn = inv.count(ModItems.PIEROS_REMEDY);
		if (client.currentScreen instanceof WheelScreen) {
			elixir(ctx, font, "ring_red", ModItems.SOKOLOVS_ELIXIR, hn, DishonoredClient.quickHealth.getBoundKeyLocalizedText(), w - 30, h - 58);
			elixir(ctx, font, "ring_blue", ModItems.PIEROS_REMEDY, mn, DishonoredClient.quickMana.getBoundKeyLocalizedText(), w - 30, h - 30);
		} else if (!held.isEmpty() && held.isStackable() && !com.dishonoredmc.client.fp.FpController.isFpItem(held)
			&& com.dishonoredmc.power.Possession.clientHostId < 0) {
			// Bottom right: a block or other stackable item in hand, with how many of it the inventory holds.
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			int bx = w - 30, by = h - 35; // room below for the count badge
			disc(ctx, bx, by, 22, 0.08f);
			HudArt.draw(ctx, "ring_grey", bx - 1, by - 1, 24, 23);
			icon(ctx, held, bx + 3, by + 3, 16);
			badge(ctx, font, inv.count(held.getItem()), bx - 4, by + 15);
		}

		boolean possessing = com.dishonoredmc.power.Possession.clientHostId >= 0;
		// Hotbar: small numbered rings that show while switching, then fade away.
		if (inv.selectedSlot != lastSlot) {
			lastSlot = inv.selectedSlot;
			hotbarUntil = System.currentTimeMillis() + 2500;
		}
		long left = hotbarUntil - System.currentTimeMillis();
		if (left > 0 && !possessing) {
			float a = Math.min(1f, left / 400f);
			int slot = 18, gap = 24;
			int hx = w / 2 - (gap * 8 + slot) / 2, hy = h - slot - 6;
			for (int i = 0; i < 9; i++) {
				int x = hx + i * gap;
				boolean sel = i == inv.selectedSlot;
				disc(ctx, x, hy, slot, 0.08f, 0.8f * a);
				ctx.setShaderColor(1f, 1f, 1f, a * (sel ? 1f : 0.7f));
				HudArt.draw(ctx, sel ? "ring_blue" : "ring_grey", x - 1, hy - 1, slot + 2, slot + 1);
				ctx.setShaderColor(1f, 1f, 1f, 1f);
				int labelColor = ((int) (a * 255) << 24) | 0xE9E4D4;
				if (a > 0.1f) ctx.drawTextWithShadow(font, String.valueOf(i + 1), x - 2, hy - 8, labelColor);
				ItemStack s = inv.getStack(i);
				if (s.isEmpty() || a < 0.1f) continue;
				icon(ctx, s, x + 2, hy + 2, slot - 4);
				if (s.getCount() > 1) badge(ctx, font, s.getCount(), x - 3, hy + slot - 7);
			}
		}

		// Takedown prompts just below the crosshair.
		Prompt prompt = takedownPrompt(client, p);
		if (prompt != null) {
			int px = w / 2 - 13, py = h / 2 + 10;
			float pulse = 0.8f + 0.2f * MathHelper.sin((p.age + tickDelta) * 0.4f);
			ctx.setShaderColor(1f, 1f, 1f, pulse);
			HudArt.draw(ctx, prompt.sprite, px, py, 26, 26);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
			ctx.drawTextWithShadow(font, prompt.key, px + 30, py + 9, 0xFFE9E4D4);
			if (prompt.choke) {
				HudArt.draw(ctx, "prompt_choke", px, py + 28, 26, 26);
				ctx.drawTextWithShadow(font, com.dishonoredmc.client.DishonoredClient.useKeyLabel(client), px + 30, py + 37, 0xFFE9E4D4);
			}
		}

		// Low health / low mana: Dishonored's quick-drink prompts.
		int bottom = h - 70;
		if (survival && hp < 0.35f && hn > 0) {
			prompt(ctx, font, "vial_red", Text.translatable("hud.dishonored.drink_health",
				DishonoredClient.quickHealth.getBoundKeyLocalizedText(), hn), 0xFFFF6A5A, w / 2, bottom, p.age + tickDelta);
			bottom -= 26;
		}
		if (!p.isCreative() && ClientPowerState.mana < power.minMana && mn > 0) {
			prompt(ctx, font, "vial_blue", Text.translatable("hud.dishonored.drink_mana",
				DishonoredClient.quickMana.getBoundKeyLocalizedText(), mn), 0xFF7FC8FF, w / 2, bottom, p.age + tickDelta);
		}
	}

	/** Dark backing disc for a round icon. */
	private static void disc(DrawContext ctx, int x, int y, int size, float shade) {
		disc(ctx, x, y, size, shade, 0.8f);
	}

	private static void disc(DrawContext ctx, int x, int y, int size, float shade, float alpha) {
		ctx.setShaderColor(shade, shade, shade, alpha);
		HudArt.draw(ctx, "wheel_disc", x, y, size, size);
		ctx.setShaderColor(1f, 1f, 1f, 1f);
	}

	/** An item icon scaled into a `size` px square. */
	static void icon(DrawContext ctx, ItemStack stack, int x, int y, int size) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x, y, 0);
		ctx.getMatrices().scale(size / 16f, size / 16f, 1f);
		ctx.drawItem(stack, 0, 0);
		ctx.getMatrices().pop();
	}

	/** Dishonored's count badge: white number on its black star (the power wheel's badge art). */
	static void badge(DrawContext ctx, TextRenderer font, int n, int x, int y) {
		String t = String.valueOf(n);
		if (HudArt.has("wheel_badge")) {
			int d = Math.max(12, font.getWidth(t) * 3 / 4 + 6);
			ctx.getMatrices().push();
			ctx.getMatrices().translate(0, 0, 200);
			ctx.setShaderColor(0.05f, 0.05f, 0.05f, 1f); // the art is stored tan; Dishonored tints it black
			HudArt.draw(ctx, "wheel_badge", x - 1, y - 2, d, d);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
			ctx.getMatrices().translate(x - 1 + d / 2f, y - 2 + d / 2f - 2.5f, 0);
			ctx.getMatrices().scale(0.75f, 0.75f, 1f);
			ctx.drawText(font, t, -font.getWidth(t) / 2, 0, 0xFFFFFFFF, false);
			ctx.getMatrices().pop();
			return;
		}
		int d = Math.max(9, font.getWidth(t) * 3 / 4 + 4);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 200);
		ctx.setShaderColor(0.02f, 0.02f, 0.02f, 1f);
		HudArt.draw(ctx, "wheel_disc", x, y, d, 9);
		ctx.setShaderColor(1f, 1f, 1f, 1f);
		ctx.getMatrices().translate(x + d / 2f, y + 2.5f, 0);
		ctx.getMatrices().scale(0.75f, 0.75f, 1f);
		ctx.drawText(font, t, -font.getWidth(t) / 2, 0, 0xFFFFFFFF, false);
		ctx.getMatrices().pop();
	}

	private static void elixir(DrawContext ctx, TextRenderer font, String ring, Item item, int count, Text key, int x, int y) {
		// Text (the previous slot's count) switches blending off: without this the second disc draws solid black.
		com.mojang.blaze3d.systems.RenderSystem.enableBlend();
		com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
		disc(ctx, x, y, 22, 0.08f);
		ctx.setShaderColor(1f, 1f, 1f, count > 0 ? 1f : 0.4f);
		HudArt.draw(ctx, ring, x - 1, y - 1, 24, 23);
		icon(ctx, new ItemStack(item), x + 3, y + 3, 16);
		ctx.setShaderColor(1f, 1f, 1f, 1f);
		ctx.drawTextWithShadow(font, key, x - 8, y - 3, 0xFFB8B4A8);
		badge(ctx, font, count, x - 4, y + 15);
	}

	/** The ammo the held weapon fires (its icon goes in the top-left ring). */
	private static ItemStack ammoIcon(ItemStack held) {
		if (held.isOf(ModItems.PISTOL)) return new ItemStack(ModItems.PISTOL_SHOT);
		if (held.isOf(ModItems.WRIST_BOW)) {
			Item[] ammo = {ModItems.CROSSBOW_BOLT, ModItems.SLEEP_DART, ModItems.INCENDIARY_BOLT};
			return new ItemStack(ammo[WristBowItem.getModeIndex(held)]);
		}
		return new ItemStack(held.getItem());
	}

	/** A slanted crystal stroke: dark empty stroke, then the coloured fill rising from the bottom. */
	private static void stroke(DrawContext ctx, String sprite, int x, int y, int w, int h, float fill) {
		ctx.getMatrices().push();
		ctx.getMatrices().translate(x + w / 2f, y + h / 2f, 0);
		ctx.getMatrices().multiply(RotationAxis.POSITIVE_Z.rotationDegrees(STROKE_TILT));
		ctx.getMatrices().translate(-w / 2f, -h / 2f, 0);
		// The game's own empty crystal when the pack has one (mana), else a darkened copy of the full one.
		if (HudArt.has(sprite + "_empty")) {
			HudArt.draw(ctx, sprite + "_empty", -1, 0, w + 2, h);
		} else {
			ctx.setShaderColor(0.12f, 0.1f, 0.1f, 0.85f);
			HudArt.draw(ctx, sprite, 0, 0, w, h);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		}
		HudArt.drawClippedUp(ctx, sprite, 0, 0, w, h, fill);
		ctx.getMatrices().pop();
	}

	private record Prompt(String sprite, Text key, boolean choke) {}

	/** What a takedown would do right now, so the icon shows before the blow lands. */
	@Nullable
	private static Prompt takedownPrompt(MinecraftClient client, ClientPlayerEntity p) {
		// Nothing while a takedown plays (until it has faded out), or while possessing a creature.
		if (com.dishonoredmc.client.fp.FpController.takedownPlaying() || com.dishonoredmc.power.Possession.clientHostId >= 0) return null;
		boolean blade = FoldingBladeItem.bladeOut(p);
		Text attack = client.options.attackKey.getBoundKeyLocalizedText();
		if (DishonoredClient.dropArmed || dropReady(p)) return new Prompt("prompt_drop", attack, false);
		if (client.targetedEntity instanceof MobEntity mob && mob.isAlive() && !Takedowns.isBoss(mob) && p.distanceTo(mob) < 3.0f) {
			// Unconscious (slept or choked): it can only be killed, from any side.
			if (mob.isAiDisabled()) return blade ? new Prompt("prompt_kill", attack, false) : null;
			// Only an unaware mob can be backstabbed or choked (a drop assassination works on any).
			if (Takedowns.isBehind(p, mob) && !mob.isAttacking() && !AwarenessRenderer.aware(mob.getId())) {
				if (blade) return new Prompt("prompt_kill", attack, true);
				return new Prompt("prompt_choke", com.dishonoredmc.client.DishonoredClient.useKeyLabel(client), false);
			}
		}
		return null;
	}

	/** A choke is possible on the mob in the crosshair right now (its prompt shows). */
	public static boolean chokeReady(MinecraftClient client, ClientPlayerEntity p) {
		Prompt prompt = takedownPrompt(client, p);
		return prompt != null && (prompt.choke || prompt.sprite.equals("prompt_choke"));
	}

	/** Falling with the blade onto a mob: attacking now is a drop assassination. */
	public static boolean dropReady(ClientPlayerEntity p) {
		return FoldingBladeItem.bladeOut(p) && com.dishonoredmc.power.Possession.clientHostId < 0 && !p.isOnGround() && p.getVelocity().y < -0.05
			&& p.fallDistance > 0.4f && Takedowns.mobBelow(p) != null;
	}

	private static void prompt(DrawContext ctx, TextRenderer font, String vial, Text text, int color, int cx, int y, float time) {
		float pulse = 0.65f + 0.35f * MathHelper.sin(time * 0.2f);
		int tw = font.getWidth(text);
		ctx.setShaderColor(1f, 1f, 1f, 0.85f);
		HudArt.draw(ctx, "ink_strip", cx - tw / 2 - 24, y - 6, tw + 40, 24);
		ctx.setShaderColor(1f, 1f, 1f, pulse);
		HudArt.draw(ctx, vial, cx - tw / 2 - 14, y - 4, 6, 20);
		ctx.setShaderColor(1f, 1f, 1f, 1f);
		ctx.drawTextWithShadow(font, text, cx - tw / 2, y + 2, color);
	}

	/** Ammo shown next to the held weapon, or -1 for none. */
	private static int ammoFor(PlayerInventory inv, ItemStack held) {
		Item i = held.getItem();
		if (i == ModItems.PISTOL) return inv.count(ModItems.PISTOL_SHOT);
		if (i == ModItems.WRIST_BOW) {
			Item[] ammo = {ModItems.CROSSBOW_BOLT, ModItems.SLEEP_DART, ModItems.INCENDIARY_BOLT};
			return inv.count(ammo[WristBowItem.getModeIndex(held)]);
		}
		if (i == ModItems.GRENADE || i == ModItems.SPRING_RAZOR || i == ModItems.SOKOLOVS_ELIXIR || i == ModItems.PIEROS_REMEDY) return inv.count(i);
		return -1;
	}
}
