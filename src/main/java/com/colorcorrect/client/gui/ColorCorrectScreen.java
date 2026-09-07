package com.colorcorrect.client.gui;

import com.colorcorrect.ColorCorrectClient;
import com.colorcorrect.client.ColorCorrectConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The grading panel. Left: categories (White Balance / Light / Color Wheels /
 * Creative / Effects / Presets). Right: the controls of the selected page.
 *
 * All controls write directly into {@link ColorCorrectConfig}, which marks the
 * pipeline dirty so the GPU pass is rebuilt with the new values — giving a
 * live preview while dragging.
 */
public class ColorCorrectScreen extends Screen {
	private static final String[] CATEGORIES = {"wb", "tone", "wheels", "creative", "effects", "presets"};

	private static final String[] LOOKS = {"None", "Warm Sunset", "Cool Arctic", "Vintage Film",
			"Noir", "Vivid", "Faded Dream", "Cyberpunk"};
	private static final String[] COLOR_SPACES = {"sRGB", "Rec. 709", "DCI-P3", "Adobe RGB"};

	private final Screen parent;
	private String category = "wb";
	private ButtonWidget activeButton;

	private final List<Row> rows = new ArrayList<>();
	private final List<WheelEntry> wheels = new ArrayList<>();

	public ColorCorrectScreen(Screen parent) {
		super(Text.translatable("colorcorrect.title"));
		this.parent = parent;
	}

	/* ================= layout ================= */

	@Override
	protected void init() {
		ColorCorrectConfig cfg = ColorCorrectClient.config;
		rows.clear();
		wheels.clear();
		int right = this.width - 8;

		addDrawableChild(ButtonWidget.builder(Text.translatable("colorcorrect.done"), b -> close())
				.dimensions(right - 60, 8, 60, 20).build());
		addDrawableChild(ButtonWidget.builder(Text.translatable("colorcorrect.resetAll"), b -> {
					cfg.resetAll();
				})
				.dimensions(right - 170, 8, 100, 20).build());
		activeButton = ButtonWidget.builder(activeLabel(), b -> {
					cfg.enabled = !cfg.enabled;
					cfg.markDirty();
				})
				.dimensions(right - 280, 8, 100, 20).build();
		addDrawableChild(activeButton);

		int y = 42;
		for (String cat : CATEGORIES) {
			String current = cat;
			addDrawableChild(ButtonWidget.builder(Text.translatable("colorcorrect.category." + cat), b -> {
				this.category = current;
				this.clearChildren();
				this.init();
			}).dimensions(8, y, 112, 20).build());
			y += 26;
		}

		buildPanel();
	}

	private void buildPanel() {
		switch (category) {
			case "wb" -> buildWhiteBalance();
			case "tone" -> buildTone();
			case "wheels" -> buildWheels();
			case "creative" -> buildCreative();
			case "effects" -> buildEffects();
			case "presets" -> buildPresets();
		}
	}

	/* ================= pages ================= */

	private void buildWhiteBalance() {
		ColorCorrectConfig c = ColorCorrectClient.config;
		slider("temperature", 64, -100, 100, 1,
				() -> c.temperature, v -> c.temperature = (float) v, 0, " K");
		slider("tint", 94, -100, 100, 1,
				() -> c.tint, v -> c.tint = (float) v, 0, "");
	}

	private void buildTone() {
		ColorCorrectConfig c = ColorCorrectClient.config;
		slider("exposure", 64, -4, 4, 0.05,
				() -> c.exposure, v -> c.exposure = (float) v, 0, " EV");
		slider("contrast", 94, -100, 100, 1,
				() -> c.contrast, v -> c.contrast = (float) v, 0, "");
		slider("highlights", 124, -100, 100, 1,
				() -> c.highlights, v -> c.highlights = (float) v, 0, "");
		slider("shadows", 154, -100, 100, 1,
				() -> c.shadows, v -> c.shadows = (float) v, 0, "");
		slider("whites", 184, -100, 100, 1,
				() -> c.whites, v -> c.whites = (float) v, 0, "");
		slider("blacks", 214, -100, 100, 1,
				() -> c.blacks, v -> c.blacks = (float) v, 0, "");
	}

	private void buildWheels() {
		ColorCorrectConfig c = ColorCorrectClient.config;
		wheel("shadow", 170, 92);
		wheel("mid", 296, 92);
		wheel("highlight", 422, 92);

		slider("splitShadowHue", 222, 0, 1, 0.01,
				() -> c.splitShadowHue, v -> c.splitShadowHue = (float) v, 0, "");
		slider("splitShadowSat", 252, 0, 1, 0.01,
				() -> c.splitShadowSat, v -> c.splitShadowSat = (float) v, 0, "");
		slider("splitHighlightHue", 282, 0, 1, 0.01,
				() -> c.splitHighlightHue, v -> c.splitHighlightHue = (float) v, 0, "");
		slider("splitHighlightSat", 312, 0, 1, 0.01,
				() -> c.splitHighlightSat, v -> c.splitHighlightSat = (float) v, 0, "");
	}

	private void buildCreative() {
		ColorCorrectConfig c = ColorCorrectClient.config;
		selector("look", 64, LOOKS, () -> c.look, v -> c.look = v);
		selector("colorSpace", 94, COLOR_SPACES, () -> c.colorSpace, v -> c.colorSpace = v);
		slider("intensity", 124, 0, 100, 1,
				() -> c.intensity, v -> c.intensity = (float) v, 100, "%");
		slider("fadedFilm", 154, 0, 100, 1,
				() -> c.fadedFilm, v -> c.fadedFilm = (float) v, 0, "%");
		slider("sharpen", 184, 0, 100, 1,
				() -> c.sharpen, v -> c.sharpen = (float) v, 0, "%");
		slider("vibrance", 214, 0, 100, 1,
				() -> c.vibrance, v -> c.vibrance = (float) v, 0, "%");
		slider("saturation", 244, 0, 200, 1,
				() -> c.saturation, v -> c.saturation = (float) v, 100, "%");
	}

	private void buildEffects() {
		ColorCorrectConfig c = ColorCorrectClient.config;
		slider("clarity", 64, 0, 100, 1,
				() -> c.clarity, v -> c.clarity = (float) v, 0, "%");
		slider("grain", 94, 0, 100, 1,
				() -> c.grain, v -> c.grain = (float) v, 0, "%");
		slider("vignette", 124, 0, 100, 1,
				() -> c.vignette, v -> c.vignette = (float) v, 0, "%");
		slider("aberration", 154, 0, 100, 1,
				() -> c.aberration, v -> c.aberration = (float) v, 0, "%");
	}

	private void buildPresets() {
		int x = 140, y = 70, w = 130, h = 20;
		int i = 0;
		for (String name : ColorCorrectConfig.PRESETS.keySet()) {
			String preset = name;
			addDrawableChild(ButtonWidget.builder(Text.literal(preset), b -> {
				ColorCorrectClient.config.applyPreset(preset);
			}).dimensions(x + (i % 2) * (w + 10), y + (i / 2) * (h + 6), w, h).build());
			i++;
		}
	}

	/* ================= row helpers ================= */

	private void slider(String key, int y, double min, double max, double step,
	                    java.util.function.DoubleSupplier getter,
	                    java.util.function.DoubleConsumer setter,
	                    double reset, String suffix) {
		CCSlider s = new CCSlider(292, y, 150, min, max, step, getter, setter, reset, suffix, this::onChanged);
		rows.add(new Row(key, y, s, null));
		addDrawableChild(s);
	}

	private void selector(String key, int y, String[] options,
	                      java.util.function.IntSupplier getter,
	                      java.util.function.IntConsumer setter) {
		CCSelector s = new CCSelector(292, y, 150, textRenderer, options, getter, setter, this::onChanged);
		rows.add(new Row(key, y, null, s));
		addDrawableChild(s);
	}

	private void wheel(String which, int x, int y) {
		ColorCorrectConfig c = ColorCorrectClient.config;
		ColorWheelWidget w;
		switch (which) {
			case "shadow" -> w = new ColorWheelWidget(x, y, 96,
					() -> c.wheelShadowHue, () -> c.wheelShadowSat,
					v -> c.wheelShadowHue = (float) v, v -> c.wheelShadowSat = (float) v, this::onChanged);
			case "highlight" -> w = new ColorWheelWidget(x, y, 96,
					() -> c.wheelHighlightHue, () -> c.wheelHighlightSat,
					v -> c.wheelHighlightHue = (float) v, v -> c.wheelHighlightSat = (float) v, this::onChanged);
			default -> w = new ColorWheelWidget(x, y, 96,
					() -> c.wheelMidHue, () -> c.wheelMidSat,
					v -> c.wheelMidHue = (float) v, v -> c.wheelMidSat = (float) v, this::onChanged);
		}
		wheels.add(new WheelEntry(which, x, y - 14));
		addDrawableChild(w);
	}

	private record Row(String key, int y, CCSlider slider, CCSelector selector) {
	}

	private record WheelEntry(String which, int x, int capY) {
	}

	/* ================= behaviour ================= */

	private void onChanged() {
		ColorCorrectClient.config.markDirty();
	}

	private Text activeLabel() {
		return Text.literal(ColorCorrectClient.config.enabled ? "Active: ON" : "Active: OFF");
	}

	@Override
	public void tick() {
		if (activeButton != null) {
			activeButton.setMessage(activeLabel());
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float delta) {
		// NOTE: we deliberately do NOT call renderBackground() here. In 1.21.9+
		// the menu-background blur pass may only run once per frame, and the GUI
		// has already blurred by the time a screen's render() runs — asking for
		// it again throws "Can only blur once per frame". We draw our own opaque
		// backdrop instead.
		context.fill(0, 0, this.width, this.height, 0xE60D0F14);

		context.drawTextWithShadow(textRenderer, this.title, 10, 14, 0xFFFFFFFF);

		// panel background
		context.fill(132, 40, this.width - 6, this.height - 26, 0x77101216);
		context.fill(132, 40, this.width - 6, 43, 0xAA161C24);

		// page caption
		context.drawTextWithShadow(textRenderer,
				Text.translatable("colorcorrect.category." + category), 142, 50, 0xFFFFD966);

		// wheel captions
		for (WheelEntry we : wheels) {
			Text cap = Text.translatable("colorcorrect.wheel." + we.which);
			context.drawTextWithShadow(textRenderer, cap,
					we.x + 48 - textRenderer.getWidth(cap) / 2, we.capY, 0xFFDADADA);
		}

		// rows: label + value
		for (Row row : rows) {
			Text label = Text.translatable("colorcorrect.setting." + row.key);
			context.drawTextWithShadow(textRenderer, label, 272 - textRenderer.getWidth(label), row.y, 0xFFC8C8C8);
			if (row.slider != null) {
				String v = row.slider.valueText();
				context.drawTextWithShadow(textRenderer, v, 292 + 150 + 12, row.y, 0xFF8FD0FF);
			}
		}

		context.drawTextWithShadow(textRenderer,
				Text.translatable("colorcorrect.hint.keys"), 10, this.height - 16, 0xFF909090);

		super.render(context, mouseX, mouseY, delta);
	}

	@Override
	public void close() {
		ColorCorrectClient.config.save();
		this.client.setScreen(parent);
	}
}
