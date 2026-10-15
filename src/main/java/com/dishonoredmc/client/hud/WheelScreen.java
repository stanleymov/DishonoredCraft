package com.dishonoredmc.client.hud;

import com.dishonoredmc.DishonoredMod;
import com.dishonoredmc.client.ClientPowerState;
import com.dishonoredmc.client.DishonoredClient;
import com.dishonoredmc.item.ModItems;
import com.dishonoredmc.net.ActionPayload;
import com.dishonoredmc.power.Power;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Dishonored's weapon wheel: hold the wheel key, point at a power or tool, release to pick it. Laid out like the
 * game's: tools and ammo across the top (with counts), powers across the bottom, all with Dishonored's own wheel
 * icons. The elixirs show in the HUD's corner while the wheel is open (their keys still drink).
 */
public class WheelScreen extends Screen {
	private record Entry(Text label, @Nullable Power power, @Nullable Item item, int bowMode, int quick) {}

	private final List<Entry> entries = new ArrayList<>();
	private int hovered = -1;

	/** Powers across the bottom, in Dishonored's order from the left. */
	private static final Power[] POWERS_LEFT_TO_RIGHT = {Power.WINDBLAST, Power.DARK_VISION, Power.BLINK,
		Power.DEVOURING_SWARM, Power.BEND_TIME, Power.POSSESSION};

	public WheelScreen() {
		super(Text.translatable("hud.dishonored.wheel"));
		// Clockwise on screen, starting at the bottom right: the powers (right to left along the bottom), then,
		// clockwise from Windblast: the blade, incendiary bolts, bolts, sleep darts, spring razors, grenades and
		// pistol shot.
		for (int i = POWERS_LEFT_TO_RIGHT.length - 1; i >= 0; i--) {
			Power p = POWERS_LEFT_TO_RIGHT[i];
			entries.add(new Entry(p.displayName(), p, null, -1, -1));
		}
		entries.add(new Entry(ModItems.FOLDING_BLADE.getName(), null, ModItems.FOLDING_BLADE, -1, -1));
		entries.add(new Entry(ModItems.INCENDIARY_BOLT.getName(), null, ModItems.WRIST_BOW, 2, -1));
		entries.add(new Entry(ModItems.CROSSBOW_BOLT.getName(), null, ModItems.WRIST_BOW, 0, -1));
		entries.add(new Entry(ModItems.SLEEP_DART.getName(), null, ModItems.WRIST_BOW, 1, -1));
		entries.add(new Entry(ModItems.SPRING_RAZOR.getName(), null, ModItems.SPRING_RAZOR, -1, -1));
		entries.add(new Entry(ModItems.GRENADE.getName(), null, ModItems.GRENADE, -1, -1));
		entries.add(new Entry(ModItems.PISTOL_SHOT.getName(), null, ModItems.PISTOL, -1, -1));
	}

	@Override
	protected void init() {
		super.init();
		com.dishonoredmc.net.Net.toServer(new ActionPayload(ActionPayload.WHEEL, "", 1));
	}

	@Override
	public void removed() {
		super.removed();
		com.dishonoredmc.net.Net.toServer(new ActionPayload(ActionPayload.WHEEL, "", 0));
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void renderBackground(DrawContext context) {
		context.fill(0, 0, width, height, 0x60000000);
	}

	/** Dishonored's own wheel icon for an entry (textures/gui/wheel, from the pack), or null to draw the item. */
	@Nullable
	private Identifier wheelIcon(Entry e) {
		String name;
		if (e.power != null) {
			name = switch (e.power) {
				case BLINK -> "ic_pow_blink";
				case BEND_TIME -> "ic_pow_bendtime";
				case DARK_VISION -> "ic_pow_darkvision";
				case POSSESSION -> "ic_pow_possession";
				case DEVOURING_SWARM -> "ic_pow_ratswarm";
				case WINDBLAST -> "ic_pow_windblast";
			};
		} else if (e.item == ModItems.FOLDING_BLADE) name = "ic_item_sword";
		else if (e.item == ModItems.PISTOL) name = "ic_item_regularbullet";
		else if (e.item == ModItems.GRENADE) name = "ic_item_regulargrenade";
		else if (e.item == ModItems.SPRING_RAZOR) name = "ic_item_springrazor";
		else if (e.bowMode == 0) name = "ic_item_regularbolt";
		else if (e.bowMode == 1) name = "ic_item_sleepdart";
		else if (e.bowMode == 2) name = "ic_item_flarebolt";
		else return null;
		Identifier id = DishonoredMod.id("textures/gui/wheel/" + name + ".png");
		return iconAvailable.computeIfAbsent(id, k -> MinecraftClient.getInstance().getResourceManager().getResource(k).isPresent()) ? id : null;
	}

	private final java.util.Map<Identifier, Boolean> iconAvailable = new java.util.HashMap<>();

	private ItemStack iconStack(Entry e) {
		if (e.item == ModItems.PISTOL) return new ItemStack(ModItems.PISTOL_SHOT); // shows the shot and its count
		if (e.bowMode == 1) return new ItemStack(ModItems.SLEEP_DART);
		if (e.bowMode == 2) return new ItemStack(ModItems.INCENDIARY_BOLT);
		if (e.bowMode == 0) return new ItemStack(ModItems.CROSSBOW_BOLT);
		return e.item == null ? ItemStack.EMPTY : new ItemStack(e.item);
	}

	private int count(Entry e) {
		var p = MinecraftClient.getInstance().player;
		if (p == null) return 0;
		ItemStack s = iconStack(e);
		return s.isEmpty() ? -1 : p.getInventory().count(s.getItem());
	}

	private boolean usable(Entry e) {
		var p = MinecraftClient.getInstance().player;
		if (e.power != null || p == null) return true;
		if (e.item == ModItems.WRIST_BOW && p.getInventory().count(ModItems.WRIST_BOW) == 0) return false;
		if (e.item == ModItems.PISTOL && p.getInventory().count(ModItems.PISTOL) == 0) return false;
		return p.isCreative() || count(e) > 0;
	}

	/** Screen angle (radians, 0 = right, +y down) of entry i, evenly spaced, the powers centred at the bottom. */
	private double angle(int i) {
		double step = Math.PI * 2 / entries.size();
		return Math.PI / 2 - (POWERS_LEFT_TO_RIGHT.length - 1) / 2.0 * step + i * step;
	}

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
		renderBackground(ctx);
		super.render(ctx, mouseX, mouseY, delta);
		int cx = width / 2, cy = height / 2;
		int radius = Math.round(height * 0.30f);
		int icon = Math.max(16, Math.round(height * 0.083f));
		RenderSystem.enableBlend();
		int bh = (radius + icon) * 2, bw = bh * 6 / 5;
		HudArt.draw(ctx, "wheel_bkgd", cx - bw / 2, cy - bh / 2, bw, bh);
		HudArt.draw(ctx, "wheel_ring", cx - radius - icon / 2 - 3, cy - radius - icon / 2 - 3, (radius + icon / 2 + 3) * 2, (radius + icon / 2 + 3) * 2);
		int inner = Math.round(radius * 1.15f);
		ctx.setShaderColor(0.07f, 0.07f, 0.08f, 0.95f);
		HudArt.draw(ctx, "wheel_disc", cx - inner / 2, cy - inner / 2, inner, inner);
		ctx.setShaderColor(1f, 1f, 1f, 1f);

		// The icons sit inside the ring's band, closer to the centre than its rim (as in Dishonored); the ring art
		// keeps its size.
		int iconRadius = Math.round(radius * 0.85f);
		double dx = mouseX - cx, dy = mouseY - cy;
		hovered = -1;
		if (dx * dx + dy * dy > 10 * 10) {
			double best = Double.MAX_VALUE;
			double m = Math.atan2(dy, dx);
			for (int i = 0; i < entries.size(); i++) {
				double d = Math.abs(Math.atan2(Math.sin(m - angle(i)), Math.cos(m - angle(i))));
				if (d < best) {
					best = d;
					hovered = i;
				}
			}
		}
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			double a = angle(i);
			boolean sel = i == hovered;
			int size = sel ? Math.round(icon * 1.35f) : icon;
			int ix = cx + (int) Math.round(Math.cos(a) * iconRadius) - size / 2, iy = cy + (int) Math.round(Math.sin(a) * iconRadius) - size / 2;
			boolean ok = usable(e);
			// Text drawing (the count badges) switches blending off: without it the icons' half-transparent grey
			// shading draws as solid cream.
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			if (sel) {
				ctx.setShaderColor(1f, 1f, 1f, 0.35f);
				HudArt.draw(ctx, "ring_glow", ix - 3, iy - 3, size + 6, size + 5);
				ctx.setShaderColor(1f, 1f, 1f, 1f);
			}
			ctx.setShaderColor(0.06f, 0.06f, 0.07f, 0.95f);
			HudArt.draw(ctx, "wheel_disc", ix, iy, size, size);
			ctx.setShaderColor(1f, 1f, 1f, ok ? 1f : 0.35f);
			Identifier wheelIcon = wheelIcon(e);
			if (e.power != null) {
				// Power icons carry their own blue ring; the equipped power gets Dishonored's glowing blue ring.
				boolean current = ClientPowerState.selectedPower() == e.power;
				if (current) HudArt.draw(ctx, "ring_blue", ix - 2, iy - 2, size + 4, size + 3);
				if (wheelIcon != null) {
					ctx.drawTexture(wheelIcon, ix, iy, size, size, 0, 0, 100, 100, 100, 100);
				} else {
					int pad = size / 14;
					ctx.drawTexture(DishonoredMod.id("textures/gui/power/" + e.power.key + ".png"), ix + pad, iy + pad, size - 2 * pad, size - 2 * pad, 0, 0, 128, 128, 128, 128);
				}
				ctx.setShaderColor(1f, 1f, 1f, 1f);
			} else {
				// Dishonored's item slot: a dark disc edged by its thin dashed grey ring, the icon inside it.
				ctx.setShaderColor(0.06f, 0.06f, 0.07f, 1f);
				HudArt.draw(ctx, "wheel_disc", ix, iy, size, size);
				ctx.setShaderColor(0.75f, 0.77f, 0.74f, ok ? 0.8f : 0.35f);
				HudArt.draw(ctx, "wheel_ring", ix, iy, size, size); // thin dashed ring (the big wheel ring, scaled down)
				ctx.setShaderColor(1f, 1f, 1f, ok ? 1f : 0.35f);
				int pad = Math.round(size * 0.05f);
				if (wheelIcon != null) {
					ctx.drawTexture(wheelIcon, ix + pad, iy + pad, size - 2 * pad, size - 2 * pad, 0, 0, 100, 100, 100, 100);
					ctx.setShaderColor(1f, 1f, 1f, 1f);
				} else {
					ctx.setShaderColor(1f, 1f, 1f, 1f);
					DishonoredHud.icon(ctx, iconStack(e), ix + pad, iy + pad, size - 2 * pad);
				}
				int n = count(e);
				// The badge sits on the slot's rim, toward the outside of the wheel and a little clockwise (as in Dishonored).
				double ba = a - 0.55;
				if (n >= 0) wheelBadge(ctx, n, ix + size / 2 + (int) Math.round(Math.cos(ba) * size * 0.5),
					iy + size / 2 + (int) Math.round(Math.sin(ba) * size * 0.5), Math.max(14, Math.round(size * 0.5f)));
			}
		}
		if (hovered >= 0) {
			Entry e = entries.get(hovered);
			ctx.drawCenteredTextWithShadow(textRenderer, e.label, cx, cy - 6, 0xFFE9E4D4);
			Text sub = e.power != null ? Text.translatable("hud.dishonored.wheel_cost", (int) e.power.cost)
				: Text.translatable("hud.dishonored.wheel_count", Math.max(0, count(e)));
			ctx.getMatrices().push();
			ctx.getMatrices().translate(cx, cy + 6, 0);
			ctx.getMatrices().scale(0.75f, 0.75f, 1f);
			ctx.drawCenteredTextWithShadow(textRenderer, sub, 0, 0, 0xFF9C988C);
			ctx.getMatrices().pop();
		}

		// Release the wheel key to choose.
		long window = MinecraftClient.getInstance().getWindow().getHandle();
		InputUtil.Key key = DishonoredClient.wheel.getKey();
		boolean held = switch (key.getCategory()) {
			case KEYSYM -> InputUtil.isKeyPressed(window, key.getCode());
			case MOUSE -> org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, key.getCode()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
			default -> true;
		};
		if (!held) choose();
	}

	/** Dishonored's count badge on a wheel slot: a black star on the slot's outer edge, the number in white. */
	private void wheelBadge(DrawContext ctx, int n, int x, int y, int size) {
		String t = String.valueOf(n);
		ctx.getMatrices().push();
		ctx.getMatrices().translate(0, 0, 200);
		if (HudArt.has("wheel_badge")) {
			ctx.setShaderColor(0.04f, 0.04f, 0.04f, 1f); // stored tan; tinted black like the game does
			HudArt.draw(ctx, "wheel_badge", x - size / 2, y - size / 2, size, size);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		}
		float scale = Math.max(0.75f, size / 22f);
		ctx.getMatrices().translate(x, y - 3.5f * scale, 0);
		ctx.getMatrices().scale(scale, scale, 1f);
		ctx.drawText(textRenderer, t, -textRenderer.getWidth(t) / 2, 0, 0xFFFFFFFF, false);
		ctx.getMatrices().pop();
	}

	/** The quick-drink keys still work while the wheel is open. */
	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (DishonoredClient.quickHealth.matchesKey(keyCode, scanCode)) {
			com.dishonoredmc.net.Net.toServer(new ActionPayload(ActionPayload.QUICK_HEALTH, "", -1));
			return true;
		}
		if (DishonoredClient.quickMana.matchesKey(keyCode, scanCode)) {
			com.dishonoredmc.net.Net.toServer(new ActionPayload(ActionPayload.QUICK_MANA, "", -1));
			return true;
		}
		return super.keyPressed(keyCode, scanCode, modifiers);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		InputUtil.Key key = DishonoredClient.wheel.getKey();
		if (key.getCategory() == InputUtil.Type.MOUSE && key.getCode() == button) return true;
		choose();
		return true;
	}

	private void choose() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (hovered >= 0 && client.player != null) {
			Entry e = entries.get(hovered);
			if (e.power != null) {
				ClientPowerState.selected = e.power.ordinal();
				com.dishonoredmc.client.fp.FpController.onSwitchPower();
				// Powers are cast with the blade in hand: picking one draws the blade.
				if (!client.player.getMainHandStack().isOf(ModItems.FOLDING_BLADE)) {
					com.dishonoredmc.net.Net.toServer(new ActionPayload(ActionPayload.EQUIP, Registries.ITEM.getId(ModItems.FOLDING_BLADE).toString(), -1));
				}
			} else if (e.quick >= 0) {
				com.dishonoredmc.net.Net.toServer(new ActionPayload(e.quick, "", -1));
			} else if (e.item != null && usable(e)) {
				com.dishonoredmc.net.Net.toServer(new ActionPayload(ActionPayload.EQUIP, Registries.ITEM.getId(e.item).toString(), e.bowMode));
			}
			client.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.2f);
		}
		close();
	}
}
