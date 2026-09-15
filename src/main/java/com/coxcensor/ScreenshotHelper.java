package com.coxcensor;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.ImageCapture;

@Singleton
class ScreenshotHelper
{
	private static final String SCREENSHOT_GROUP = "screenshot";
	private static final String COLLECTION_LOG_DIR = "Collection Log";

	private final ImageCapture imageCapture;
	private final ConfigManager configManager;
	private final CoxCensorConfig config;

	@Inject
	ScreenshotHelper(ImageCapture imageCapture, ConfigManager configManager, CoxCensorConfig config)
	{
		this.imageCapture = imageCapture;
		this.configManager = configManager;
		this.config = config;
	}

	void captureCollectionLog(String itemName)
	{
		if (!config.takeCollectionLogScreenshot())
		{
			return;
		}

		String fileName = "Collection log (" + itemName + ")";
		boolean includeFrame = screenshotBool("includeFrame", true);
		boolean notify = screenshotBool("notifyWhenTaken", true);
		boolean copyToClipboard = screenshotBool("copyToClipboard", false);
		imageCapture.takeScreenshot(COLLECTION_LOG_DIR, fileName, includeFrame, notify, copyToClipboard);
	}

	private boolean screenshotBool(String key, boolean defaultValue)
	{
		Boolean value = configManager.getConfiguration(SCREENSHOT_GROUP, key, Boolean.class);
		return value != null ? value : defaultValue;
	}
}
