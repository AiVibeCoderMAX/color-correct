package com.colorcorrect.client.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import org.joml.Math;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A color balance wheel (like Lightroom's): hue = direction from the center,
 * saturation = distance. Drag to pick a color, right-click to reset to neutral.
 */
public class ColorWheelWidget extends ClickableWidget {
	private static final int GRID = 18;

	private final DoubleSupplier hueGetter;
	private final DoubleSupplier satGetter;
	private final DoubleConsumer hueSetter;
	private final DoubleConsumer satSetter;
	private final Runnable onChange;
	private boolean dragging;

	public ColorWheelWidget(int x, int y, int size,
	                        DoubleSupplier hueGetter, DoubleSupplier satGetter,
	                        DoubleConsumer hueSetter, DoubleConsumer satSetter,
	                        Runnable onChange) {
		super(x, y, size, size, Text.empty());
		this.hueGetter = hueGetter;
		this.satGetter = satGetter;
		this.hueSetter = hueSetter;
		this.satSetter = satSetter;
		this.onChange = onChange;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int cx = getX() + getWidth() / 2;
		int cy = getY() + getHeight() / 2;
		float r = getWidth() / 2f - 2f;

		float cell = (r * 2f) / GRID;
		for (int i = 0; i < GRID; i++) {
			for (int j = 0; j < GRID; j++) {
				float x = -r + (i + 0.5f) * cell;
				float y = -r + (j + 0.5f) * cell;
				float d = (float) Math.sqrt(x * x + y * y) / r;
				if (d > 1f) continue;
				float hue = ((float) Math.atan2(y, x) / (float) (2.0 * Math.PI) + 1f) % 1f;
				int px = cx + (int) (x - cell / 2f);
				int py = cy + (int) (y - cell / 2f);
				context.fill(px, py, px + (int) cell + 1, py + (int) cell + 1, hsvToArgb(hue, d, 1f, 255));
			}
		}

		// rim
		int rim = 0xFF6F6F6F;
		for (int i = 0; i < 48; i++) {
			double a = i / 48.0 * 2.0 * Math.PI;
			int px = cx + (int) (Math.cos(a) * r);
			int py = cy + (int) (Math.sin(a) * r);
			context.fill(px - 1, py - 1, px + 2, py + 2, rim);
		}

		// pin
		double hue = hueGetter.getAsDouble();
		double sat = satGetter.getAsDouble();
		int px = cx + (int) (Math.cos(hue * 2.0 * Math.PI) * sat * r);
		int py = cy + (int) (Math.sin(hue * 2.0 * Math.PI) * sat * r);
		context.fill(px - 4, py - 4, px + 5, py + 5, 0xFF141414);
		context.fill(px - 3, py - 3, px + 4, py + 4, 0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (!visible || !active) return false;
		if (click.x() >= getX() && click.x() <= getX() + getWidth()
				&& click.y() >= getY() && click.y() <= getY() + getHeight()) {
			if (click.button() == 1) {
				satSetter.accept(0.0);
				onChange.run();
				return true;
			}
			if (click.button() == 0) {
				dragging = true;
				setFocused(true);
				updateFromMouse(click.x(), click.y());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging && click.button() == 0) {
			updateFromMouse(click.x(), click.y());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = false;
		return false;
	}

	private void updateFromMouse(double mx, double my) {
		float r = getWidth() / 2f - 2f;
		float dx = (float) (mx - (getX() + getWidth() / 2f));
		float dy = (float) (my - (getY() + getHeight() / 2f));
		float d = (float) Math.sqrt(dx * dx + dy * dy) / r;
		if (d > 1f) d = 1f;
		double hue = (Math.atan2(dy, dx) / (2.0 * Math.PI) + 1.0) % 1.0;
		boolean changed = Math.abs(hueGetter.getAsDouble() - hue) > 0.001 || Math.abs(satGetter.getAsDouble() - d) > 0.001;
		hueSetter.accept(hue);
		satSetter.accept(d);
		if (changed) onChange.run();
	}

	@Override
	protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}

	/** HSV (hue in 0..1) to 0xAARRGGBB. */
	public static int hsvToArgb(float hue, float sat, float value, int alpha) {
		int i = (int) (hue * 6f);
		float f = hue * 6f - i;
		float p = value * (1f - sat);
		float q = value * (1f - sat * f);
		float t = value * (1f - sat * (1f - f));
		float r, g, b;
		switch (i % 6) {
			case 0 -> { r = value; g = t; b = p; }
			case 1 -> { r = q; g = value; b = p; }
			case 2 -> { r = p; g = value; b = t; }
			case 3 -> { r = p; g = q; b = value; }
			case 4 -> { r = t; g = p; b = value; }
			default -> { r = value; g = p; b = q; }
		}
		return (alpha << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
	}
}
