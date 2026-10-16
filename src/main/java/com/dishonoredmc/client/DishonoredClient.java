package com.dishonoredmc.client;

import com.dishonoredmc.ModSounds;
import com.dishonoredmc.entity.ModEntities;
import com.dishonoredmc.net.CastPowerPayload;
import com.dishonoredmc.net.ManaSyncPayload;
import com.dishonoredmc.net.PowerStatePayload;
import com.dishonoredmc.power.BlinkTargeting;
import com.dishonoredmc.power.Power;
import com.dishonoredmc.net.Net;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class DishonoredClient {
	private static final String CATEGORY = "category.dishonored";
	public static final KeyBinding usePower = new KeyBinding("key.dishonored.use_power", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_4, CATEGORY);
	public static final KeyBinding nextPower = new KeyBinding("key.dishonored.next_power", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);
	public static final KeyBinding prevPower = new KeyBinding("key.dishonored.prev_power", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);
	public static final KeyBinding lean = new KeyBinding("key.dishonored.lean", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
	public static final KeyBinding wheel = new KeyBinding("key.dishonored.wheel", InputUtil.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_5, CATEGORY);
	public static final KeyBinding quickHealth = new KeyBinding("key.dishonored.quick_health", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, CATEGORY);
	public static final KeyBinding quickMana = new KeyBinding("key.dishonored.quick_mana", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
	public static final KeyBinding cancelPower = new KeyBinding("key.dishonored.cancel_power", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F, CATEGORY);
	/** Turns vanilla's "can't walk off ledges while sneaking" on and off. */
	/** Switches the wrist bow between bolts, sleep darts and incendiary bolts. */
	public static final KeyBinding cycleBolt = new KeyBinding("key.dishonored.cycle_bolt", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);
	public static final KeyBinding edgeGuard = new KeyBinding("key.dishonored.edge_guard", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);
	/** Blink was cancelled while aiming: ignore the power key until it is released. */
	private static boolean blinkCancelled;
	private static boolean bladeWasOut;
	private static boolean jumpWasDown;
	/** A drop assassination is lined up and will land when Corvo reaches the victim. */
	public static boolean dropArmed;
	private static int lastStrike = -1000;

	/** Wires the client side into Forge's mod and game event buses (called from the mod constructor on the client). */
	public static void init(IEventBus modBus) {
		modBus.addListener((RegisterKeyMappingsEvent e) -> {
			for (KeyBinding k : new KeyBinding[] {usePower, nextPower, prevPower, lean, wheel, quickHealth, quickMana, cancelPower, edgeGuard, cycleBolt}) e.register(k);
		});
		com.dishonoredmc.client.hud.CharmPanel.init();
		modBus.addListener((RegisterClientReloadListenersEvent e) -> {
			e.registerReloadListener(new com.dishonoredmc.client.hud.HudArt());
			e.registerReloadListener(new com.dishonoredmc.client.fp.FpAssets());
		});
		modBus.addListener((RegisterGuiOverlaysEvent e) ->
			// Below the F3 debug text, so the debug screen stays readable.
			e.registerBelow(VanillaGuiOverlay.DEBUG_TEXT.id(), "hud", (gui, ctx, partialTick, w, h) -> PowerHud.render(ctx, partialTick)));
		modBus.addListener((EntityRenderersEvent.RegisterRenderers e) -> {
			e.registerEntityRenderer(ModEntities.PLAGUE_RAT, PlagueRatRenderer::new);
			e.registerEntityRenderer(ModEntities.BOLT, BoltRenderer::new);
			e.registerEntityRenderer(ModEntities.GRENADE, ctx -> new GadgetRenderer<>(ctx, "grenade", 1.8f));
			e.registerEntityRenderer(ModEntities.SPRING_RAZOR, ctx -> new GadgetRenderer<>(ctx, "spring_razor", 1.6f));
			e.registerEntityRenderer(ModEntities.BULLET, ctx -> new FlyingItemEntityRenderer<>(ctx, 0.35f, true));
		});

		Net.onClient(ManaSyncPayload.class, payload -> {
			ClientPowerState.mana = payload.mana();
			ClientPowerState.maxMana = payload.max();
		});
		Net.onClient(PowerStatePayload.class, payload -> {
			if (ClientPowerState.bendTimeTicks <= 0 && payload.bendTimeTicks() > 0) BendTimeSound.start();
			ClientPowerState.bendTimeTicks = payload.bendTimeTicks();
			ClientPowerState.darkVision = payload.darkVision();
			com.dishonoredmc.power.Possession.clientHostId = payload.possessedId();
		});
		Net.onClient(com.dishonoredmc.net.AwarenessPayload.class, com.dishonoredmc.client.hud.AwarenessRenderer::receive);
		Net.onClient(com.dishonoredmc.net.AnimEventPayload.class,
			payload -> com.dishonoredmc.client.fp.FpController.onAnimEvent(payload.event()));

		IEventBus bus = MinecraftForge.EVENT_BUS;
		// Hearts are replaced by Dishonored's red health stroke; with the game's own HUD art in the pack, the hotbar,
		// food / armor / air rows and XP bar go too (DishonoredHud draws its own versions).
		bus.addListener((RenderGuiOverlayEvent.Pre e) -> {
			var id = e.getOverlay().id();
			if (id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) e.setCanceled(true);
			else if (com.dishonoredmc.client.hud.HudArt.available() && (id.equals(VanillaGuiOverlay.HOTBAR.id())
				|| id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()) || id.equals(VanillaGuiOverlay.ARMOR_LEVEL.id())
				|| id.equals(VanillaGuiOverlay.AIR_LEVEL.id()) || id.equals(VanillaGuiOverlay.EXPERIENCE_BAR.id())
				|| id.equals(VanillaGuiOverlay.MOUNT_HEALTH.id()))) e.setCanceled(true);
		});
		// Detection gauges above hostile mobs' heads.
		bus.addListener((net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> e) ->
			com.dishonoredmc.client.hud.AwarenessRenderer.render(e.getEntity(), e.getPoseStack(), e.getMultiBufferSource()));
		bus.addListener((net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered e) -> {
			var pl = MinecraftClient.getInstance().player;
			if (e.isAttack() && pl != null) com.dishonoredmc.client.fp.FpController.lastAttackInput = pl.age;
		});
		bus.addListener((TickEvent.RenderTickEvent e) -> {
			if (e.phase == TickEvent.Phase.START) RedirectiveClient.frame(MinecraftClient.getInstance());
		});
		bus.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
			ClientPowerState.reset();
			com.dishonoredmc.TimeScale.reset();
			com.dishonoredmc.power.Possession.clientHostId = -1;
			com.dishonoredmc.client.hud.AwarenessRenderer.reset();
			com.dishonoredmc.power.RedirectiveBlink.clientFrozen = false;
		});
		// No hold-to-mine with the blade: blocks break only when a swing lands (see bladeStrike).
		bus.addListener((net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock e) -> {
			if (e.getLevel().isClient && !e.getEntity().isCreative()
				&& com.dishonoredmc.item.FoldingBladeItem.bladeOut(e.getEntity())) e.setCanceled(true);
		});
		bus.addListener((TickEvent.ClientTickEvent e) -> {
			MinecraftClient client = MinecraftClient.getInstance();
			if (e.phase == TickEvent.Phase.START) {
				// Inside a possessed creature the attack button does nothing (no swing, no hit, no block breaking).
				if (com.dishonoredmc.power.Possession.clientHostId >= 0) {
					var attack = client.options.attackKey;
					attack.setPressed(false);
					((com.dishonoredmc.client.mixin.KeyBindingAccessor) attack).dishonored$setTimesPressed(0);
				}
				ControlSwap.tick(client);
				dropAssassination(client);
				bladeStrike(client);
			}
			else tick(client);
		});
	}

	private static void tick(MinecraftClient client) {
		if (ClientPowerState.bendTimeTicks > 0) ClientPowerState.bendTimeTicks--;
		ClientPowerState.tick();
		CorvoMovement.tick(client);
		com.dishonoredmc.client.fp.FpController.tick(client);
		if (client.player == null || client.world == null) return;

		// Corvo's blade snaps open when drawn.
		boolean bladeOut = com.dishonoredmc.item.FoldingBladeItem.bladeOut(client.player);
		if (bladeOut && !bladeWasOut) client.player.playSound(com.dishonoredmc.ModSounds.SWORD_UNFOLD, 0.8F, 1.0F);
		bladeWasOut = bladeOut;

		// Toggle-crouch: crouching in water sinks; pressing jump while crouched in water stands Corvo up. The sticky
		// sneak key only flips on a press, so one press toggles it off.
		boolean jumpDown = client.options.jumpKey.isPressed();
		if (jumpDown && !jumpWasDown && client.player.isTouchingWater() && client.options.getSneakToggled().getValue()
			&& client.options.sneakKey.isPressed()) {
			client.options.sneakKey.setPressed(true);
		}
		jumpWasDown = jumpDown;
		while (cycleBolt.wasPressed()) {
			if (client.player.getMainHandStack().isOf(com.dishonoredmc.item.ModItems.WRIST_BOW) || client.player.getOffHandStack().isOf(com.dishonoredmc.item.ModItems.WRIST_BOW)) {
				com.dishonoredmc.net.Net.toServer(new com.dishonoredmc.net.ActionPayload(com.dishonoredmc.net.ActionPayload.CYCLE_BOLT, "", -1));
			}
		}
		while (edgeGuard.wasPressed()) {
			com.dishonoredmc.EdgeGuard.enabled = !com.dishonoredmc.EdgeGuard.enabled;
			client.player.sendMessage(net.minecraft.text.Text.translatable(com.dishonoredmc.EdgeGuard.enabled
				? "message.dishonored.edge_guard_on" : "message.dishonored.edge_guard_off"), true);
		}
		while (quickHealth.wasPressed()) com.dishonoredmc.net.Net.toServer(new com.dishonoredmc.net.ActionPayload(com.dishonoredmc.net.ActionPayload.QUICK_HEALTH, "", -1));
		while (quickMana.wasPressed()) com.dishonoredmc.net.Net.toServer(new com.dishonoredmc.net.ActionPayload(com.dishonoredmc.net.ActionPayload.QUICK_MANA, "", -1));
		while (wheel.wasPressed()) if (client.currentScreen == null) client.setScreen(new com.dishonoredmc.client.hud.WheelScreen());
		while (nextPower.wasPressed()) cycle(client, 1);
		while (prevPower.wasPressed()) cycle(client, -1);

		Power power = ClientPowerState.selectedPower();
		if (!ControlSwap.active()) {
			// Powers need the blade in hand: putting it away mid-aim lowers the marked hand without blinking.
			if (ClientPowerState.aimingBlink) {
				ClientPowerState.aimingBlink = false;
				com.dishonoredmc.client.fp.FpController.onBlinkAim(false);
			}
			while (cancelPower.wasPressed()) {}
		} else if (power == Power.BLINK) {
			// Hold to aim, release to Blink, as in Dishonored.
			while (usePower.wasPressed()) {}
			boolean cancel = false;
			while (cancelPower.wasPressed()) cancel = true;
			if (!usePower.isPressed()) blinkCancelled = false;
			if (cancel && ClientPowerState.aimingBlink) {
				// Put the marked hand down without blinking.
				ClientPowerState.aimingBlink = false;
				blinkCancelled = true;
				com.dishonoredmc.client.fp.FpController.onBlinkAim(false);
				client.player.playSound(ModSounds.POWER_SWITCH, 0.4F, 0.8F);
			}
			if (usePower.isPressed() && !blinkCancelled && client.currentScreen == null && !ClientPowerState.aimingBlink
				&& !ClientPowerState.canAfford(client.player, power)) {
				// Not enough mana (and no Piero's Remedy to drink): no aim, the hand shows it is out of mana, and the
				// server says so. Nothing more until the button is released.
				blinkCancelled = true;
				com.dishonoredmc.client.fp.FpController.onCast(power);
				com.dishonoredmc.net.Net.toServer(new CastPowerPayload(power.ordinal()));
			}
			if (usePower.isPressed() && !blinkCancelled && client.currentScreen == null) {
				if (!ClientPowerState.aimingBlink) com.dishonoredmc.client.fp.FpController.onBlinkAim(true);
				ClientPowerState.aimingBlink = true;
				blinkPreview(client);
			} else if (ClientPowerState.aimingBlink) {
				ClientPowerState.aimingBlink = false;
				com.dishonoredmc.client.fp.FpController.onBlinkAim(false);
				ClientPowerState.startCastAnimation();
				com.dishonoredmc.net.Net.toServer(new CastPowerPayload(power.ordinal()));
			}
		} else {
			ClientPowerState.aimingBlink = false;
			while (usePower.wasPressed()) {
				com.dishonoredmc.client.fp.FpController.onCast(power);
				ClientPowerState.startCastAnimation();
				com.dishonoredmc.net.Net.toServer(new CastPowerPayload(power.ordinal()));
			}
		}
	}

	/**
	 * Attack while the drop-assassination prompt shows: lock onto the mob below, as Dishonored does. Runs
	 * before Minecraft reads the attack key, so the click becomes the takedown instead of a swing at air.
	 */
	/**
	 * The folding blade breaks the block it strikes, one block per swing (like an attack, with the sword's attack
	 * cooldown), instead of mining while the button is held.
	 */
	private static void bladeStrike(MinecraftClient client) {
		var p = client.player;
		if (p == null || client.currentScreen != null || p.isSpectator() || p.isCreative()) return;
		if (!com.dishonoredmc.item.FoldingBladeItem.bladeOut(p)) return;
		// A drop assassination lined up (or any takedown playing) owns the attack button: no cutting the block
		// under the crosshair with it.
		if (dropArmed || com.dishonoredmc.client.hud.DishonoredHud.dropReady(p) || com.dishonoredmc.client.fp.FpController.takedownPlaying()) return;
		boolean held = client.options.attackKey.isPressed();
		// Only cutting blocks repeats while the button is held; fighting takes a click per swing.
		if (!(client.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit)
			|| hit.getType() != net.minecraft.util.hit.HitResult.Type.BLOCK) return;
		boolean pressed = false;
		while (client.options.attackKey.wasPressed()) pressed = true;
		if (!(pressed || held) || p.getAttackCooldownProgress(0f) < 0.9f) return;
		// Holding the button repeats a strike when the sword is ready again (attack speed, so Whirlwind counts).
		if (!pressed && (p.getAttackCooldownProgress(0f) < 1.0f || p.age - lastStrike < 4) && p.age >= lastStrike) return;
		lastStrike = p.age;
		com.dishonoredmc.client.fp.FpController.lastAttackInput = p.age;
		p.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		p.resetLastAttackedTicks();
		com.dishonoredmc.net.Net.toServer(new com.dishonoredmc.net.ActionPayload(
			com.dishonoredmc.net.ActionPayload.BLADE_BREAK, Long.toString(hit.getBlockPos().asLong()),
			Math.round(com.dishonoredmc.client.fp.FpController.SLASH_HIT / com.dishonoredmc.client.fp.FpController.swingSpeed(p) * 20f)));
	}

	private static void dropAssassination(MinecraftClient client) {
		if (client.player == null || client.player.isOnGround()) dropArmed = false;
		if (client.player == null || client.currentScreen != null) return;
		boolean ready = com.dishonoredmc.client.hud.DishonoredHud.dropReady(client.player);
		if (!ready && !dropArmed) return;
		boolean pressed = false;
		while (client.options.attackKey.wasPressed()) pressed = true;
		if (pressed && ready && !dropArmed) {
			dropArmed = true;
			com.dishonoredmc.net.Net.toServer(new com.dishonoredmc.net.ActionPayload(com.dishonoredmc.net.ActionPayload.DROP_ASSASSINATE, "", -1));
			com.dishonoredmc.client.fp.FpController.lastAttackInput = client.player.age;
			client.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		}
	}

	/** The block / choke button (see ControlSwap). */
	public static net.minecraft.text.Text useKeyLabel(MinecraftClient client) {
		return usePower.getBoundKeyLocalizedText();
	}

	/** Counts Blink preview updates (game ticks, or real-time steps while time is slowed: see RedirectiveClient). */
	private static int previewStep;

	/** The Blink target preview: a ring where Corvo lands (and the mantle arrow when it climbs a ledge). */
	public static void blinkPreview(MinecraftClient client) {
		if (client.player == null || client.world == null) return;
		previewStep++;
		BlinkTargeting.Target aimed = BlinkTargeting.target(client.player);
		Vec3d target = aimed == null ? null : aimed.feet();
		if (aimed != null && aimed.mantle() != null) mantleArrow(client, aimed.mantle());
		if (target != null) {
			for (int i = 0; i < 6; i++) {
				double a = (previewStep * 0.3) + i * Math.PI / 3;
				client.world.addParticle(ParticleTypes.REVERSE_PORTAL, target.x + Math.cos(a) * 0.45, target.y + 0.1, target.z + Math.sin(a) * 0.45, 0, 0.02, 0);
			}
			client.world.addParticle(ParticleTypes.WITCH, target.x, target.y + 1.0, target.z, 0, 0, 0);
		}
	}

	/**
	 * Blink aimed at a ledge it will mantle: an arrow of particles flat against the wall's face, from the aimed point
	 * up to the edge, its head at the top and a short trail over the edge onto the ledge.
	 */
	private static void mantleArrow(MinecraftClient client, BlinkTargeting.Mantle m) {
		if (previewStep % 2 != 0) return;
		var dust = new net.minecraft.particle.DustParticleEffect(new org.joml.Vector3f(0.62f, 0.45f, 1.0f), 0.6f);
		Vec3d n = new Vec3d(m.side().getOffsetX(), 0, m.side().getOffsetZ());
		Vec3d r = new Vec3d(-n.z, 0, n.x);
		Vec3d face = m.hit().add(n.multiply(0.06));
		double tip = m.ledgeY() - 0.08;
		double bottom = Math.min(m.hit().y, tip - 0.7);
		for (double y = bottom; y <= tip - 0.2; y += 0.13) client.world.addParticle(dust, face.x, y, face.z, 0, 0, 0);
		for (double t = 0.07; t <= 0.3; t += 0.075) {
			Vec3d a = face.add(r.multiply(t)), b = face.subtract(r.multiply(t));
			client.world.addParticle(dust, a.x, tip - t, a.z, 0, 0, 0);
			client.world.addParticle(dust, b.x, tip - t, b.z, 0, 0, 0);
		}
		client.world.addParticle(dust, face.x, tip, face.z, 0, 0, 0);
		for (double d = 0.12; d <= 0.5; d += 0.13) {
			client.world.addParticle(dust, face.x - n.x * (0.06 + d), m.ledgeY() + 0.06, face.z - n.z * (0.06 + d), 0, 0, 0);
		}
	}

	private static void cycle(MinecraftClient client, int dir) {
		ClientPowerState.aimingBlink = false;
		ClientPowerState.selected = Math.floorMod(ClientPowerState.selected + dir, Power.values().length);
		client.player.playSound(ModSounds.POWER_SWITCH, 0.6F, 1.0F);
		PowerHud.flashName();
		com.dishonoredmc.client.fp.FpController.onSwitchPower();
	}
}
