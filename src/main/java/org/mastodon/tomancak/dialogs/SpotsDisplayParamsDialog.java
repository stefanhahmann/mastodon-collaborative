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
		public float sphereSize = 1.0f;
		public float sphereAlpha = 1.0f;
	};

	/** the object with parameters shared between this dialog and its caller */
	@Parameter
	private ParamsWrapper params;

	@Parameter
	private Node spheresGatheringNode;

	@Parameter(label = "Spheres size", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "10.0", stepSize = "0.1", callback = "adjustSphereSize")
	private float sphereSize = 1.0f;

	@Parameter(label = "Spheres alpha", style = NumberWidget.SLIDER_STYLE,
	           min = "0.1", max = "1.0", stepSize = "0.1", callback = "adjustSphereAlpha")
	private float sphereAlpha = 1.0f;

	private
	void adjustSphereSize()
	{
		//tell back to our caller about the new value of this attribute
		params.sphereSize = sphereSize;

		for (Node s : spheresGatheringNode.getChildren())
			s.getScale().set(sphereSize,sphereSize,sphereSize);
		spheresGatheringNode.updateWorld(true,true);
	}

	private
	void adjustSphereAlpha()
	{
		params.sphereAlpha = sphereAlpha;

		for (Node s : spheresGatheringNode.getChildren())
			s.getMaterial().getBlending().setOpacity(sphereAlpha);
		spheresGatheringNode.updateWorld(true,true);
	}
}
