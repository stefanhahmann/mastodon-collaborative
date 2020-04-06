package org.mastodon.tomancak;

import org.mastodon.project.MamutProject;
import org.mastodon.project.ReadZip;

import java.io.InputStream;
import java.io.IOException;
import java.util.Collection;

public class ZippedModelReader implements MamutProject.ProjectReader
{
	/** the main input source */
	private ReadZip zip;

	public ZippedModelReader(final String filename) throws IOException
	{
		zip = new ReadZip(filename);
	}

	@Override
	public InputStream getRawModelInputStream() throws IOException
	{
		return zip.getInputStream("model.raw");
	}

	@Override
	public InputStream getRawTagsInputStream() throws IOException
	{
		return zip.getInputStream("tags.raw");
	}

	@Override
	public void close() throws IOException
	{
	    zip.close();
	}


	/** fake input stream that saves nothing */
	private InputStream nullOutput = new InputStream() {
		@Override
		public int read()
		{ return 0; /* does nothing, intentionally */ }
	};

	// -------- methods that offer "no-read" streams --------
	@Override
	public InputStream getProjectXmlInputStream()
	{
		return nullOutput;
	}

	@Override
	public InputStream getFeatureInputStream(String featureKey)
	{
		return nullOutput;
	}

	@Override
	public Collection<String> getFeatureKeys() {
		return null;
	}
}
