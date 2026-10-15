package com.dishonoredmc.client;

import com.dishonoredmc.DishonoredMod;
import com.dishonoredmc.power.Power;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/** Dishonored-style HUD: power icon with red health and blue mana bars top-left, plus screen tints. */
final class PowerHud {
	private static final int HEALTH_RED = 0xFFB0202A;
	private static final int HEALTH_HI = 0xFFE0505A;
	private static final int MANA_BLUE = 0xFF2E6BD6;
	private static final int MANA_HI = 0xFF6FA4FF;
	private static final int BAR_BACK = 0xB0101018;
	private static long nameFlashUntil;
	private static float shownHealth = -1;

	private PowerHud() {}

	static void flashName() {
		nameFlashUntil = System.currentTimeMillis() + 1500;
	}

	private static Identifier icon(Power power) {
		return DishonoredMod.id("textures/gui/power/" + power.key + ".png");
	}

	static void render(DrawContext ctx, float tickDelta) {
		MinecraftClient client = MinecraftClient.getInstance();
		ClientPlayerEntity player = client.player;
		if (player == null || client.options.hudHidden) return;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		TextRenderer font = client.textRenderer;

		float stealth = CorvoMovement.stealth(tickDelta);
		if (stealth > 0.01F) {
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			ctx.setShaderColor(1F, 1F, 1F, 0.75F * stealth);
			com.dishonoredmc.client.hud.HudArt.drawVignette(ctx, w, h);
			ctx.setShaderColor(1F, 1F, 1F, 1F);
		}
		if (ClientPowerState.bendTimeTicks > 0) {
			int alpha = (int) (0x50 * Math.min(1.0, ClientPowerState.bendTimeTicks / 10.0));
			ctx.fill(0, 0, w, h, (alpha << 24) | 0x5A3A10);
		}
		if (ClientPowerState.darkVision) {
			ctx.fill(0, 0, w, h, 0x18203070);
		}
		float aim = ClientPowerState.aimAmount(tickDelta);
		if (aim > 0.01F) {
			ctx.fill(0, 0, w, h, ((int) (0x18 * aim) << 24) | 0x3060C0);
		}

		if (com.dishonoredmc.client.hud.HudArt.available()) {
			com.dishonoredmc.client.hud.DishonoredHud.render(ctx, tickDelta);
			return;
		}

		Power power = ClientPowerState.selectedPower();
		int x = 8;
		int y = 8;

		// Power medallion.
		RenderSystem.enableBlend();
		ctx.drawTexture(icon(power), x, y, 26, 26, 0, 0, 32, 32, 32, 32);

		// Health (red) and mana (blue) bars.
		int bx = x + 30;
		int barW = 110;
		if (!player.isCreative() && !player.isSpectator()) {
			float health = player.getHealth();
			shownHealth = shownHealth < 0 ? health : MathHelper.lerp(0.25F, shownHealth, health);
			float hf = MathHelper.clamp(shownHealth / player.getMaxHealth(), 0F, 1F);
			ctx.fill(bx - 1, y + 1, bx + barW + 1, y + 10, 0xFF000000);
			ctx.fill(bx, y + 2, bx + barW, y + 9, BAR_BACK);
			ctx.fill(bx, y + 2, bx + (int) (barW * hf), y + 9, HEALTH_RED);
			ctx.fill(bx, y + 2, bx + (int) (barW * hf), y + 4, HEALTH_HI);
			float absorption = player.getAbsorptionAmount();
			if (absorption > 0) {
				int aw = (int) (barW * MathHelper.clamp(absorption / player.getMaxHealth(), 0F, 1F));
				ctx.fill(bx, y + 7, bx + aw, y + 9, 0xFFE8C040);
			}
		}

		float frac = MathHelper.clamp(ClientPowerState.mana / ClientPowerState.maxMana, 0F, 1F);
		int my = y + 13;
		ctx.fill(bx - 1, my - 1, bx + barW + 1, my + 6, 0xFF000000);
		ctx.fill(bx, my, bx + barW, my + 5, BAR_BACK);
		ctx.fill(bx, my, bx + (int) (barW * frac), my + 5, MANA_BLUE);
		ctx.fill(bx, my, bx + (int) (barW * frac), my + 2, MANA_HI);
		// A tick where the selected power's cost falls on the bar.
		int costX = bx + (int) (barW * MathHelper.clamp(power.cost / ClientPowerState.maxMana, 0F, 1F));
		ctx.fill(costX, my - 2, costX + 1, my + 7, 0xFFE9D9A6);

		boolean affordable = player.isCreative() || ClientPowerState.mana >= power.minMana;
		ctx.drawTextWithShadow(font, power.displayName(), bx, my + 9, affordable ? 0xFFE9D9A6 : 0xFF8A7F66);

		if (System.currentTimeMillis() < nameFlashUntil) {
			Text name = power.displayName();
			int tw = font.getWidth(name);
			ctx.drawTextWithShadow(font, name, (w - tw) / 2, h / 2 + 14, 0xFFE9D9A6);
		}
	}
}
