package com.ironcoffee.retrograde.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
//? if <26 {
import net.minecraft.client.gui.GuiGraphics;
//?} else {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?}

/**
 * The handful of draw calls this mod's screens actually make, behind one
 * type.
 *
 * 26.x replaced Screen's render(GuiGraphics, ...) with
 * extractRenderState(GuiGraphicsExtractor, ...) - a real rendering pipeline
 * change, not a rename - and the draw methods were renamed and reshaped
 * along with it. Without something like this every screen has to carry two
 * copies of its entire layout code, one per era, which is exactly how this
 * mod's map screen got unmaintainable: the tooltip was ~70 lines written
 * twice, and every fix had to be made in both.
 *
 * So: screens take a Painter, the Stonecutter branches live here and
 * nowhere else, and layout is written once.
 */
final class Painter {
	/** Chunk terrain textures are always 16x16 (see ChunkTerrainSample). */
	private static final int CHUNK_TEXTURE_SIZE = 16;

	//? if <26 {
	private final GuiGraphics graphics;

	Painter(GuiGraphics graphics) {
		this.graphics = graphics;
	}
	//?} else {
	/*private final GuiGraphicsExtractor graphics;

	Painter(GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}
	*///?}

	// fill() kept its name and shape across the split, so it needs no branch.
	void fill(int x1, int y1, int x2, int y2, int color) {
		graphics.fill(x1, y1, x2, y2, color);
	}

	// So did the scissor pair - same names, same four-corner signature, in
	// both eras - which is what makes a scrollable list safe to clip rather
	// than letting it bleed into whatever panel sits below its viewport.
	void scissor(int x1, int y1, int x2, int y2) {
		graphics.enableScissor(x1, y1, x2, y2);
	}

	void resetScissor() {
		graphics.disableScissor();
	}

	void text(Font font, Component text, int x, int y, int color) {
		//? if <26 {
		graphics.drawString(font, text, x, y, color);
		//?} else {
		/*graphics.text(font, text, x, y, color);
		*///?}
	}

	void text(Font font, String text, int x, int y, int color) {
		//? if <26 {
		graphics.drawString(font, text, x, y, color);
		//?} else {
		/*graphics.text(font, text, x, y, color);
		*///?}
	}

	void centeredText(Font font, Component text, int x, int y, int color) {
		//? if <26 {
		graphics.drawCenteredString(font, text, x, y, color);
		//?} else {
		/*graphics.centeredText(font, text, x, y, color);
		*///?}
	}

	void item(ItemStack stack, int x, int y) {
		//? if <26 {
		graphics.renderItem(stack, x, y);
		//?} else {
		/*graphics.item(stack, x, y);
		*///?}
	}

	/**
	 * One chunk's terrain texture, scaled to whatever the current zoom
	 * level calls for. blit() went from destination size + pixel UV offsets
	 * to destination corners + UV fractions in 26.x.
	 */
	void chunkTexture(ResourceLocation texture, int x, int y, int size) {
		//? if <26 {
		graphics.blit(texture, x, y, size, size, 0.0F, 0.0F, CHUNK_TEXTURE_SIZE, CHUNK_TEXTURE_SIZE, CHUNK_TEXTURE_SIZE, CHUNK_TEXTURE_SIZE);
		//?} else {
		/*graphics.blit(texture, x, y, x + size, y + size, 0.0F, 1.0F, 0.0F, 1.0F);
		*///?}
	}

	// Everything below is built out of fill() alone, so it's shared.

	/** Filled box with a light top/left and dark bottom/right edge. */
	void panel(int x, int y, int w, int h, int background, int lightEdge, int darkEdge) {
		fill(x, y, x + w, y + h, background);
		fill(x, y, x + w, y + 1, lightEdge);
		fill(x, y, x + 1, y + h, lightEdge);
		fill(x, y + h - 1, x + w, y + h, darkEdge);
		fill(x + w - 1, y, x + w, y + h, darkEdge);
	}

	/** One-pixel rectangle outline, nothing filled in. */
	void outline(int x, int y, int w, int h, int color) {
		fill(x, y, x + w, y + 1, color);
		fill(x, y + h - 1, x + w, y + h, color);
		fill(x, y, x + 1, y + h, color);
		fill(x + w - 1, y, x + w, y + h, color);
	}

	/** Horizontal progress bar. Fraction is clamped to 0..1. */
	void progressBar(int x, int y, int w, int h, float fraction, int trackColor, int barColor) {
		fill(x, y, x + w, y + h, trackColor);
		int filled = (int) (Math.max(0.0F, Math.min(1.0F, fraction)) * (w - 2));
		if (filled > 0) {
			fill(x + 1, y + 1, x + 1 + filled, y + h - 1, barColor);
		}
	}

	// ----- overlay system primitives -----
	//
	// Added for the map's chunk-manipulation and settings overlays. Same
	// rule as everything above: built out of fill() alone, so there is no
	// texture to go blurry at an odd GUI scale and nothing here needs a
	// Stonecutter branch of its own. Every screen and overlay draws its
	// chrome through these instead of hand-rolling panels, so the mod reads
	// as one visual system instead of each screen inventing its own.

	/**
	 * A panel with its four corner pixels chamfered off, which is as close
	 * to "rounded" as a renderer with only axis-aligned fill() can get
	 * without a texture. At every GUI scale this reads as a soft corner
	 * rather than a jagged one, because there's nothing finer than a pixel
	 * to jag - the chamfer never has an "edge" of its own to look wrong.
	 */
	void roundedPanel(int x, int y, int w, int h, int background, int lightEdge, int darkEdge) {
		if (w < 2 || h < 2) {
			fill(x, y, x + w, y + h, background);
			return;
		}
		fill(x + 1, y, x + w - 1, y + h, background);
		fill(x, y + 1, x + w, y + h - 1, background);
		fill(x + 1, y, x + w - 1, y + 1, lightEdge);
		fill(x, y + 1, x + 1, y + h - 1, lightEdge);
		fill(x + 1, y + h - 1, x + w - 1, y + h, darkEdge);
		fill(x + w - 1, y + 1, x + w, y + h - 1, darkEdge);
	}

	/**
	 * A soft-edged drop shadow behind a panel: concentric one-pixel outlines
	 * stepping outward from the panel's bounds, fading in opacity as they
	 * go. Draw this before the panel it belongs to. {@code color} carries
	 * the shadow's peak alpha in its top byte; {@code spread} is how many
	 * pixels it reaches outward.
	 */
	void dropShadow(int x, int y, int w, int h, int color, int spread) {
		int baseAlpha = (color >>> 24) & 0xFF;
		int rgb = color & 0xFFFFFF;
		for (int i = 1; i <= spread; i++) {
			float falloff = 1.0F - (i - 1.0F) / spread;
			int alpha = Math.round(baseAlpha * falloff * falloff);
			if (alpha <= 0) continue;
			outline(x - i, y - i, w + i * 2, h + i * 2, (alpha << 24) | rgb);
		}
	}

	/**
	 * A glowing border: the same idea as {@link #dropShadow}, but growing
	 * inward from an edge that's meant to draw attention to itself - a
	 * focused overlay's frame, say - rather than to sit behind a panel.
	 */
	void glowEdge(int x, int y, int w, int h, int color, int layers) {
		int baseAlpha = (color >>> 24) & 0xFF;
		int rgb = color & 0xFFFFFF;
		for (int i = 0; i < layers; i++) {
			int alpha = Math.round(baseAlpha * (1.0F - (float) i / layers));
			if (alpha <= 0) continue;
			outline(x - i, y - i, w + i * 2, h + i * 2, (alpha << 24) | rgb);
		}
	}

	/** A thin horizontal rule, for separating sections of an overlay or panel. */
	void divider(int x, int y, int w, int color) {
		fill(x, y, x + w, y + 1, color);
	}

	/**
	 * Vertical scrollbar: a track the full height of the viewport, and a
	 * thumb sized to how much of the content that viewport actually shows.
	 * Draws nothing beyond the bare track when the content fits without
	 * scrolling, so a short list doesn't grow a decorative thumb that never
	 * moves.
	 */
	void scrollbar(int x, int y, int w, int h, int contentHeight, int viewHeight, int scrollOffset,
			int trackColor, int thumbColor) {
		fill(x, y, x + w, y + h, trackColor);
		if (contentHeight <= viewHeight) return;
		int thumbH = Math.max(w * 2, h * viewHeight / contentHeight);
		int maxOffset = contentHeight - viewHeight;
		int thumbY = y + Math.round((h - thumbH) * (Math.max(0, Math.min(scrollOffset, maxOffset)) / (float) maxOffset));
		fill(x, thumbY, x + w, thumbY + thumbH, thumbColor);
	}

	/**
	 * A real gear, not a glyph from the font - drawn as a filled disc, eight
	 * square teeth at the compass points, and a punched-out hub hole for
	 * depth. All three are row-span fills, so the silhouette is exact at
	 * any radius instead of a character that goes fuzzy or lopsided as the
	 * GUI scale changes underneath it.
	 */
	void gearIcon(int centerX, int centerY, int radius, int color, int holeColor) {
		filledCircle(centerX, centerY, radius, color);
		int toothSize = Math.max(2, radius / 2);
		double[] angles = {0, 45, 90, 135, 180, 225, 270, 315};
		for (double degrees : angles) {
			double rad = Math.toRadians(degrees);
			int tx = centerX + (int) Math.round(Math.cos(rad) * radius);
			int ty = centerY + (int) Math.round(Math.sin(rad) * radius);
			fill(tx - toothSize / 2, ty - toothSize / 2, tx - toothSize / 2 + toothSize, ty - toothSize / 2 + toothSize, color);
		}
		int hubRadius = Math.max(1, radius / 3);
		filledCircle(centerX, centerY, hubRadius, holeColor);
	}

	private void filledCircle(int centerX, int centerY, int radius, int color) {
		for (int dy = -radius; dy <= radius; dy++) {
			double dx = Math.sqrt(Math.max(0, (double) radius * radius - (double) dy * dy));
			int span = (int) Math.round(dx);
			if (span <= 0) continue;
			fill(centerX - span, centerY + dy, centerX + span, centerY + dy + 1, color);
		}
	}
}
