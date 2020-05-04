package org.mastodon.tomancak.net;

import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.file.Path;
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
	public FileServer(final String filesRootFolder, final String hostname, final int port)
	{
		this.filesRootFolder = Paths.get(filesRootFolder);

		HttpHandler h = Handlers.path()
		  .addPrefixPath("/put",   fileUploadHandler())
		  .addPrefixPath("/list",  fileSkinnyListingHandler())
		  .addPrefixPath("/files", filePrettyListingHandler())
		  .addPrefixPath("/",      helpListingHandler());

		Undertow server = Undertow.builder()
		  .addHttpListener(port, hostname)
		  .setHandler(h)
		  .build();

		System.out.println("Starting server "+hostname+":"+port+" over "+filesRootFolder);
		server.start();
	}

	public FileServer(final String filesRootFolder)
	{
		this(filesRootFolder,"localhost",defaultPort);
	}


	final Path filesRootFolder;

	HttpHandler filePrettyListingHandler()
	{
		return Handlers.resource(new PathResourceManager(filesRootFolder))
		         .setDirectoryListingEnabled(true);
	}

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
				    new FileOutputStream(filesRootFolder.resolve(nameValue).toFile()) );

				exchange.startBlocking();
				exchange.getRequestReceiver().receivePartialBytes( (ex,bytes,flag) -> {
					//System.out.println("found: "+bytes.length);
					//System.out.println("flag : "+flag);
					try { if (bytes.length > 0) fos.write(bytes); } catch (IOException e) {/*yes,empty*/}
				} );

				fos.close();
				System.out.println("Just stored: " + nameValue + " (" + noOfSpots + "," + noOfLinks + ")");
			}
		};
	}

	HttpHandler helpListingHandler()
	{
		return new HttpHandler() {
			static final char newLine = '\n';
			final StringBuilder sb = new StringBuilder();

			void writeLine(final String l)
			{ sb.append(l); sb.append(newLine); }

			{
				writeLine("Listings:");
				writeLine("---------");
				writeLine("/\t-- accessing root folder of the server prints this help");
				writeLine("/files\t-- lists all files and folders that the server sees");
				writeLine("/list\t-- lists all snapshot files (files matching the specific filename syntax) that the server sees");
				writeLine("\t-- printed in plain text, one file per row");

				writeLine(newLine+"Download/Upload:");
				writeLine(        "----------------");
				writeLine("/snapshot.mstdn -- downloads the 'snapshot.mstdn' file from the server");
				writeLine("/put?name=snapshot.mstdn&spots=100&links=99");
				writeLine("\t-- uploads a file to the server via the POST method");
				writeLine("\t-- parameters name, spots, links are mandatory");
				writeLine("\t\tname -- specifies the name under which the uploaded file will be saved");
				writeLine("\t\t     -- the file should be some snapshot file, here 'snapshot.mstdn'");
				writeLine("\t\tspots -- specifies the number of spots in the uploaded snapshot");
				writeLine("\t\tlinks -- specifies the number of links in the uploaded snapshot");
				writeLine("\t\t      -- both spots and links are here to avoid scanning the content of the snapshot file");

				writeLine(newLine+"Details:");
				writeLine(        "--------");
				writeLine("snapshot.mstdn -- includes (only) a complete lineage as of given point in time from a certain user");
				writeLine("               -- collection of these from one user shows her annotation progress");
				writeLine("               -- here, it is a simplified substitute name for a properly named files");
				writeLine("proper syntax is: YYYY-MM-DD__HH-MM-SS__userIdentifingAnyString.mstdn");
			}

			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception
			{
				exchange.getResponseSender().send(sb.toString());
			}
		};
	}


	/** the default port if none is provided to start a server */
	public static final int defaultPort = 7070;

	/**
	 * requires/collects and formats the mandatory data into
	 * a proper query that this server can understand
	 */
	public static
	String fileUploadQueryStringCreate(final String name, final int spotsCnt, final int linksCnt)
	{
		return String.format("?name=%s&spots=%d&links=%d",name,spotsCnt,linksCnt);
	}


	public static void main(final String[] args)
	{
		if (args.length < 1 || args.length > 3)
		{
			System.out.println("I need these arguments: filesRootFolder [hostname [port]]");
			return;
		}

		if (args.length == 1)
			new FileServer(args[0]);
		else if (args.length == 2)
			new FileServer(args[0], args[1], defaultPort);
		else
			new FileServer(args[0], args[1], Integer.parseInt(args[2]));
	}
}
