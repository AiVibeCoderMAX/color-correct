package com.colorcorrect.compat;

import com.colorcorrect.client.gui.ColorCorrectScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Optional Mod Menu integration (only loaded when Mod Menu is present). */
public class ColorCorrectModMenu implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ColorCorrectScreen::new;
	}
}
