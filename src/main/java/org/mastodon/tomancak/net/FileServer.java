package org.mastodon.tomancak.net;

import java.nio.file.Paths;
import io.undertow.Undertow;
import io.undertow.server.handlers.resource.PathResourceManager;
import static io.undertow.Handlers.resource;

public class FileServer
{
	static final String filesRootFolder = "/temp";
	static final long transferMinSize = 100;
	static final int port = 7070;

	public static void main(final String[] args)
	{
		Undertow server = Undertow.builder()
			.addHttpListener(port, "localhost")
			.setHandler(resource(new PathResourceManager(Paths.get(filesRootFolder), transferMinSize))
			.setDirectoryListingEnabled(true))
			.build();
		server.start();
	}
}
