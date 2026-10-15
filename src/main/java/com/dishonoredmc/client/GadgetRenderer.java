package com.dishonoredmc.client;

import com.dishonoredmc.client.fp.WorldMesh;
import com.dishonoredmc.entity.SpringRazorEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** Grenades and spring razors as Dishonored's real meshes; spinning in flight, upright once a razor is armed. */
public class GadgetRenderer<T extends ThrownItemEntity> extends EntityRenderer<T> {
	private final String mesh;
	private final float scale;
	private final FlyingItemEntityRenderer<T> fallback;

	public GadgetRenderer(EntityRendererFactory.Context context, String mesh, float scale) {
		super(context);
		this.mesh = mesh;
		this.scale = scale;
		this.fallback = new FlyingItemEntityRenderer<>(context);
	}

	@Override
	public void render(T entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
		// Freshly thrown by the player looking through their own eyes: hide it for its first frames, while it is
		// still right at the camera (the throw animation shows it leaving the hand).
		var client = net.minecraft.client.MinecraftClient.getInstance();
		if (entity.age < 3 && entity.getOwner() == client.player && client.options.getPerspective().isFirstPerson()) return;
		if (!WorldMesh.available(mesh)) {
			fallback.render(entity, yaw, tickDelta, matrices, consumers, light);
			return;
		}
		matrices.push();
		boolean armed = entity instanceof SpringRazorEntity razor && razor.isArmed();
		if (!armed) {
			float spin = (entity.age + tickDelta) * 25f;
			matrices.translate(0, 0.1, 0);
			matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spin));
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(spin * 0.7f));
		}
		WorldMesh.render(mesh, null, 0, false, scale, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
		matrices.pop();
		super.render(entity, yaw, tickDelta, matrices, consumers, light);
	}

	@Override
	public Identifier getTexture(T entity) {
		return new Identifier("textures/misc/white.png");
	}
}
