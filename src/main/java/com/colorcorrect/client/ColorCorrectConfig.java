package com.colorcorrect.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gl.UniformValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * All color grading settings.
 *
 * The settings are written into a single std140 uniform block ("ColorCorrectConfig");
 * {@link #uniformValues} must stay in the exact same order as the GLSL block in
 * color_grade.fsh.
 */
public final class ColorCorrectConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("colorcorrect.json");

	/* ================= settings (persisted) ================= */

	public boolean enabled = true;

	/* White Balance */
	public float temperature = 0f;   // -100..100
	public float tint = 0f;          // -100..100

	/* Light */
	public float exposure = 0f;      // EV, -4..4
	public float contrast = 0f;      // -100..100
	public float highlights = 0f;    // -100..100
	public float shadows = 0f;       // -100..100
	public float whites = 0f;        // -100..100
	public float blacks = 0f;        // -100..100

	/* Creative */
	public int look = 0;             // 0 none .. 7
	public int colorSpace = 0;       // 0 sRGB, 1 Rec.709, 2 DCI-P3, 3 Adobe RGB
	public float intensity = 100f;   // 0..100
	public float fadedFilm = 0f;     // 0..100
	public float sharpen = 0f;       // 0..100
	public float vibrance = 0f;      // 0..100
	public float saturation = 100f;  // 0..200 (100 = neutral)

	/* Color wheels (hue 0..1, saturation 0..1) */
	public float wheelShadowHue = 0f;
	public float wheelShadowSat = 0f;
	public float wheelMidHue = 0f;
	public float wheelMidSat = 0f;
	public float wheelHighlightHue = 0f;
	public float wheelHighlightSat = 0f;

	/* Split toning */
	public float splitShadowHue = 0f;
	public float splitShadowSat = 0f;
	public float splitHighlightHue = 0f;
	public float splitHighlightSat = 0f;

	/* Lens & film effects */
	public float clarity = 0f;       // 0..100
	public float grain = 0f;         // 0..100
	public float vignette = 0f;      // 0..100
	public float aberration = 0f;    // 0..100

	/* ================= transient ================= */
	public boolean compareHeld = false; // hold-to-compare (set by client tick)
	private boolean dirty;
	private long lastSave;

	/* ================= presets ================= */

	public static final Map<String, Consumer<ColorCorrectConfig>> PRESETS = new LinkedHashMap<>();

	static {
		PRESETS.put("Neutral", c -> reset(c));
		PRESETS.put("Warm", c -> {
			reset(c);
			c.temperature = 32; c.tint = 6; c.saturation = 112; c.vibrance = 22; c.contrast = 6;
		});
		PRESETS.put("Cool", c -> {
			reset(c);
			c.temperature = -30; c.tint = -4; c.saturation = 106; c.contrast = 8; c.wheelMidHue = 0.58f; c.wheelMidSat = 0.06f;
		});
		PRESETS.put("Vintage Film", c -> {
			reset(c);
			c.fadedFilm = 45; c.saturation = 82; c.contrast = -8; c.grain = 30; c.vignette = 25;
			c.splitShadowHue = 0.08f; c.splitShadowSat = 0.25f; c.splitHighlightHue = 0.12f; c.splitHighlightSat = 0.15f;
		});
		PRESETS.put("Noir", c -> {
			reset(c);
			c.saturation = 12; c.contrast = 32; c.vignette = 60; c.grain = 55; c.sharpen = 25;
		});
		PRESETS.put("Vivid", c -> {
			reset(c);
			c.saturation = 138; c.vibrance = 55; c.contrast = 14; c.clarity = 22;
		});
		PRESETS.put("Cyberpunk", c -> {
			reset(c);
			c.saturation = 122; c.contrast = 20; c.aberration = 35; c.vignette = 45;
			c.splitShadowHue = 0.48f; c.splitShadowSat = 0.5f;
			c.splitHighlightHue = 0.92f; c.splitHighlightSat = 0.5f;
		});
		PRESETS.put("Cinematic", c -> {
			reset(c);
			c.fadedFilm = 20; c.contrast = 12; c.saturation = 92; c.vibrance = 15; c.vignette = 35;
			c.splitHighlightHue = 0.07f; c.splitHighlightSat = 0.2f;
		});
	}

	private static void reset(ColorCorrectConfig c) {
		ColorCorrectConfig d = new ColorCorrectConfig();
		c.enabled = d.enabled; c.temperature = 0; c.tint = 0;
		c.exposure = 0; c.contrast = 0; c.highlights = 0; c.shadows = 0; c.whites = 0; c.blacks = 0;
		c.look = 0; c.colorSpace = 0; c.intensity = 100; c.fadedFilm = 0; c.sharpen = 0; c.vibrance = 0; c.saturation = 100;
		c.wheelShadowHue = c.wheelShadowSat = 0;
		c.wheelMidHue = c.wheelMidSat = 0;
		c.wheelHighlightHue = c.wheelHighlightSat = 0;
		c.splitShadowHue = c.splitShadowSat = 0;
		c.splitHighlightHue = c.splitHighlightSat = 0;
		c.clarity = 0; c.grain = 0; c.vignette = 0; c.aberration = 0;
	}

	public void resetAll() {
		reset(this);
		markDirty();
	}

	public void applyPreset(String name) {
		Consumer<ColorCorrectConfig> preset = PRESETS.get(name);
		if (preset != null) {
			preset.accept(this);
			markDirty();
		}
	}

	/* ================= uniforms ================= */

	/**
	 * Builds the uniform values for the ColorCorrectConfig std140 block.
	 * Order MUST match color_grade.fsh.
	 */
	public List<UniformValue> uniformValues(float time) {
		float[] a = toArray(time);
		List<UniformValue> list = new ArrayList<>(a.length);
		for (float f : a) {
			list.add(new UniformValue.FloatValue(f));
		}
		return list;
	}

	public float[] toArray(float time) {
		return new float[] {
				intensity,             // 0
				temperature,           // 1
				tint,                  // 2
				exposure,              // 3
				contrast,              // 4
				highlights,            // 5
				shadows,               // 6
				whites,                // 7
				blacks,                // 8
				saturation,            // 9
				vibrance,              // 10
				fadedFilm,             // 11
				colorSpace,            // 12
				look,                  // 13
				splitShadowHue,        // 14
				splitShadowSat,        // 15
				splitHighlightHue,     // 16
				splitHighlightSat,     // 17
				sharpen,               // 18
				clarity,               // 19
				grain,                 // 20
				vignette,              // 21
				aberration,            // 22
				time,                  // 23
				wheelShadowHue,        // 24
				wheelShadowSat,        // 25
				wheelMidHue,           // 26
				wheelMidSat,           // 27
				wheelHighlightHue,     // 28
				wheelHighlightSat,     // 29
		};
	}

	/* ================= IO ================= */

	public static ColorCorrectConfig load() {
		ColorCorrectConfig cfg = new ColorCorrectConfig();
		if (!Files.exists(FILE)) {
			cfg.markDirty();
			return cfg;
		}
		try {
			JsonObject o = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), JsonObject.class);
			if (o != null) {
				cfg.enabled = getBool(o, "enabled", true);
				cfg.temperature = getFloat(o, "temperature", 0);
				cfg.tint = getFloat(o, "tint", 0);
				cfg.exposure = getFloat(o, "exposure", 0);
				cfg.contrast = getFloat(o, "contrast", 0);
				cfg.highlights = getFloat(o, "highlights", 0);
				cfg.shadows = getFloat(o, "shadows", 0);
				cfg.whites = getFloat(o, "whites", 0);
				cfg.blacks = getFloat(o, "blacks", 0);
				cfg.look = getInt(o, "look", 0);
				cfg.colorSpace = getInt(o, "color_space", 0);
				cfg.intensity = getFloat(o, "intensity", 100);
				cfg.fadedFilm = getFloat(o, "faded_film", 0);
				cfg.sharpen = getFloat(o, "sharpen", 0);
				cfg.vibrance = getFloat(o, "vibrance", 0);
				cfg.saturation = getFloat(o, "saturation", 100);
				cfg.wheelShadowHue = getFloat(o, "wheel_shadow_hue", 0);
				cfg.wheelShadowSat = getFloat(o, "wheel_shadow_sat", 0);
				cfg.wheelMidHue = getFloat(o, "wheel_mid_hue", 0);
				cfg.wheelMidSat = getFloat(o, "wheel_mid_sat", 0);
				cfg.wheelHighlightHue = getFloat(o, "wheel_highlight_hue", 0);
				cfg.wheelHighlightSat = getFloat(o, "wheel_highlight_sat", 0);
				cfg.splitShadowHue = getFloat(o, "split_shadow_hue", 0);
				cfg.splitShadowSat = getFloat(o, "split_shadow_sat", 0);
				cfg.splitHighlightHue = getFloat(o, "split_highlight_hue", 0);
				cfg.splitHighlightSat = getFloat(o, "split_highlight_sat", 0);
				cfg.clarity = getFloat(o, "clarity", 0);
				cfg.grain = getFloat(o, "grain", 0);
				cfg.vignette = getFloat(o, "vignette", 0);
				cfg.aberration = getFloat(o, "aberration", 0);
			}
		} catch (Exception e) {
			ColorCorrectRenderer.LOGGER.error("Failed to read colorcorrect.json, using defaults", e);
		}
		return cfg;
	}

	public void save() {
		try {
			Files.createDirectories(FILE.getParent());
			JsonObject o = new JsonObject();
			o.addProperty("enabled", enabled);
			o.addProperty("temperature", temperature);
			o.addProperty("tint", tint);
			o.addProperty("exposure", exposure);
			o.addProperty("contrast", contrast);
			o.addProperty("highlights", highlights);
			o.addProperty("shadows", shadows);
			o.addProperty("whites", whites);
			o.addProperty("blacks", blacks);
			o.addProperty("look", look);
			o.addProperty("color_space", colorSpace);
			o.addProperty("intensity", intensity);
			o.addProperty("faded_film", fadedFilm);
			o.addProperty("sharpen", sharpen);
			o.addProperty("vibrance", vibrance);
			o.addProperty("saturation", saturation);
			o.addProperty("wheel_shadow_hue", wheelShadowHue);
			o.addProperty("wheel_shadow_sat", wheelShadowSat);
			o.addProperty("wheel_mid_hue", wheelMidHue);
			o.addProperty("wheel_mid_sat", wheelMidSat);
			o.addProperty("wheel_highlight_hue", wheelHighlightHue);
			o.addProperty("wheel_highlight_sat", wheelHighlightSat);
			o.addProperty("split_shadow_hue", splitShadowHue);
			o.addProperty("split_shadow_sat", splitShadowSat);
			o.addProperty("split_highlight_hue", splitHighlightHue);
			o.addProperty("split_highlight_sat", splitHighlightSat);
			o.addProperty("clarity", clarity);
			o.addProperty("grain", grain);
			o.addProperty("vignette", vignette);
			o.addProperty("aberration", aberration);
			Files.writeString(FILE, GSON.toJson(o), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ColorCorrectRenderer.LOGGER.error("Failed to save colorcorrect.json", e);
		}
	}

	private static boolean getBool(JsonObject o, String key, boolean def) {
		return o.has(key) ? o.get(key).getAsBoolean() : def;
	}

	private static float getFloat(JsonObject o, String key, float def) {
		return o.has(key) ? o.get(key).getAsFloat() : def;
	}

	private static int getInt(JsonObject o, String key, int def) {
		return o.has(key) ? o.get(key).getAsInt() : def;
	}

	/* ================= dirty tracking ================= */

	public void markDirty() {
		this.dirty = true;
	}

	public boolean consumeDirty() {
		boolean d = this.dirty;
		this.dirty = false;
		return d;
	}

	/** Returns true (once) when the config should be written to disk. */
	public boolean pollSave() {
		if (!dirty) return false;
		long now = System.currentTimeMillis();
		if (now - lastSave > 1200L) {
			lastSave = now;
			dirty = false;
			return true;
		}
		return false;
	}
}
