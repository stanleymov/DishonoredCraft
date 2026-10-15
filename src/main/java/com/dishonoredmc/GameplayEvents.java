package com.dishonoredmc;

import com.dishonoredmc.item.ElixirItem;
import com.dishonoredmc.item.FoldingBladeItem;
import com.dishonoredmc.item.ModItems;
import com.dishonoredmc.item.WristBowItem;
import com.dishonoredmc.net.ActionPayload;
import com.dishonoredmc.net.AnimEventPayload;
import com.dishonoredmc.power.Elixirs;
import com.dishonoredmc.power.Takedowns;
import com.dishonoredmc.net.Net;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.util.Hand;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Takedowns, the blade's one-hit block breaking, loot drops, quick-drink and the weapon wheel's equip action. */
final class GameplayEvents {
	private static final Map<UUID, Integer> lastBladeBreak = new HashMap<>();

	private GameplayEvents() {}

	static void register() {
		Net.onServer(ActionPayload.class, (payload, player) -> handleAction(player, payload));

		// Drop assassinations and stealth kills from behind with the folding blade.
		// No attacks with the guard up while a gun or gadget is in hand (BladeBlock); the client stops them too.
		MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.HIGH, (AttackEntityEvent e) -> {
			if (BladeBlock.isBlocking(e.getEntity())) e.setCanceled(true);
		});
		MinecraftForge.EVENT_BUS.addListener((AttackEntityEvent e) -> {
			if (e.getEntity() instanceof ServerPlayerEntity sp && Takedowns.tryAssassinate(sp, e.getTarget())) e.setCanceled(true);
		});

		// Scavenger bone charm: ammo pickups have a 50% chance to be doubled.
		MinecraftForge.EVENT_BUS.addListener((net.minecraftforge.event.entity.player.EntityItemPickupEvent e) -> {
			var itemEntity = e.getItem();
			var stack = itemEntity.getStack();
			if (!com.dishonoredmc.item.Ammo.isAmmo(stack) || !BoneCharms.has(e.getEntity(), ModItems.CHARM_SCAVENGER)) return;
			var data = itemEntity.getPersistentData();
			if (data.getBoolean("dishonored_scavenged")) return;
			data.putBoolean("dishonored_scavenged", true);
			if (e.getEntity().getRandom().nextBoolean()) stack.setCount(Math.min(stack.getMaxCount(), stack.getCount() * 2));
		});

		// Enemies carry Dishonored's supplies.
		MinecraftForge.EVENT_BUS.addListener((LivingDeathEvent e) -> {
			var entity = e.getEntity();
			if (entity.getWorld().isClient || !(entity instanceof Monster) || !(e.getSource().getAttacker() instanceof PlayerEntity)) return;
			Random r = entity.getRandom();
			drop(entity, r, 0.35F, ModItems.PISTOL_SHOT, 1, 3);
			drop(entity, r, 0.30F, ModItems.CROSSBOW_BOLT, 1, 4);
			drop(entity, r, 0.14F, ModItems.SOKOLOVS_ELIXIR, 1, 1);
			drop(entity, r, 0.14F, ModItems.PIEROS_REMEDY, 1, 1);
			drop(entity, r, 0.08F, ModItems.SLEEP_DART, 1, 2);
			drop(entity, r, 0.06F, ModItems.INCENDIARY_BOLT, 1, 2);
			drop(entity, r, 0.04F, ModItems.SPRING_RAZOR, 1, 1);
			drop(entity, r, 0.04F, ModItems.GRENADE, 1, 1);
			drop(entity, r, 0.01F, ModItems.BONE_CHARMS.get(r.nextInt(ModItems.BONE_CHARMS.size())), 1, 1);
		});
	}

	private static void drop(net.minecraft.entity.LivingEntity entity, Random r, float chance, Item item, int min, int max) {
		if (r.nextFloat() < chance) entity.dropStack(new ItemStack(item, min + r.nextInt(max - min + 1)));
	}

	/** A blade swing at a block: the block breaks when the slash lands, BLADE_IMPACT_TICKS later. */
	private record PendingBreak(ServerPlayerEntity player, BlockPos pos, long at) {}

	private static final int BLADE_IMPACT_TICKS = 3;
	private static final java.util.List<PendingBreak> pending = new java.util.ArrayList<>();

	private static void queueBladeBreak(ServerPlayerEntity player, String packedPos, int impactTicks) {
		if (player.isSpectator() || player.isCreative() || !FoldingBladeItem.bladeOut(player) || BladeBlock.isBlocking(player)) return;
		BlockPos pos;
		try {
			pos = BlockPos.fromLong(Long.parseLong(packedPos));
		} catch (NumberFormatException e) {
			return;
		}
		if (player.getEyePos().squaredDistanceTo(pos.toCenterPos()) > 6.0 * 6.0) return;
		// Server time, not player age: age restarts at 0 after a respawn.
		long now = player.getServerWorld().getTime();
		Integer last = lastBladeBreak.get(player.getUuid());
		if (last != null && now - last >= 0 && now - last < 3) return;
		lastBladeBreak.put(player.getUuid(), (int) now);
		// When the slash lands in the swing animation (sent by the client; it scales with the attack speed).
		int delay = impactTicks >= 0 && impactTicks <= 10 ? impactTicks : BLADE_IMPACT_TICKS;
		pending.add(new PendingBreak(player, pos, now + delay));
	}

	/** Called every server tick. */
	static void tickBladeBreaks() {
		for (var it = pending.iterator(); it.hasNext(); ) {
			PendingBreak b = it.next();
			ServerWorld world = b.player().getServerWorld();
			if (world.getTime() < b.at()) continue;
			it.remove();
			if (b.player().isRemoved() || !b.player().isAlive()) continue;
			for (BlockPos pos : swingTargets(b.player(), b.pos())) {
				BlockState state = world.getBlockState(pos);
				if (state.isAir() || state.getHardness(world, pos) < 0) continue;
				bladeBreak(b.player(), world, pos, state);
			}
		}
	}

	private static final int BLOCKS_PER_SWING = 1;
	private static final double SWING_REACH = 5.0;

	/**
	 * The first BLOCKS_PER_SWING solid blocks along the player's line of sight, starting with the block that was
	 * struck: one swing cuts through up to three blocks.
	 */
	private static java.util.List<BlockPos> swingTargets(ServerPlayerEntity player, BlockPos first) {
		java.util.List<BlockPos> out = new java.util.ArrayList<>();
		out.add(first);
		var world = player.getServerWorld();
		var eye = player.getEyePos();
		var dir = player.getRotationVec(1.0F);
		BlockPos last = first;
		for (double t = 0; t <= SWING_REACH && out.size() < BLOCKS_PER_SWING; t += 0.05) {
			BlockPos pos = BlockPos.ofFloored(eye.add(dir.multiply(t)));
			if (pos.equals(last) || out.contains(pos)) continue;
			last = pos;
			// Only blocks beyond the struck one count.
			if (eye.squaredDistanceTo(pos.toCenterPos()) < eye.squaredDistanceTo(first.toCenterPos())) continue;
			BlockState state = world.getBlockState(pos);
			if (!state.isAir() && state.getHardness(world, pos) >= 0 && state.getFluidState().isEmpty()) out.add(pos);
		}
		return out;
	}

	private static void bladeBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState state) {
		// Drop what the right tool would have dropped; the Outsider's blade cuts anything.
		ItemStack tool = state.isIn(BlockTags.PICKAXE_MINEABLE) ? new ItemStack(Items.NETHERITE_PICKAXE)
			: state.isIn(BlockTags.SHOVEL_MINEABLE) ? new ItemStack(Items.NETHERITE_SHOVEL)
			: state.isIn(BlockTags.HOE_MINEABLE) ? new ItemStack(Items.NETHERITE_HOE)
			: new ItemStack(Items.NETHERITE_AXE);
		// The blade cuts anything, but ores, stone and other blocks that need a proper tool give nothing.
		if (!state.isToolRequired()) Block.dropStacks(state, world, pos, world.getBlockEntity(pos), player, tool);
		world.breakBlock(pos, false, player);
		player.getMainHandStack().damage(1, player, p -> p.sendEquipmentBreakStatus(net.minecraft.entity.EquipmentSlot.MAINHAND));
	}

	private static void handleAction(ServerPlayerEntity player, ActionPayload payload) {
		switch (payload.action()) {
			case ActionPayload.QUICK_HEALTH -> Elixirs.drink(player, ElixirItem.Kind.HEALTH);
			case ActionPayload.QUICK_MANA -> Elixirs.drink(player, ElixirItem.Kind.MANA);
			case ActionPayload.EQUIP -> equip(player, payload.item(), payload.arg());
			case ActionPayload.DROP_ASSASSINATE -> Takedowns.tryDropAssassinate(player);
			case ActionPayload.WHEEL -> WheelSlowdown.set(player, payload.arg() == 1);
			case ActionPayload.BLADE_BREAK -> queueBladeBreak(player, payload.item(), payload.arg());
			case ActionPayload.CYCLE_BOLT -> {
				var main = player.getMainHandStack();
				var stack = main.isOf(ModItems.WRIST_BOW) ? main : player.getOffHandStack();
				if (stack.isOf(ModItems.WRIST_BOW)) WristBowItem.cycle(player, stack);
			}
			case ActionPayload.BLADE_BLOCK -> com.dishonoredmc.BladeBlock.set(player, payload.arg() == 1);
			// Only the block / choke button chokes (never right click): the client sends it with the mob's id.
			case ActionPayload.CHOKE -> {
				var target = player.getServerWorld().getEntityById(payload.arg());
				if (target != null && player.distanceTo(target) < 4.0F) Takedowns.tryChoke(player, target);
			}
			case ActionPayload.REDIRECTIVE -> com.dishonoredmc.power.RedirectiveBlink.set(player, payload.arg() == 1);
			default -> {}
		}
	}

	/** Bring an item into the selected hotbar slot (or select it if it is already on the hotbar). */
	private static void equip(ServerPlayerEntity player, String id, int bowMode) {
		Identifier key = Identifier.tryParse(id);
		if (key == null || !Registries.ITEM.containsId(key)) return;
		Item item = Registries.ITEM.get(key);
		var inv = player.getInventory();
		int found = -1;
		for (int i = 0; i < inv.main.size(); i++) {
			if (inv.main.get(i).isOf(item)) {
				found = i;
				break;
			}
		}
		if (found < 0) return;
		if (bowMode >= 0 && item instanceof WristBowItem) WristBowItem.setModeIndex(inv.main.get(found), bowMode);
		if (found < 9) {
			inv.selectedSlot = found;
		} else {
			ItemStack held = inv.main.get(inv.selectedSlot);
			inv.main.set(inv.selectedSlot, inv.main.get(found));
			inv.main.set(found, held);
		}
		player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(inv.selectedSlot));
		player.currentScreenHandler.sendContentUpdates();
	}

	static void tick(ServerPlayerEntity player) {
		Takedowns.tick(player);
	}

	static void forget(UUID id) {
		Takedowns.forget(id);
		lastBladeBreak.remove(id);
	}
}
