package com.coxcensor;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.JagexColors;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.TextComponent;

@Singleton
class CollectionLogPopupOverlay extends Overlay
{
	private static final int OPEN_MS = 350;
	private static final int HOLD_MS = 2800;
	private static final int CLOSE_MS = 350;
	private static final int SCREENSHOT_DELAY_MS = 250;

	private static final int MIN_INNER_WIDTH = 196;
	private static final int OUTER_PAD = 8;
	private static final int PANEL_GAP = 2;
	private static final int INNER_PAD_X = 14;
	private static final int TITLE_PAD_Y = 6;
	private static final int BODY_PAD_Y = 18;
	private static final int BODY_LINE_GAP = 12;
	private static final int TOP_OFFSET = 20;
	private static final int CORNER_SIZE = 8;
	private static final int WRAP_INSET = 2;

	private static final Palette CLASSIC = new Palette(
		new Color(81, 73, 55),
		new Color(62, 53, 41),
		new Color(24, 22, 18),
		new Color(45, 39, 24),
		new Color(117, 110, 92),
		new Color(45, 39, 24),
		new Color(168, 159, 143),
		new Color(70, 57, 39),
		Color.BLACK,
		JagexColors.DARK_ORANGE_INTERFACE_TEXT,
		Color.WHITE
	);
	private static final Palette DARK = new Palette(
		new Color(10, 10, 10),
		new Color(36, 36, 36),
		new Color(86, 86, 86),
		new Color(58, 58, 58),
		new Color(72, 72, 70),
		new Color(28, 28, 28),
		new Color(16, 16, 16),
		new Color(40, 40, 40),
		Color.BLACK,
		JagexColors.DARK_ORANGE_INTERFACE_TEXT,
		Color.WHITE
	);
	private static final String TITLE_TEXT = "Collection Log";
	private static final String NEW_ITEM_TEXT = "New item:";

	enum Phase
	{
		IDLE,
		OPENING,
		HOLDING,
		CLOSING
	}

	private final Client client;
	private final CoxCensorConfig config;
	private final Deque<PendingUnlock> queue = new ArrayDeque<>();

	private CoxCensorPlugin plugin;
	private PendingUnlock current;
	private Phase phase = Phase.IDLE;
	private long phaseStartedAt;
	private boolean screenshotRequested;

	@Inject
	CollectionLogPopupOverlay(Client client, CoxCensorConfig config)
	{
		this.client = client;
		this.config = config;
		setPosition(OverlayPosition.TOP_CENTER);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
		setMovable(false);
	}

	void setPlugin(CoxCensorPlugin plugin)
	{
		this.plugin = plugin;
	}

	void enqueue(Collection<PendingUnlock> unlocks)
	{
		queue.addAll(unlocks);
		if (phase == Phase.IDLE)
		{
			advance();
		}
	}

	void enqueue(PendingUnlock unlock)
	{
		queue.add(unlock);
		if (phase == Phase.IDLE)
		{
			advance();
		}
	}

	void reset()
	{
		queue.clear();
		current = null;
		phase = Phase.IDLE;
		screenshotRequested = false;
	}

	boolean isShowing()
	{
		return phase != Phase.IDLE;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (phase == Phase.IDLE || current == null)
		{
			return null;
		}

		long elapsed = System.currentTimeMillis() - phaseStartedAt;
		float alpha = 1f;

		switch (phase)
		{
			case OPENING:
				alpha = Math.min(1f, elapsed / (float) OPEN_MS);
				if (elapsed >= OPEN_MS)
				{
					enterPhase(Phase.HOLDING);
				}
				break;
			case HOLDING:
				if (!screenshotRequested && elapsed >= SCREENSHOT_DELAY_MS && plugin != null)
				{
					screenshotRequested = true;
					plugin.onPopupFullyOpen(current);
				}
				if (elapsed >= HOLD_MS)
				{
					enterPhase(Phase.CLOSING);
					elapsed = 0;
				}
				break;
			case CLOSING:
				alpha = Math.max(0f, 1f - (elapsed / (float) CLOSE_MS));
				if (elapsed >= CLOSE_MS)
				{
					advance();
					if (phase == Phase.IDLE)
					{
						return null;
					}
				}
				break;
			default:
				break;
		}

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_NORMALIZE);

		Font titleFont = FontManager.getRunescapeBoldFont();
		Font bodyFont = FontManager.getRunescapeSmallFont();
		FontMetrics titleMetrics = graphics.getFontMetrics(titleFont);
		FontMetrics bodyMetrics = graphics.getFontMetrics(bodyFont);

		String itemName = current.getItem();
		int innerWidth = Math.max(MIN_INNER_WIDTH,
			Math.max(titleMetrics.stringWidth(TITLE_TEXT),
				Math.max(bodyMetrics.stringWidth(NEW_ITEM_TEXT), bodyMetrics.stringWidth(itemName)))
				+ INNER_PAD_X * 2);
		int titleHeight = titleMetrics.getHeight() + TITLE_PAD_Y * 2;
		int bodyHeight = bodyMetrics.getHeight() * 2 + BODY_LINE_GAP + BODY_PAD_Y * 2;
		int boxWidth = innerWidth + OUTER_PAD * 2;
		int boxHeight = OUTER_PAD + titleHeight + PANEL_GAP + bodyHeight + OUTER_PAD;

		setPreferredLocation(new Point(
			Math.max(0, client.getViewportXOffset() + (client.getViewportWidth() - boxWidth) / 2),
			TOP_OFFSET));

		int slide = Math.round((1f - alpha) * -boxHeight);
		int innerX = OUTER_PAD;
		int titleY = slide + OUTER_PAD;
		int bodyY = titleY + titleHeight + PANEL_GAP;
		Palette palette = config.darkCollectionLogPopup() ? DARK : CLASSIC;

		graphics.setColor(withAlpha(palette.frame, alpha));
		graphics.fillRect(0, slide, boxWidth, boxHeight);

		int wrapX = innerX - WRAP_INSET;
		int wrapY = titleY - WRAP_INSET;
		int wrapWidth = innerWidth + WRAP_INSET * 2;
		int wrapHeight = titleHeight + PANEL_GAP + bodyHeight + WRAP_INSET * 2;
		drawWrapBorder(graphics, wrapX, wrapY, wrapWidth, wrapHeight, palette, alpha);

		drawInsetPanel(graphics, innerX, titleY, innerWidth, titleHeight, palette, alpha);
		drawInsetPanel(graphics, innerX, bodyY, innerWidth, bodyHeight, palette, alpha);
		drawCornerTriangles(graphics, 0, slide, boxWidth, boxHeight, palette, alpha);

		graphics.setColor(withAlpha(palette.outline, alpha));
		graphics.drawRect(0, slide, boxWidth - 1, boxHeight - 1);

		int centerX = boxWidth / 2;
		int titleBaseline = titleY + TITLE_PAD_Y + titleMetrics.getAscent();
		int newItemBaseline = bodyY + BODY_PAD_Y + bodyMetrics.getAscent();
		int itemBaseline = newItemBaseline + bodyMetrics.getDescent() + BODY_LINE_GAP + bodyMetrics.getAscent();

		drawCentered(graphics, TITLE_TEXT, withAlpha(palette.title, alpha), titleFont, titleMetrics, centerX, titleBaseline);
		drawCentered(graphics, NEW_ITEM_TEXT, withAlpha(palette.title, alpha), bodyFont, bodyMetrics, centerX, newItemBaseline);
		drawCentered(graphics, itemName, withAlpha(palette.body, alpha), bodyFont, bodyMetrics, centerX, itemBaseline);

		return new Dimension(boxWidth, boxHeight);
	}

	private static void drawInsetPanel(Graphics2D graphics, int x, int y, int width, int height, Palette palette, float alpha)
	{
		graphics.setColor(withAlpha(palette.panel, alpha));
		graphics.fillRect(x, y, width, height);

		graphics.setColor(withAlpha(palette.panelEdge, alpha));
		graphics.drawRect(x, y, width - 1, height - 1);
		graphics.setColor(withAlpha(palette.panelEdgeInner, alpha));
		graphics.drawRect(x + 1, y + 1, width - 3, height - 3);
	}

	private static void drawWrapBorder(Graphics2D graphics, int x, int y, int width, int height, Palette palette, float alpha)
	{
		graphics.setColor(withAlpha(palette.wrapEdge, alpha));
		graphics.drawRect(x, y, width - 1, height - 1);
		graphics.setColor(withAlpha(palette.wrapEdgeInner, alpha));
		graphics.drawRect(x + 1, y + 1, width - 3, height - 3);
	}

	private static void drawCornerTriangles(Graphics2D graphics, int x, int y, int width, int height, Palette palette, float alpha)
	{
		int right = x + width - 1;
		int bottom = y + height - 1;
		drawCorner(graphics, x, y, 1, 1, palette, alpha);
		drawCorner(graphics, right, y, -1, 1, palette, alpha);
		drawCorner(graphics, x, bottom, 1, -1, palette, alpha);
		drawCorner(graphics, right, bottom, -1, -1, palette, alpha);
	}

	private static void drawCorner(Graphics2D graphics, int cx, int cy, int dirX, int dirY, Palette palette, float alpha)
	{
		int ox = cx + dirX;
		int oy = cy + dirY;
		int[] xs = {ox, ox + CORNER_SIZE * dirX, ox};
		int[] ys = {oy, oy, oy + CORNER_SIZE * dirY};
		graphics.setColor(withAlpha(palette.corner, alpha));
		graphics.fillPolygon(xs, ys, 3);

		graphics.setColor(withAlpha(palette.cornerEdge, alpha));
		graphics.drawLine(ox + CORNER_SIZE * dirX, oy, ox, oy + CORNER_SIZE * dirY);
		graphics.drawLine(
			ox + (CORNER_SIZE - 1) * dirX, oy + dirY,
			ox + dirX, oy + (CORNER_SIZE - 1) * dirY);
	}

	private void advance()
	{
		current = queue.poll();
		screenshotRequested = false;
		if (current == null)
		{
			phase = Phase.IDLE;
			if (plugin != null)
			{
				plugin.onPopupQueueFinished();
			}
			return;
		}
		enterPhase(Phase.OPENING);
	}

	private void enterPhase(Phase next)
	{
		phase = next;
		phaseStartedAt = System.currentTimeMillis();
	}

	private static void drawCentered(Graphics2D graphics, String text, Color color, Font font,
		FontMetrics metrics, int centerX, int baselineY)
	{
		TextComponent textComponent = new TextComponent();
		textComponent.setText(text);
		textComponent.setColor(color);
		textComponent.setFont(font);
		textComponent.setPosition(centerX - metrics.stringWidth(text) / 2, baselineY);
		textComponent.render(graphics);
	}

	private static Color withAlpha(Color color, float alpha)
	{
		if (alpha >= 1f)
		{
			return color;
		}
		int a = Math.round(color.getAlpha() * alpha);
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, a)));
	}

	private static final class Palette
	{
		private final Color frame;
		private final Color panel;
		private final Color panelEdge;
		private final Color panelEdgeInner;
		private final Color wrapEdge;
		private final Color wrapEdgeInner;
		private final Color corner;
		private final Color cornerEdge;
		private final Color outline;
		private final Color title;
		private final Color body;

		private Palette(Color frame, Color panel, Color panelEdge, Color panelEdgeInner,
			Color wrapEdge, Color wrapEdgeInner, Color corner, Color cornerEdge,
			Color outline, Color title, Color body)
		{
			this.frame = frame;
			this.panel = panel;
			this.panelEdge = panelEdge;
			this.panelEdgeInner = panelEdgeInner;
			this.wrapEdge = wrapEdge;
			this.wrapEdgeInner = wrapEdgeInner;
			this.corner = corner;
			this.cornerEdge = cornerEdge;
			this.outline = outline;
			this.title = title;
			this.body = body;
		}
	}
}
