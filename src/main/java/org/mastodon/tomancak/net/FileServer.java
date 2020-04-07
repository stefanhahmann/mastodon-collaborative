package org.mastodon.tomancak.net;

import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.Map;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.PathResourceManager;

public class FileServer
{
	static final int port = 7070;

	static final String filesRootFolder = "/temp";
	static final long transferMinSize = 100;

	public static void main(final String[] args)
	{
		final HttpHandler h = Handlers.path()
				.addPrefixPath("/files", fileListingHandler())
				.addPrefixPath("/put", fileUploadHandler());

		Undertow server = Undertow.builder()
		  .addHttpListener(port, "localhost")
		  .setHandler(h)
		  .build();
		server.start();
	}

	public static
	HttpHandler fileListingHandler()
	{
		return Handlers.resource(new PathResourceManager(Paths.get(filesRootFolder), transferMinSize))
		         .setDirectoryListingEnabled(true);
	}

	public static
	HttpHandler fileUploadHandler()
	{
		return new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception
			{
				//dispatches the serving of this request to a "worker thread" in which blocking operations are legal
				if (exchange.isInIoThread())
				{
					exchange.dispatch(this);
					return;
				}

				final Map<String,Deque<String>> params = exchange.getQueryParameters();
				final Deque<String> _nameValue = params.get("name");
				final Deque<String> _noOfSpots = params.get("spots");
				final Deque<String> _noOfLinks = params.get("links");

				final String nameValue = _nameValue != null ?  _nameValue.getFirst() : "fake file";
				final String noOfSpots = _noOfSpots != null ?  _noOfSpots.getFirst() : "fake spot";
				final String noOfLinks = _noOfLinks != null ?  _noOfLinks.getFirst() : "fake link";

				System.out.println("Query: name=" + nameValue);
				System.out.println("Query: spots=" + noOfSpots);
				System.out.println("Query: links=" + noOfLinks);

				System.out.println("req method=" + exchange.getRequestMethod());
				final InputStream is = exchange.getInputStream();
				if (is != null)
				{
					System.out.println("some input stream:");
					//System.out.println("post="+ is);
				}
				else
				{
					System.out.println("no input stream");
				}
			}
		};
	}

	public static
	String fileUploadQueryStringCreate(final String name, final int spotsCnt, final int linksCnt)
	{
		return String.format("?name=%s&spots=%u&links=%u",name,spotsCnt,linksCnt);
	}
}
