package org.mastodon.tomancak.dialogs;

import graphics.scenery.Node;
import org.scijava.command.Command;
import org.scijava.command.InteractiveCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

@Plugin(type = Command.class, name = "Spots Display Parameters Dialog")
public class SpotsDisplayParamsDialog extends InteractiveCommand
{
	public static class ParamsWrapper {
		public float spotSize = 1.0f;
		public float spotAlpha = 1.0f;
	};

	/** the object with parameters shared between this dialog and its caller */
	@Parameter
	private ParamsWrapper params;

	@Parameter
	private Node spotsGatheringNode;

	@Parameter(label = "Spots size", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "10.0", stepSize = "0.1", callback = "adjustSpotSize")
	private float spotSize = 1.0f;

	@Parameter(label = "Spots alpha", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "1.0", stepSize = "0.1", callback = "adjustSpotAlpha")
	private float spotAlpha = 1.0f;

	private
	void adjustSpotSize()
	{
		//tell back to our caller about the new value of this attribute
		params.spotSize = spotSize;

		for (Node s : spotsGatheringNode.getChildren())
			s.getScale().set(spotSize,spotSize,spotSize);
		spotsGatheringNode.updateWorld(true,true);
	}

	private
	void adjustSpotAlpha()
	{
		params.spotAlpha = spotAlpha;

		for (Node s : spotsGatheringNode.getChildren())
			s.getMaterial().getBlending().setOpacity(spotAlpha);
		spotsGatheringNode.updateWorld(true,true);
	}
}