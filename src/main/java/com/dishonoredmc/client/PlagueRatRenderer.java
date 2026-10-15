package com.dishonoredmc.client;

import com.dishonoredmc.DishonoredMod;
import com.dishonoredmc.client.fp.WorldMesh;
import com.dishonoredmc.entity.PlagueRatEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.SilverfishEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.mob.SilverfishEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * Plague rats: Dishonored's own rat mesh and animations (run, idle, swarm attack, death) from the private
 * pack, falling back to a re-skinned silverfish without it.
 */
public class PlagueRatRenderer extends SilverfishEntityRenderer {
	/** Each rat's current and previous clip, so changes cross-fade and every clip starts from its first frame. */
	private static final class Anim {
		String clip = "", prevClip = "";
		float start, prevStart;
		boolean loop = true, prevLoop = true, running;
		float lastSwing = -10f;

		void switchTo(String next, boolean asLoop, float now) {
			prevClip = clip;
			prevStart = start;
			prevLoop = loop;
			clip = next;
			start = now;
			loop = asLoop;
		}
	}

	private static final java.util.Map<net.minecraft.entity.Entity, Anim> ANIMS = new java.util.WeakHashMap<>();

	private static final Identifier TEXTURE = DishonoredMod.id("textures/world/rat.png");

	public PlagueRatRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(SilverfishEntity entity) {
		return TEXTURE;
	}

	@Override
	public void render(SilverfishEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		if (!WorldMesh.available("rat") || !(entity instanceof PlagueRatEntity)) {
			super.render(entity, yaw, tickDelta, matrices, consumers, light);
			return;
		}
		float now = (entity.age + tickDelta) / 20f;
		Anim anim = ANIMS.computeIfAbsent(entity, e -> new Anim());
		String want;
		boolean wantLoop = true;
		float speed = entity.limbAnimator.getSpeed(tickDelta);
		// Run/idle with a gap between the thresholds, so the choice can't flicker frame to frame.
		if (anim.running ? speed < 0.04f : speed > 0.1f) anim.running = !anim.running;
		if (entity.deathTime > 0) {
			want = "rat:Death";
			wantLoop = false;
		} else if (entity.age < 30) {
			want = "rat:Spawn";
			wantLoop = false;
		} else if (entity.handSwinging || now - anim.lastSwing < 1.4f) {
			// Fighting: keep the attack cycle looping instead of restarting it on every swing.
			if (entity.handSwinging) anim.lastSwing = now;
			want = "rat:SwarmAttack";
		} else {
			want = anim.running ? "rat:RunN" : "rat:Idle2";
		}
		if (!want.equals(anim.clip)) anim.switchTo(want, wantLoop, now);
		float fade = Math.min(1f, (now - anim.start) / 0.2f);
		matrices.push();
		float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-bodyYaw));
		int overlay = LivingEntityRenderer.getOverlay(entity, 0);
		WorldMesh.renderBlend("rat", anim.prevClip, now - anim.prevStart, anim.prevLoop, anim.clip, now - anim.start, anim.loop,
			anim.prevClip.isEmpty() ? 1f : fade, 1.1f, matrices, consumers, light, overlay);
		matrices.pop();
	}
}
