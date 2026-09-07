package com.colorcorrect.client;

import com.colorcorrect.ColorCorrectClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.PostEffectPipeline;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.util.memory.ObjectPool;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Owns the {@link PostEffectProcessor} that applies the color grade.
 *
 * The processor is (re)built from vanilla's public post-effect API whenever the
 * config changes: the graded frame is written to an internal "swap" target and
 * blitted back onto "minecraft:main" (the screen framebuffer), which is exactly
 * how the vanilla blur/invert chains work. Uniform values are baked in by the
 * pipeline, so a rebuild is triggered by any config change — the GPU program
 * itself stays cached, so this is cheap.
 */
public final class ColorCorrectRenderer {
	public static final Logger LOGGER = LoggerFactory.getLogger("ColorCorrect");
	public static final ColorCorrectRenderer INSTANCE = new ColorCorrectRenderer();

	private static final Identifier PIPELINE_ID = Identifier.of("colorcorrect", "main");
	private static final Identifier VERTEX_SHADER = Identifier.of("colorcorrect", "post/screenquad");
	private static final Identifier GRADE_SHADER = Identifier.of("colorcorrect", "post/color_grade");
	private static final Identifier BLIT_SHADER = Identifier.of("colorcorrect", "post/blit");
	private static final Identifier SWAP = Identifier.ofVanilla("swap");

	private PostEffectProcessor processor;
	private ProjectionMatrix2 projection;
	private final ObjectPool allocator = new ObjectPool(3);
	private int lastWidth = -1;
	private int lastHeight = -1;
	private int grainCounter;
	private boolean failed;

	private ColorCorrectRenderer() {
	}

	/** Called every frame from the HUD hook, right after world + vanilla post effects. */
	public void render() {
		MinecraftClient client = MinecraftClient.getInstance();
		ColorCorrectConfig cfg = ColorCorrectClient.config;
		if (cfg == null || client.world == null || !cfg.enabled || cfg.compareHeld) {
			return;
		}

		// Mirror the lifecycle GameRenderer uses for its own pool.
		int w = client.getWindow().getFramebufferWidth();
		int h = client.getWindow().getFramebufferHeight();
		if (w != lastWidth || h != lastHeight) {
			allocator.clear();
			lastWidth = w;
			lastHeight = h;
		}

		// Rebuild when a setting changed; grain also needs periodic time updates.
		boolean animateGrain = cfg.grain > 0.001f && (++grainCounter % 6 == 0);
		if (cfg.consumeDirty() || animateGrain) {
			rebuild(client);
		}
		if (processor != null) {
			try {
				processor.render(client.getFramebuffer(), allocator);
			} catch (Throwable t) {
				if (!failed) {
					LOGGER.error("Color Correct failed to render the grade pass", t);
					failed = true;
				}
			}
		}
		allocator.decrementLifespan();
	}

	private void rebuild(MinecraftClient client) {
		try {
			if (projection == null) {
				projection = new ProjectionMatrix2("colorcorrect", 0.1f, 1000.0f, false);
			}
			ColorCorrectConfig cfg = ColorCorrectClient.config;
			float time = System.nanoTime() * 1.0e-9f;

			PostEffectPipeline pipeline = new PostEffectPipeline(
					Map.of(SWAP, new PostEffectPipeline.Targets(Optional.empty(), Optional.empty(), false, 0)),
					List.of(
							new PostEffectPipeline.Pass(
									VERTEX_SHADER, GRADE_SHADER,
									List.of(new PostEffectPipeline.TargetSampler("In", PostEffectProcessor.MAIN, false, true)),
									SWAP,
									Map.of("ColorCorrectConfig", cfg.uniformValues(time))),
							new PostEffectPipeline.Pass(
									VERTEX_SHADER, BLIT_SHADER,
									List.of(new PostEffectPipeline.TargetSampler("In", SWAP, false, true)),
									PostEffectProcessor.MAIN,
									Map.of())
					));

			PostEffectProcessor next = PostEffectProcessor.parseEffect(
					pipeline, client.getTextureManager(),
					Set.of(PostEffectProcessor.MAIN), PIPELINE_ID, projection);

			if (processor != null) {
				processor.close();
			}
			processor = next;
			failed = false;
		} catch (ShaderLoader.LoadException | RuntimeException e) {
			LOGGER.error("Color Correct failed to build its post pipeline", e);
			if (processor != null) {
				processor.close();
				processor = null;
			}
		}
	}
}
