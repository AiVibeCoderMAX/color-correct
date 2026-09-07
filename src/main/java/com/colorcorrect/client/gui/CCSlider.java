package com.colorcorrect.client.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/**
 * A compact slider strip. The label and value text are drawn by the screen;
 * the widget itself is only the draggable track.
 *
 * Left-drag to set, scroll wheel to fine-step, right-click to reset.
 */
public class CCSlider extends ClickableWidget {
	private final double min;
	private final double max;
	private final double step;
	private final DoubleSupplier getter;
	private final DoubleConsumer setter;
	private final double resetValue;
	private final Runnable onChange;
	private final String format;
	private boolean dragging;

	public CCSlider(int x, int y, int w, double min, double max, double step,
	                DoubleSupplier getter, DoubleConsumer setter,
	                double resetValue, String suffix, Runnable onChange) {
		super(x, y, w, 10, Text.empty());
		this.min = min;
		this.max = max;
		this.step = step;
		this.getter = getter;
		this.setter = setter;
		this.resetValue = resetValue;
		this.onChange = onChange;
		this.format = "%.1f" + suffix;
	}

	public String valueText() {
		return String.format(format, snap(getter.getAsDouble()));
	}

	private double snap(double v) {
		double s = Math.max(step, 1.0e-6);
		return Math.round((v - min) / s) * s + min;
	}

	private void setValue(double v) {
		double snapped = Math.max(min, Math.min(max, snap(v)));
		if (snapped != getter.getAsDouble()) {
			setter.accept(snapped);
			onChange.run();
		}
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int x0 = getX();
		int cy = getY() + getHeight() / 2;
		double t = (getter.getAsDouble() - min) / (max - min);
		t = Math.max(0.0, Math.min(1.0, t));
		int tx = x0 + (int) (getWidth() * t);

		context.fill(x0, cy - 1, x0 + getWidth(), cy + 2, 0xFF23262B);
		int fillColor = dragging ? 0xFF7CC0FF : (hovered ? 0xFF5FADF8 : 0xFF4796E8);
		context.fill(x0, cy - 1, tx, cy + 2, fillColor);
		// thumb
		context.fill(tx - 1, cy - 6, tx + 2, cy + 7, 0xFFEDEDED);
		if (isFocused()) {
			context.fill(tx - 3, cy - 8, tx + 4, cy + 9, 0x55FFFFFF);
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (!visible || !active) return false;
		if (inBounds(click)) {
			if (click.button() == 1) {
				setValue(resetValue);
				return true;
			}
			if (click.button() == 0) {
				dragging = true;
				setFocused(true);
				updateFromMouse(click.x());
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(Click click, double offsetX, double offsetY) {
		if (dragging && click.button() == 0) {
			updateFromMouse(click.x());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(Click click) {
		dragging = false;
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (visible && active && isMouseOver(mouseX, mouseY)) {
			setValue(getter.getAsDouble() + Math.signum(verticalAmount) * step);
			return true;
		}
		return false;
	}

	@Override
	protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}

	private boolean inBounds(Click click) {
		return click.x() >= getX() && click.x() <= getX() + getWidth()
				&& click.y() >= getY() - 4 && click.y() <= getY() + getHeight() + 4;
	}

	private void updateFromMouse(double mouseX) {
		double t = Math.max(0.0, Math.min(1.0, (mouseX - getX()) / getWidth()));
		setValue(min + t * (max - min));
	}
}
