package com.dishonoredmc.client.hud;

import com.dishonoredmc.DishonoredMod;
import net.minecraft.resource.SynchronousResourceReloader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Dishonored's own HUD sprites (from the private pack, textures/gui/hud/*.png). Reads each PNG's size so
 * sprites can be drawn scaled and bars clipped to a fill fraction.
 */
public final class HudArt implements SynchronousResourceReloader {
	static final String[] SPRITES = {"bar_red_b", "bar_grey", "bar_black", "ink_strip", "ink_backdrop", "vial_red", "vial_cyan",
		"ring_red", "ring_blue", "ring_grey", "ring_glow", "wheel_bkgd", "wheel_disc", "wheel_ring", "wheel_select", "wheel_vial_health", "wheel_vial_mana",
		"vial_blue", "vial_blue_empty", "aware_white", "aware_red", "aware_glow", "aware_bolt", "aware_bolt_dim", "aware_bolt_glow", "vial_outline", "prompt_kill", "prompt_choke", "prompt_drop", "stance_stand", "stance_crouch",
		"mark_outsider", "mark_outsider_glow", "charm_slot",
		"wheel_slot_ring", "wheel_badge", "wheel_noise", "vignette"};

	private static final Map<String, int[]> sizes = new HashMap<>();

	@Override
	public void reload(ResourceManager manager) {
		sizes.clear();
		for (String s : SPRITES) {
			Optional<Resource> res = manager.getResource(id(s));
			if (res.isEmpty()) continue;
			try (InputStream in = res.get().getInputStream()) {
				byte[] head = in.readNBytes(24); // PNG: 8-byte signature, IHDR length+type, then width/height
				int w = ((head[16] & 0xff) << 24) | ((head[17] & 0xff) << 16) | ((head[18] & 0xff) << 8) | (head[19] & 0xff);
				int h = ((head[20] & 0xff) << 24) | ((head[21] & 0xff) << 16) | ((head[22] & 0xff) << 8) | (head[23] & 0xff);
				sizes.put(s, new int[] {w, h});
			} catch (Exception ignored) {
			}
		}
	}

	static Identifier id(String sprite) {
		return DishonoredMod.id("textures/gui/hud/" + sprite + ".png");
	}

	/** True when the pack provides Dishonored's HUD art. */
	public static boolean available() {
		return sizes.containsKey("bar_red_b") && sizes.containsKey("ring_grey");
	}

	public static boolean has(String sprite) {
		return sizes.containsKey(sprite);
	}

	/** How far the screen-edge mask is zoomed in (1 = the whole mask on screen): bigger = thinner dark edge. */
	public static float vignetteZoom = 1.35f;
	/** The top-left health / mana / power block: scale, and its distance from the screen corner (GUI pixels). */
	public static float hudScale = 1.5f, hudPadLeft = 15f, hudPadTop = 0f;

	/** Dishonored's stealth screen mask over the whole screen, zoomed in so only its outer part darkens the edges. */
	public static void drawVignette(DrawContext ctx, int w, int h) {
		int ew = Math.round(w * (vignetteZoom - 1f) / 2f), eh = Math.round(h * (vignetteZoom - 1f) / 2f);
		draw(ctx, "vignette", -ew, -eh, w + 2 * ew, h + 2 * eh);
	}

	/** Draw a whole sprite into the given rectangle. */
	public static void draw(DrawContext ctx, String sprite, int x, int y, int w, int h) {
		int[] s = sizes.get(sprite);
		if (s == null) return;
		ctx.drawTexture(id(sprite), x, y, w, h, 0, 0, s[0], s[1], s[0], s[1]);
	}

	/** Draw the bottom `fraction` of a sprite (vertical stroke fill), keeping it anchored at the bottom. */
	static void drawClippedUp(DrawContext ctx, String sprite, int x, int y, int w, int h, float fraction) {
		int[] s = sizes.get(sprite);
		if (s == null || fraction <= 0) return;
		fraction = Math.min(1f, fraction);
		int dh = Math.max(1, Math.round(h * fraction));
		int region = Math.max(1, Math.round(s[1] * fraction));
		ctx.drawTexture(id(sprite), x, y + h - dh, w, dh, 0, s[1] - region, s[0], region, s[0], s[1]);
	}

	/** Draw the left `fraction` of a sprite (bar fill). */
	static void drawClipped(DrawContext ctx, String sprite, int x, int y, int w, int h, float fraction) {
		int[] s = sizes.get(sprite);
		if (s == null || fraction <= 0) return;
		fraction = Math.min(1f, fraction);
		int dw = Math.max(1, Math.round(w * fraction));
		int region = Math.max(1, Math.round(s[0] * fraction));
		ctx.drawTexture(id(sprite), x, y, dw, h, 0, 0, region, s[1], s[0], s[1]);
	}
}
