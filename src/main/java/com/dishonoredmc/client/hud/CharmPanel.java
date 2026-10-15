package com.dishonoredmc.client.hud;

import com.dishonoredmc.BoneCharms;
import com.dishonoredmc.item.ModItems;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraftforge.client.event.ContainerScreenEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * The bone charm panel in the survival inventory: the Mark of the Outsider at the top-left of the player preview
 * glows blue when hovered or open; clicking it shows Corvo's six charm slots in a column left of the inventory.
 */
public final class CharmPanel {
	/** Mark button, relative to the inventory screen (inside the top-left corner of the player preview). */
	private static final int BTN_X = 27, BTN_Y = 9, BTN = 14;
	/** Panel around the slot column (BoneCharms.SLOT_X / SLOT_Y), with a 2 px gap to the inventory. */
	private static final int PANEL_X = BoneCharms.SLOT_X - 6, PANEL_Y = BoneCharms.SLOT_Y - 6, PANEL_W = 28,
		PANEL_H = BoneCharms.SLOTS * 18 + 10;

	/** Stays as the player left it for the rest of the session. */
	private static boolean open;

	private CharmPanel() {}

	public static void init() {
		BoneCharms.panelOpen = CharmPanel::visible;
		var bus = MinecraftForge.EVENT_BUS;
		bus.addListener((ContainerScreenEvent.Render.Background e) -> {
			if (e.getContainerScreen() instanceof InventoryScreen inv) background(e.getGuiGraphics(), inv, e.getMouseX(), e.getMouseY());
		});
		bus.addListener((ScreenEvent.Init.Post e) -> {
			if (e.getScreen() instanceof InventoryScreen inv) e.addListener(new MarkButton(inv));
		});
	}

	/**
	 * The mark as a screen widget, so the screen itself routes clicks to it. It follows the inventory when the
	 * recipe book slides it sideways; the art is drawn in background() (under the slots, over the player preview).
	 */
	private static final class MarkButton extends net.minecraft.client.gui.widget.ClickableWidget {
		private final InventoryScreen inv;

		MarkButton(InventoryScreen inv) {
			super(0, 0, BTN, BTN, Text.translatable("gui.dishonored.bone_charms"));
			this.inv = inv;
			setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(getMessage()));
		}

		@Override
		protected void renderButton(DrawContext ctx, int mouseX, int mouseY, float delta) {
			setX(inv.getGuiLeft() + BTN_X);
			setY(inv.getGuiTop() + BTN_Y);
		}

		@Override
		public void onClick(double mouseX, double mouseY) {
			open = !open;
		}

		@Override
		public void playDownSound(net.minecraft.client.sound.SoundManager sounds) {
			sounds.play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
		}

		@Override
		protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
			appendDefaultNarrations(builder);
		}
	}

	/** The slots are showing: survival inventory, panel open, recipe book closed (it covers the same space). */
	public static boolean visible() {
		return open && MinecraftClient.getInstance().currentScreen instanceof InventoryScreen inv && !inv.getRecipeBookWidget().isOpen();
	}

	/** Clicks on the panel count as inside the screen (not as throwing the held item away). */
	public static boolean inPanel(int left, int top, double mx, double my) {
		return visible() && mx >= left + PANEL_X && mx < left + PANEL_X + PANEL_W && my >= top + PANEL_Y && my < top + PANEL_Y + PANEL_H;
	}

	private static boolean overButton(InventoryScreen inv, double mx, double my) {
		if (inv.getRecipeBookWidget().isOpen() && inv.width < 379) return false; // narrow window: the book covers it
		int x = inv.getGuiLeft() + BTN_X, y = inv.getGuiTop() + BTN_Y;
		return mx >= x && mx < x + BTN && my >= y && my < y + BTN;
	}

	private static void background(DrawContext ctx, InventoryScreen inv, int mx, int my) {
		int left = inv.getGuiLeft(), top = inv.getGuiTop();
		RenderSystem.enableBlend();
		// Mark of the Outsider button.
		boolean lit = open || overButton(inv, mx, my);
		int bx = left + BTN_X, by = top + BTN_Y;
		if (HudArt.has("mark_outsider")) {
			if (lit) {
				ctx.setShaderColor(0.3f, 0.7f, 1f, 1f);
				HudArt.draw(ctx, "mark_outsider_glow", bx, by, BTN, BTN);
				ctx.setShaderColor(0.85f, 0.95f, 1f, 1f);
			} else {
				ctx.setShaderColor(0.62f, 0.62f, 0.66f, 1f);
			}
			HudArt.draw(ctx, "mark_outsider", bx, by, BTN, BTN);
			ctx.setShaderColor(1f, 1f, 1f, 1f);
		} else {
			ctx.drawItem(new ItemStack(ModItems.BONE_CHARM), bx - 1, by - 1);
		}
		if (!visible()) return;

		// Panel: vanilla container style.
		int x0 = left + PANEL_X, y0 = top + PANEL_Y, x1 = x0 + PANEL_W, y1 = y0 + PANEL_H;
		ctx.fill(x0 + 1, y0, x1 - 1, y1, 0xFF000000);
		ctx.fill(x0, y0 + 1, x1, y1 - 1, 0xFF000000);
		ctx.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, 0xFFC6C6C6);
		ctx.fill(x0 + 1, y0 + 1, x1 - 2, y0 + 3, 0xFFFFFFFF);
		ctx.fill(x0 + 1, y0 + 1, x0 + 3, y1 - 2, 0xFFFFFFFF);
		ctx.fill(x0 + 2, y1 - 3, x1 - 1, y1 - 1, 0xFF555555);
		ctx.fill(x1 - 3, y0 + 2, x1 - 1, y1 - 1, 0xFF555555);
		var charms = BoneCharms.of(MinecraftClient.getInstance().player);
		for (int i = 0; i < BoneCharms.SLOTS; i++) {
			int sx = left + BoneCharms.SLOT_X - 1, sy = top + BoneCharms.SLOT_Y + i * 18 - 1;
			ctx.fill(sx, sy, sx + 18, sy + 18, 0xFFFFFFFF);
			ctx.fill(sx, sy, sx + 17, sy + 17, 0xFF373737);
			ctx.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
			// Empty slots show the outline of a charm.
			if (charms.getStack(i).isEmpty()) {
				RenderSystem.enableBlend();
				HudArt.draw(ctx, "charm_slot", sx + 1, sy + 1, 16, 16);
			}
		}
	}
}
