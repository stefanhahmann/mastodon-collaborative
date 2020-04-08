package org.mastodon.tomancak.net;

import java.io.*;
import java.net.URLDecoder;
import java.nio.file.Paths;
import java.util.Deque;
import java.util.Map;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.resource.PathResourceManager;
import org.mastodon.tomancak.util.LineageFiles;

public class FileServer
{
	static final int port = 7070;
	static final long transferMinSize = 100;

	static final String filesRootFolder = "/temp/put";

	public static void main(final String[] args)
	{
		final HttpHandler h = Handlers.path()
				.addPrefixPath("/put",   fileUploadHandler())
				.addPrefixPath("/list",  fileSkinnyListingHandler())
				.addPrefixPath("/files", filePrettyListingHandler())
				.addPrefixPath("/",      filePrettyListingHandler());

		Undertow server = Undertow.builder()
		  .addHttpListener(port, "localhost")
		  .setHandler(h)
		  .build();
		server.start();
	}

	public static
	HttpHandler filePrettyListingHandler()
	{
		return Handlers.resource(new PathResourceManager(Paths.get(filesRootFolder), transferMinSize))
		         .setDirectoryListingEnabled(true);
	}

	public static
	HttpHandler fileSkinnyListingHandler()
	{
		return new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception
			{
				final StringBuilder sb = new StringBuilder();
				LineageFiles.listLineageFiles(filesRootFolder).forEach(p -> {
					sb.append(p.getFileName().toString());
					sb.append('\n');
				});
				exchange.getResponseSender().send( sb.toString() );
			}
		};
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
				//if we got here, we're in a "worker thread" (and may potentially block ourselves later)

				final Map<String,Deque<String>> params = exchange.getQueryParameters();
				final Deque<String> _nameValue = params.get("name");
				final Deque<String> _noOfSpots = params.get("spots");
				final Deque<String> _noOfLinks = params.get("links");

				final String nameValue = _nameValue != null ? URLDecoder.decode(_nameValue.getFirst(),"UTF-8") : "fake file";
				final String noOfSpots = _noOfSpots != null ? _noOfSpots.getFirst() : "fake spot";
				final String noOfLinks = _noOfLinks != null ? _noOfLinks.getFirst() : "fake link";

				/*
				System.out.println("Query: name=" + nameValue);
				System.out.println("Query: spots=" + noOfSpots);
				System.out.println("Query: links=" + noOfLinks);
				*/

				//sanity test...
				if (!exchange.getRequestMethod().equalToString("POST"))
				{
					System.out.println("Going to complain...");
					exchange.getResponseSender().send("You gave me no file data!");
				}

				OutputStream fos
				  = new BufferedOutputStream(
				    new FileOutputStream(filesRootFolder+File.separator+nameValue) );

				exchange.startBlocking();
				exchange.getRequestReceiver().receivePartialBytes( (ex,bytes,flag) -> {
					//System.out.println("found: "+bytes.length);
					//System.out.println("flag : "+flag);
					try { if (bytes.length > 0) fos.write(bytes); } catch (IOException e) {/*yes,empty*/}
				} );

				fos.close();
			}
		};
	}

	public static
	String fileUploadQueryStringCreate(final String name, final int spotsCnt, final int linksCnt)
	{
		return String.format("?name=%s&spots=%d&links=%d",name,spotsCnt,linksCnt);
	}
}
