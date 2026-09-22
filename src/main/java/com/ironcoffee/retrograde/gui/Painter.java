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

	/**
	 * A whole region-atlas texture (see ChunkMapDataCache), scaled to
	 * whatever screen rectangle its chunks currently map to - one blit for
	 * however many chunks that region holds, rather than one per chunk.
	 * {@code textureSize} is the atlas's own pixel width/height (it's
	 * square), used for the UV extent on pre-26; 26.x addresses by fraction
	 * so the atlas size never enters into it.
	 */
	void regionTexture(ResourceLocation texture, int x, int y, int w, int h, int textureSize) {
		//? if <26 {
		graphics.blit(texture, x, y, w, h, 0.0F, 0.0F, textureSize, textureSize, textureSize, textureSize);
		//?} else {
		/*graphics.blit(texture, x, y, x + w, y + h, 0.0F, 1.0F, 0.0F, 1.0F);
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

	/** Teeth on {@link #gearIcon}. Six, not the usual eight: at the ~14px a
	 * toolbar button gives you, eight teeth land under 2px apart and blur into
	 * a scalloped ring. Six stay separate enough to read as teeth. */
	private static final int GEAR_TEETH = 6;
	/** Where the valley between two teeth sits, as a fraction of the radius. */
	private static final double GEAR_ROOT = 0.66;
	/** The hub bore, as a fraction of the radius. Generous on purpose: a
	 * pinhole hub disappears at toolbar size and leaves a cog-shaped blob,
	 * and the hole is most of what makes a gear read as a gear. */
	private static final double GEAR_HUB = 0.40;
	/** Half-width of a tooth at its root and at its tip, as a fraction of the
	 * angular pitch. Tip narrower than root, so teeth taper outward the way a
	 * real involute tooth does instead of reading as square pegs. */
	private static final double GEAR_TOOTH_ROOT = 0.30;
	private static final double GEAR_TOOTH_TIP = 0.18;
	/** Supersampling grid per pixel. 4x4 gives 17 coverage steps, which is
	 * more than enough to hide the stair-stepping at this size. */
	private static final int GEAR_SAMPLES = 4;

	/**
	 * A gear, rasterised in polar coordinates with supersampled coverage
	 * rather than assembled out of stamped shapes.
	 *
	 * The previous version - a disc with eight squares dropped on the
	 * circumference - was the obvious construction and it looks like a lump,
	 * because square teeth centred on a curve merge into the disc on the
	 * diagonals and stick out on the axes. So instead: for each pixel, sample
	 * a 4x4 grid, ask each sample whether it's inside the gear's polar
	 * boundary, and fill the pixel at an alpha proportional to how many
	 * samples said yes. The edge gets antialiased for free, the teeth stay
	 * the same shape all the way round, and the silhouette holds up at any
	 * radius because nothing here is a fixed-size stamp.
	 *
	 * {@code color} is the gear body; {@code holeColor} fills the hub bore.
	 * Both carry their own alpha, which coverage scales down at the edges.
	 */
	void gearIcon(int centerX, int centerY, int radius, int color, int holeColor) {
		if (radius <= 0) return;

		double rootRadius = radius * GEAR_ROOT;
		double hubRadius = radius * GEAR_HUB;
		double pitch = Math.PI * 2 / GEAR_TEETH;
		double toothRoot = pitch * GEAR_TOOTH_ROOT;
		double toothTip = pitch * GEAR_TOOTH_TIP;

		int bodyAlpha = (color >>> 24) & 0xFF;
		int bodyRgb = color & 0xFFFFFF;
		int holeAlpha = (holeColor >>> 24) & 0xFF;
		int holeRgb = holeColor & 0xFFFFFF;

		int total = GEAR_SAMPLES * GEAR_SAMPLES;
		double step = 1.0 / GEAR_SAMPLES;
		double first = step / 2.0;

		for (int py = centerY - radius; py < centerY + radius; py++) {
			// Horizontal runs of equal coverage are common - the flat top of
			// the gear, the inside of the hub - so accumulate them and emit
			// one fill() per run instead of one per pixel.
			int runStart = 0, runBody = -1, runHub = -1;
			for (int px = centerX - radius; px <= centerX + radius; px++) {
				int bodyHits = 0, hubHits = 0;
				if (px < centerX + radius) {
					for (int sy = 0; sy < GEAR_SAMPLES; sy++) {
						double dy = py + first + sy * step - centerY;
						for (int sx = 0; sx < GEAR_SAMPLES; sx++) {
							double dx = px + first + sx * step - centerX;
							double r = Math.sqrt(dx * dx + dy * dy);
							if (r > radius) continue;
							if (r > rootRadius) {
								// Out in the tooth band: inside only if this
								// sample falls within the tooth's angular
								// window, which narrows as it goes outward.
								double t = (r - rootRadius) / (radius - rootRadius);
								double half = toothRoot + (toothTip - toothRoot) * t;
								double angle = Math.atan2(dy, dx);
								double within = angle - Math.floor(angle / pitch) * pitch;
								if (Math.min(within, pitch - within) > half) continue;
							}
							bodyHits++;
							if (r <= hubRadius) hubHits++;
						}
					}
				}
				if (bodyHits != runBody || hubHits != runHub) {
					emitGearRun(runStart, px, py, runBody, runHub, total, bodyAlpha, bodyRgb, holeAlpha, holeRgb);
					runStart = px;
					runBody = bodyHits;
					runHub = hubHits;
				}
			}
			emitGearRun(runStart, centerX + radius, py, runBody, runHub, total, bodyAlpha, bodyRgb, holeAlpha, holeRgb);
		}
	}

	/** One horizontal run of {@link #gearIcon} pixels that share a coverage. */
	private void emitGearRun(int x1, int x2, int y, int bodyHits, int hubHits, int total,
			int bodyAlpha, int bodyRgb, int holeAlpha, int holeRgb) {
		if (x2 <= x1 || bodyHits <= 0) return;
		int alpha = bodyAlpha * bodyHits / total;
		if (alpha > 0) {
			fill(x1, y, x2, y + 1, (alpha << 24) | bodyRgb);
		}
		if (hubHits > 0) {
			// Drawn over the body rather than instead of it, so a partly
			// covered hub edge blends into the gear instead of into whatever
			// happens to be behind the button.
			int hub = holeAlpha * hubHits / total;
			if (hub > 0) {
				fill(x1, y, x2, y + 1, (hub << 24) | holeRgb);
			}
		}
	}
}
