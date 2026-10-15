package com.dishonoredmc.client;

import com.dishonoredmc.client.mixin.KeyBindingAccessor;
import com.dishonoredmc.item.FoldingBladeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * Dishonored's layout. Powers are cast with right click (the use key), and only with the blade in hand. The
 * power button (mouse back by default) is always block / choke: with the blade it does what right click did
 * (block, choke, open doors); with anything else it chokes the mob in the crosshair. Other items keep right click
 * for themselves (fire the pistol / crossbow, throw a gadget).
 *
 * Done by trading the two keys' states at the start of each client tick, before Minecraft and the power code
 * read them, from the buttons' real (polled) state. The power code reads the power key, so it only ever sees
 * right click with the blade in hand.
 */
public final class ControlSwap {
	private static boolean active;
	/** This right-click press was used on a door / chest / mob instead: no power until it is released. */
	private static boolean rightUsed;
	/** Blocking with the blade while a gun or gadget is in hand (BladeBlock on the server). */
	private static boolean toolBlocking;

	private ControlSwap() {}

	/** The keys are swapped this tick. */
	public static boolean active() {
		return active;
	}

	public static boolean toolBlocking() {
		return toolBlocking;
	}

	/** The cast button is down (right click with the blade in hand). Safe to call every frame. */
	public static boolean powerHeld(MinecraftClient client) {
		return active && held(client, client.options.useKey);
	}

	/** Client tick start. */
	public static void tick(MinecraftClient client) {
		var p = client.player;
		boolean on = p != null && client.currentScreen == null && p.getMainHandStack().getItem() instanceof FoldingBladeItem;
		KeyBinding use = client.options.useKey, power = DishonoredClient.usePower;
		var useKey = (KeyBindingAccessor) use;
		var powerKey = (KeyBindingAccessor) power;
		if (on) {
			int useClicks = useKey.dishonored$getTimesPressed(), powerClicks = powerKey.dishonored$getTimesPressed();
			boolean right = held(client, use), back = held(client, power);
			if (useClicks > 0 && interact(client, p)) {
				useClicks = 0;
				rightUsed = true;
			}
			if (!right) rightUsed = false;
			use.setPressed(back);
			useKey.dishonored$setTimesPressed(powerClicks);
			power.setPressed(right && !rightUsed);
			powerKey.dishonored$setTimesPressed(useClicks);
		} else {
			if (active) use.setPressed(held(client, use));
			// No powers without the blade: the power button only chokes the mob in the crosshair.
			int chokes = powerKey.dishonored$getTimesPressed();
			power.setPressed(false);
			powerKey.dishonored$setTimesPressed(0);
			if (chokes > 0 && p != null && client.currentScreen == null && client.interactionManager != null
				&& client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
				client.interactionManager.interactEntity(p, hit.getEntity(), net.minecraft.util.Hand.MAIN_HAND);
			}
		}
		// A gun or gadget in hand with the blade out: holding the power button raises the blade to block, and the
		// gun / gadget can't be used meanwhile.
		boolean block = !on && p != null && client.currentScreen == null && FoldingBladeItem.isLeftHandTool(p.getMainHandStack())
			&& FoldingBladeItem.bladeOut(p) && held(client, power);
		if (block != toolBlocking) {
			toolBlocking = block;
			com.dishonoredmc.net.Net.toServer(new com.dishonoredmc.net.ActionPayload(com.dishonoredmc.net.ActionPayload.BLADE_BLOCK, "", block ? 1 : 0));
		}
		if (toolBlocking) {
			use.setPressed(false);
			useKey.dishonored$setTimesPressed(0);
			// No attacking with the guard up (as with the blade itself, where Minecraft ignores attacks while blocking).
			var attack = client.options.attackKey;
			attack.setPressed(false);
			((KeyBindingAccessor) attack).dishonored$setTimesPressed(0);
		}
		active = on;
	}

	/**
	 * Right click with the blade first uses what is under the crosshair, as it would without the swap: a door,
	 * lever, chest, a villager, or a choke from behind. Only when there's nothing to use does it cast the power.
	 */
	private static boolean interact(MinecraftClient client, net.minecraft.client.network.ClientPlayerEntity p) {
		var im = client.interactionManager;
		if (im == null) return false;
		var hand = net.minecraft.util.Hand.MAIN_HAND;
		if (client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
			// The choke is decided on the server; its prompt says whether it will happen.
			boolean choke = com.dishonoredmc.client.hud.DishonoredHud.chokeReady(client, p);
			var r = im.interactEntityAtLocation(p, hit.getEntity(), hit, hand);
			if (!r.isAccepted()) r = im.interactEntity(p, hit.getEntity(), hand);
			if (r.isAccepted() || choke) {
				if (r.shouldSwingHand()) p.swingHand(hand);
				return true;
			}
		} else if (client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit
			&& hit.getType() == net.minecraft.util.hit.HitResult.Type.BLOCK) {
			var r = im.interactBlock(p, hand, hit);
			if (r.isAccepted()) {
				if (r.shouldSwingHand()) p.swingHand(hand);
				return true;
			}
		}
		return false;
	}

	/** Whether the button bound to `key` is physically down right now. */
	static boolean held(MinecraftClient client, KeyBinding key) {
		InputUtil.Key bound = ((KeyBindingAccessor) key).dishonored$getBoundKey();
		long window = client.getWindow().getHandle();
		if (bound.getCategory() == InputUtil.Type.MOUSE) return GLFW.glfwGetMouseButton(window, bound.getCode()) == GLFW.GLFW_PRESS;
		if (bound.getCategory() == InputUtil.Type.KEYSYM && bound.getCode() != -1) return InputUtil.isKeyPressed(window, bound.getCode());
		return false;
	}
}
