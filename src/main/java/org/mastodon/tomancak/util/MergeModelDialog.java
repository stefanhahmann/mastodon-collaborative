package org.mastodon.tomancak.util;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.Parameter;
import org.scijava.command.Command;

@Plugin( type = Command.class, name = "Adjust merging params:" )
public class MergeModelDialog implements Command
{
	@Parameter(label = "Absolute distance cutoff:", min = "0")
	public double distCutoff = 1000;

	@Parameter(label = "Mahalanobis distance cutoff:", min = "0")
	public double mahalanobisDistCutoff = 1;

	@Parameter(label = "Ratio threshold:", min = "0")
	public double ratioThreshold = 2;

	//not an outside Parameter
	public boolean wasExecuted = false;

	@Override
	public void run()
	{
		wasExecuted = true;
		/* intentionally empty */
	}
}
