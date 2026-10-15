package com.dishonoredmc.client;

import com.dishonoredmc.entity.BoltEntity;
import net.minecraft.client.render.entity.ArrowEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.ProjectileEntityRenderer;
import net.minecraft.util.Identifier;

/** Bolts use the vanilla arrow textures from the player's own game install. */
public class BoltRenderer extends ProjectileEntityRenderer<BoltEntity> {
	private static final Identifier TIPPED = new Identifier("textures/entity/projectiles/tipped_arrow.png");

	public BoltRenderer(EntityRendererFactory.Context context) {
		super(context);
	}

	@Override
	public Identifier getTexture(BoltEntity entity) {
		return entity.getKind() == BoltEntity.Kind.BOLT ? ArrowEntityRenderer.TEXTURE : TIPPED;
	}
}
