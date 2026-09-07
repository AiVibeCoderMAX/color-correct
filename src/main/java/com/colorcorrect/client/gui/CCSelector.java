package com.colorcorrect.client.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Left/right cycling selector ("◀ None ▶") for looks, color spaces, etc. */
public class CCSelector extends ClickableWidget {
	private final TextRenderer textRenderer;
	private final String[] options;
	private final IntSupplier getter;
	private final IntConsumer setter;
	private final Runnable onChange;

	public CCSelector(int x, int y, int w, TextRenderer textRenderer, String[] options,
	                  IntSupplier getter, IntConsumer setter, Runnable onChange) {
		super(x, y, w, 16, Text.empty());
		this.textRenderer = textRenderer;
		this.options = options;
		this.getter = getter;
		this.setter = setter;
		this.onChange = onChange;
	}

	@Override
	protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
		int cy = getY() + getHeight() / 2;
		int idx = Math.max(0, Math.min(options.length - 1, getter.getAsInt()));
		context.drawTextWithShadow(textRenderer, "<", getX(), cy - 4, 0xFFB0B0B0);
		context.drawTextWithShadow(textRenderer, ">", getX() + getWidth() - 6, cy - 4, 0xFFB0B0B0);
		String label = options[idx];
		int w = textRenderer.getWidth(label);
		context.drawTextWithShadow(textRenderer, label,
				getX() + getWidth() / 2 - w / 2, cy - 4, hovered ? 0xFFFFFFFF : 0xFFDADADA);
	}

	private void cycle(int dir) {
		int n = options.length;
		int next = Math.floorMod(getter.getAsInt() + dir, n);
		if (next != getter.getAsInt()) {
			setter.accept(next);
			onChange.run();
		}
	}

	@Override
	public boolean mouseClicked(Click click, boolean doubled) {
		if (!visible || !active) return false;
		if (click.x() >= getX() && click.x() <= getX() + getWidth()
				&& click.y() >= getY() && click.y() <= getY() + getHeight() && click.button() == 0) {
			cycle(click.x() < getX() + getWidth() / 2 ? -1 : 1);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (visible && active && isMouseOver(mouseX, mouseY)) {
			cycle((int) Math.signum(verticalAmount));
			return true;
		}
		return false;
	}

	@Override
	protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
		this.appendDefaultNarrations(builder);
	}
}
