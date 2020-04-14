package org.mastodon.tomancak;

import net.imagej.ImageJ;
import org.mastodon.tomancak.util.MergeModelDialog;

public class clearPrefs
{
	public static void main(String[] args)
	{
		final ImageJ IJ = new ImageJ();
		IJ.prefs().clear(LoadEarlierProgress.class);
		IJ.prefs().clear(ReportProgress.class);
		IJ.prefs().clear(MergeModelDialog.class);
		System.out.println("RACING plugins' preferences cleared");
	}
}
