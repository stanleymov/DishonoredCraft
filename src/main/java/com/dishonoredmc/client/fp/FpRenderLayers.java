package com.dishonoredmc.client.fp;

import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.util.function.Function;

/**
 * Render types for Dishonored's meshes, drawn as real TRIANGLES. Vanilla's entity layers are QUADS, which forced
 * each triangle into a degenerate quad; shader mods (Oculus/Iris) derive per-face tangents and texture centres
 * from those quads, and the broken values made every triangle shade separately.
 *
 * They don't feed Minecraft's entity outline (glowing, Dark Vision) themselves: that buffer is QUADS, and three
 * vertices per triangle drifted out of step and joined neighbouring entities with lines. FpRenderer.draw writes
 * the outline separately, as one quad per triangle.
 */
final class FpRenderLayers extends RenderLayer {
	private FpRenderLayers() {
		super("dishonored_unused", VertexFormats.POSITION, VertexFormat.DrawMode.TRIANGLES, 256, false, false, () -> {}, () -> {});
	}

	static final Function<Identifier, RenderLayer> CUTOUT = Util.memoize(texture -> RenderLayer.of("dishonored_fp_cutout",
		VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.TRIANGLES, 256, true, false,
		MultiPhaseParameters.builder()
			.program(ENTITY_CUTOUT_NONULL_PROGRAM)
			.texture(new RenderPhase.Texture(texture, false, false))
			.transparency(NO_TRANSPARENCY)
			.cull(DISABLE_CULLING)
			.lightmap(ENABLE_LIGHTMAP)
			.overlay(ENABLE_OVERLAY_COLOR)
			.build(false)));

	static final Function<Identifier, RenderLayer> TRANSLUCENT = Util.memoize(texture -> RenderLayer.of("dishonored_fp_translucent",
		VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, VertexFormat.DrawMode.TRIANGLES, 256, true, true,
		MultiPhaseParameters.builder()
			.program(ENTITY_TRANSLUCENT_PROGRAM)
			.texture(new RenderPhase.Texture(texture, false, false))
			.transparency(TRANSLUCENT_TRANSPARENCY)
			.cull(DISABLE_CULLING)
			.lightmap(ENABLE_LIGHTMAP)
			.overlay(ENABLE_OVERLAY_COLOR)
			.build(false)));
}
