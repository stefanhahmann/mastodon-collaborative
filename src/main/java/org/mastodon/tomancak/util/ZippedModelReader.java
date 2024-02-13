/*-
 * #%L
 * mastodon-collaborative
 * %%
 * Copyright (C) 2020 - 2024 Vladimir Ulman
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.tomancak.util;

import org.mastodon.mamut.io.project.MamutProject;
import org.mastodon.mamut.io.project.ReadZip;

import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

public class ZippedModelReader implements MamutProject.ProjectReader
{
	/** the main input source */
	private final ReadZip zip;

	public ZippedModelReader(final Path filename) throws IOException
	{
		zip = new ReadZip(filename.toFile());
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
	private final InputStream nullOutput = new InputStream() {
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

	@Override
	public InputStream getGuiInputStream() throws IOException {
		return null;
	}

	@Override
	public InputStream getBackupDatasetXmlInputStream() throws IOException {
		return null;
	}
}
