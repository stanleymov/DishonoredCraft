package com.dishonoredmc;

import com.dishonoredmc.entity.ModEntities;
import com.dishonoredmc.item.FoldingBladeItem;
import com.dishonoredmc.item.ModItems;
import com.dishonoredmc.net.CastPowerPayload;
import com.dishonoredmc.net.MovementPayload;
import com.dishonoredmc.net.Net;
import com.dishonoredmc.power.BendTime;
import com.dishonoredmc.power.DarkVision;
import com.dishonoredmc.power.Mana;
import com.dishonoredmc.power.Possession;
import com.dishonoredmc.power.Power;
import com.dishonoredmc.power.Powers;
import com.dishonoredmc.power.SleepDarts;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(DishonoredMod.MOD_ID)
public class DishonoredMod {
	public static final String MOD_ID = "dishonored";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Agility (1.20.1 has no jump-strength / safe-fall attributes for players): extra jump velocity and safe fall height. */
	private static final double AGILITY_JUMP_BOOST = 0.12;
	private static final float AGILITY_SAFE_FALL = 4.0F; // vanilla 3 + 4 = 7 blocks without damage

	public static Identifier id(String path) {
		return new Identifier(MOD_ID, path);
	}

	public DishonoredMod() {
		IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
		// Vanilla registries are open for writing only while Forge fires their RegisterEvent.
		modBus.addListener((RegisterEvent e) -> {
			if (e.getRegistryKey().equals(RegistryKeys.SOUND_EVENT)) ModSounds.init();
			else if (e.getRegistryKey().equals(RegistryKeys.ITEM)) ModItems.init();
			else if (e.getRegistryKey().equals(RegistryKeys.ENTITY_TYPE)) ModEntities.init();
			else if (e.getRegistryKey().equals(RegistryKeys.ITEM_GROUP)) ModItems.registerGroup();
		});
		modBus.addListener((EntityAttributeCreationEvent e) -> ModEntities.registerAttributes(e));
		modBus.addListener((FMLCommonSetupEvent e) -> Net.register());
		if (FMLEnvironment.dist == Dist.CLIENT) com.dishonoredmc.client.DishonoredClient.init(modBus);

		GameplayEvents.register();
		Net.onServer(CastPowerPayload.class, (payload, player) -> Powers.cast(player, Power.byIndex(payload.power())));
		// Agility moves are client-driven; the server only clears fall damage for them.
		Net.onServer(MovementPayload.class, (payload, player) -> player.fallDistance = 0);

		IEventBus bus = MinecraftForge.EVENT_BUS;
		bus.addListener((TickEvent.ServerTickEvent e) -> {
			if (e.phase != TickEvent.Phase.END) return;
			var server = e.getServer();
			BendTime.tick(server);
			com.dishonoredmc.power.Awareness.cleanup(server);
			GameplayEvents.tickBladeBreaks();
			SleepDarts.tick(server);
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				Mana.tick(player);
				// No hunger in Dishonored: food stays full. Health never regenerates (gamerule below): drink elixirs.
				player.getHungerManager().setFoodLevel(20);
				player.getHungerManager().setSaturationLevel(0F);
				DarkVision.tick(player);
				Possession.tick(player);
				GameplayEvents.tick(player);
				com.dishonoredmc.power.Takedowns.tickStealth(player);
				com.dishonoredmc.power.Awareness.tick(player);
				FoodHealing.tick(player);
			}
		});

		bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
			if (!(e.getEntity() instanceof ServerPlayerEntity player)) return;
			Mana.sync(player, true);
			BendTime.sendState(player);
			StarterKit.giveOnce(player);
		});
		bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
			if (!(e.getEntity() instanceof ServerPlayerEntity player)) return;
			Possession.end(player);
			DarkVision.forget(player.getUuid());
			Mana.forget(player.getUuid());
			GameplayEvents.forget(player.getUuid());
			BladeBlock.forget(player.getUuid());
			WheelSlowdown.forget(player);
			FoodHealing.forget(player.getUuid());
			com.dishonoredmc.power.RedirectiveBlink.forget(player.getUuid());
		});
		bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
			if (!(e.getEntity() instanceof ServerPlayerEntity player)) return;
			DarkVision.forget(player.getUuid());
			Mana.sync(player, true);
			BendTime.sendState(player);
		});
		// Mana survives death and dimension changes.
		bus.addListener((PlayerEvent.Clone e) -> {
			Mana.copy(e.getOriginal(), e.getEntity());
			com.dishonoredmc.BoneCharms.copy(e.getOriginal(), e.getEntity());
		});
		bus.addListener((EntityJoinLevelEvent e) -> {
			if (e.getLevel().isClient) return;
			Possession.onEntityLoad(e.getEntity());
			SleepDarts.onEntityLoad(e.getEntity());
		});
		// Dishonored rules for every world: you keep your gear when you die, and health only comes back from elixirs.
		bus.addListener((ServerStartedEvent e) -> {
			var server = e.getServer();
			server.getGameRules().get(GameRules.KEEP_INVENTORY).set(true, server);
			server.getGameRules().get(GameRules.NATURAL_REGENERATION).set(false, server);
		});
		bus.addListener((ServerStoppingEvent e) -> WheelSlowdown.reset(e.getServer()));
		bus.addListener((ServerStoppedEvent e) -> {
			BendTime.clear();
			com.dishonoredmc.power.Takedowns.clear();
			com.dishonoredmc.power.Awareness.clear();
			com.dishonoredmc.power.RedirectiveBlink.clear();
		});

		bus.addListener((LivingAttackEvent e) -> {
			var entity = e.getEntity();
			if (entity.getWorld().isClient) return;
			// Getting hit gives the player away at once.
			if (entity instanceof MobEntity m && e.getSource().getAttacker() instanceof ServerPlayerEntity sp) com.dishonoredmc.power.Awareness.alert(m, sp);
			// Sleeping targets wake up when hurt.
			if (entity instanceof MobEntity mob && SleepDarts.isAsleep(mob) && e.getAmount() > 1.0F) SleepDarts.wake(mob);
			// Parrying with the folding blade.
			if (entity instanceof PlayerEntity p && p.isBlocking() && (p.getActiveItem().getItem() instanceof FoldingBladeItem || BladeBlock.isBlocking(p))) {
				p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SWORD_PARRY, SoundCategory.PLAYERS, 1.0F, 1.0F);
			}
		});

		// A gun or gadget in hand keeps the blade out: left click strikes with it.
		bus.addListener((net.minecraftforge.event.TickEvent.PlayerTickEvent e) -> {
			if (e.phase == net.minecraftforge.event.TickEvent.Phase.START) {
				FoldingBladeItem.tickLeftHandTool(e.player);
				BoneCharms.tick(e.player);
			}
		});

		// Food heals over time (there is no hunger).
		bus.addListener((net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Finish e) -> {
			if (e.getEntity() instanceof ServerPlayerEntity p && e.getItem().isFood()) FoodHealing.ate(p, e.getItem().getItem().getFoodComponent());
		});

		// Shadow Kill: whatever Corvo kills crumbles to ash.
		bus.addListener((LivingDeathEvent e) -> {
			var entity = e.getEntity();
			if (!(e.getSource().getAttacker() instanceof PlayerEntity) || entity instanceof PlayerEntity) return;
			if (!(entity.getWorld() instanceof ServerWorld world)) return;
			double h = entity.getHeight();
			world.spawnParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + h / 2, entity.getZ(), 20, entity.getWidth() / 2, h / 3, entity.getWidth() / 2, 0.02);
			world.spawnParticles(ParticleTypes.WHITE_ASH, entity.getX(), entity.getY() + h / 2, entity.getZ(), 80, entity.getWidth() / 2, h / 2, entity.getWidth() / 2, 0.05);
			world.spawnParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + h / 2, entity.getZ(), 10, entity.getWidth() / 2, h / 3, entity.getWidth() / 2, 0.01);
			entity.setInvisible(true);
			world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), ModSounds.SHADOW_KILL, SoundCategory.PLAYERS, 1.0F, 1.0F);
		});


		// A grenade's own thrower takes a light, distance-scaled hit instead of the full blast (twice: damage + crater).
		bus.addListener((net.minecraftforge.event.level.ExplosionEvent.Detonate e) -> {
			if (!(e.getExplosion().getEntity() instanceof com.dishonoredmc.entity.GrenadeEntity grenade)) return;
			// The crater blast only breaks blocks: creatures are hurt by the first blast alone.
			if (grenade.craterBlast) {
				e.getAffectedEntities().clear();
				return;
			}
			if (!(grenade.getOwner() instanceof PlayerEntity owner) || !e.getAffectedEntities().remove(owner)) return;
			double dist = owner.getPos().distanceTo(grenade.getPos());
			float dmg = (float) Math.max(0, 5.0 * (1.0 - dist / 4.0));
			if (dmg > 0) owner.damage(owner.getDamageSources().explosion(grenade, owner), dmg);
		});

		// Agility: higher jumps and no fall damage from moderate drops.
		bus.addListener((LivingEvent.LivingJumpEvent e) -> {
			if (e.getEntity() instanceof PlayerEntity p) p.setVelocity(p.getVelocity().add(0, AGILITY_JUMP_BOOST, 0));
		});
		bus.addListener((LivingFallEvent e) -> {
			if (e.getEntity() instanceof PlayerEntity) e.setDistance(Math.max(0, e.getDistance() - AGILITY_SAFE_FALL));
		});

		LOGGER.info("The Outsider has marked you. Dishonored powers loaded.");
	}
}
