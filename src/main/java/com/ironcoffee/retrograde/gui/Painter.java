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
}
