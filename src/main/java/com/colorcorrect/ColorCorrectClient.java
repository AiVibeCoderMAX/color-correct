package com.colorcorrect;

import com.colorcorrect.client.ColorCorrectConfig;
import com.colorcorrect.client.ColorCorrectRenderer;
import com.colorcorrect.client.gui.ColorCorrectScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class ColorCorrectClient implements ClientModInitializer {
	public static ColorCorrectConfig config;

	public static KeyBinding keyOpen;
	public static KeyBinding keyToggle;
	public static KeyBinding keyCompare;

	private static final KeyBinding.Category CATEGORY =
			KeyBinding.Category.create(Identifier.of("colorcorrect", "main"));

	@Override
	public void onInitializeClient() {
		config = ColorCorrectConfig.load();

		keyOpen = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.colorcorrect.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY));
		keyToggle = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.colorcorrect.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F6, CATEGORY));
		keyCompare = KeyBindingHelper.registerKeyBinding(
				new KeyBinding("key.colorcorrect.compare", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, CATEGORY));

		// Run the grade pass right after the world (and any vanilla effects) are
		// rendered, but before any HUD elements are drawn.
		HudElementRegistry.addFirst(Identifier.of("colorcorrect", "grade"), (context, tickCounter) ->
				ColorCorrectRenderer.INSTANCE.render());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (keyToggle.wasPressed()) {
				config.enabled = !config.enabled;
				config.markDirty();
			}
			while (keyOpen.wasPressed()) {
				client.setScreen(new ColorCorrectScreen(client.currentScreen));
			}
			config.compareHeld = keyCompare.isPressed();
			if (config.pollSave()) {
				config.save();
			}
		});

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			config.compareHeld = false;
			config.save();
		});
	}
}
