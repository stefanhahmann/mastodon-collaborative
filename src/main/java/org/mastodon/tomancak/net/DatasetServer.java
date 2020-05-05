package org.mastodon.tomancak.net;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;

public class DatasetServer
{
	// --------------------- server management ---------------------
	/** the entry to start the server is here, run without args to read out "how to use" */
	public static void main(final String[] args)
	{
		if (args.length < 1 || args.length > 3)
		{
			System.out.println("I need these arguments: datasetsRootFolder [hostname [port]]");
			return;
		}

		if (args.length == 1)
			new DatasetServer(args[0]);
		else if (args.length == 2)
			new DatasetServer(args[0], args[1], defaultPort);
		else
			new DatasetServer(args[0], args[1], Integer.parseInt(args[2]));
	}

	/** actually starts the server at specific hostname and port */
	public DatasetServer(final String datasetsRootFolder, final String hostname, final int port)
	{
		this.datasetsRootFolder = Paths.get(datasetsRootFolder);

		requestsRooter = Handlers.path()
		  .addPrefixPath("/add",       addDatasetHandler(false))
		  .addPrefixPath("/addSecret", addDatasetHandler(true))
		  .addPrefixPath("/remove",    removeDatasetHandler())
		  .addExactPath( "/",          helpListingHandler());

		Undertow server = Undertow.builder()
		  .addHttpListener(port, hostname)
		  .setHandler(requestsRooter)
		  .build();

		System.out.println("Starting server "+hostname+":"+port+" over "+datasetsRootFolder);
		server.start();

		//check if there are already any folders/datasets in there and register FileServer handlers for them
		try {
			Stream<Path> datasetFolders = Files.walk(this.datasetsRootFolder,1);
			datasetFolders.forEach( datasetPath -> {
				//double-check that the item is really an existing folder,
				//and filter out our own root folder (e.q. '.' folder on Unixes)
				final File datasetAsFile = datasetPath.toFile();
				if (datasetAsFile.isDirectory() && !this.datasetsRootFolder.equals(datasetPath))
				{
					final String datasetStr = datasetAsFile.getName();
					requestsRooter.addPrefixPath("/"+datasetStr, FileServer.createDatasetHttpHandler(datasetPath));
					System.out.println("Auto-created a dataset handler for files in "+datasetPath);
				}
			});
			datasetFolders.close();
		} catch (IOException e) { /* just ignore errors */ }
	}

	/** actually starts the server at default hostname and port */
	public DatasetServer(final String datasetsRootFolder)
	{
		this(datasetsRootFolder,"localhost",defaultPort);
	}

	/** the default port if none is provided to start a server */
	public static final int defaultPort = 7070;


	// --------------------- dataset management ---------------------
	final Path datasetsRootFolder;
	final PathHandler requestsRooter;

	HttpHandler addDatasetHandler(final boolean prefixWithSecret)
	{
	    return new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange)
			{
				String datasetStr = extractDatasetString(exchange);
				if (datasetStr == null) { respondERROR(exchange); return; }

				if (prefixWithSecret) datasetStr = java.util.UUID.randomUUID().toString() +'-'+ datasetStr;

				//which folder shall be created
				final Path datasetPath = datasetsRootFolder.resolve(datasetStr);

				//filesystem stuff
				if (datasetPath.toFile().exists() || !datasetPath.toFile().mkdir())
				{
					//the folder already exist or cannot be created
					System.out.println("Refused to create a dataset handler for files in "+datasetPath);
					respondERROR(exchange);
					return;
				}

				//HTTP stuff
				requestsRooter.addPrefixPath("/"+datasetStr, FileServer.createDatasetHttpHandler(datasetPath));

				System.out.println("Created a dataset handler for files in "+datasetPath);
				exchange.getResponseSender().send(datasetStr);
				//respondOK(exchange);
			}
		};
	}

	HttpHandler removeDatasetHandler()
	{
		return new HttpHandler() {
			@Override
			public void handleRequest(HttpServerExchange exchange) throws Exception
			{
				final String datasetStr = extractDatasetString(exchange);
				if (datasetStr == null) { respondERROR(exchange); return; }

				//which folder shall be removed
				final Path datasetPath = datasetsRootFolder.resolve(datasetStr);

				//filesystem stuff
				if (!datasetPath.toFile().exists())
				{
					//the folder does not exist
					System.out.println("Refused to remove a dataset handler for files in "+datasetPath);
					respondERROR(exchange);
					return;
				}

				// delete recursively (with counting): create a stream
				Stream<Path> files = Files.walk(datasetPath);

				class deleteAndCount
				{
					long filesCnt = 0;
					long foldersCnt = 0;
					void delete(final File f)
					{
						foldersCnt += f.isDirectory() && f.delete() ? 1 : 0;
						filesCnt   += f.isFile() && f.delete() ? 1 : 0;
					}
				}
				deleteAndCount DAC = new deleteAndCount();

				// delete directory including files and sub-folders
				files.sorted(Comparator.reverseOrder())
				  .map(Path::toFile)
				  .forEach(DAC::delete);

				// close the stream
				files.close();

				//HTTP stuff
				requestsRooter.removePrefixPath("/"+datasetStr);

				System.out.println("Removed a dataset handler and "+DAC.foldersCnt+" folders and "+DAC.filesCnt+" files in "+datasetPath);
				respondOK(exchange);
			}
		};
	}

	String extractDatasetString(final HttpServerExchange exchange)
	{
		String dataset = exchange.getRelativePath();
		System.out.println("Processing request: "+dataset);

		if (dataset.length() <= 1)
		{
			System.out.println("Requested to manage a dataset without giving its name.");
			return null;
		}

		int nextSlashPos = dataset.indexOf('/',1);
		dataset = nextSlashPos < 0 ? dataset.substring(1) : dataset.substring(1,nextSlashPos);

		System.out.println("Extracted  DATASET: "+dataset);
		return dataset;
	}

	void respondOK(final HttpServerExchange exchange)
	{
		exchange.getResponseSender().send("OK");
	}

	void respondERROR(final HttpServerExchange exchange)
	{
		exchange.getResponseSender().send("ERROR ");
	}


	// --------------------- help listing ---------------------
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

				writeLine(newLine+"Dataset management:");
				writeLine(        "-------------------");
				writeLine("/add/DATASET\t-- adds a new DATASET to this server");
				writeLine("            \t-- returns either DATASET or ERROR textual response");
				writeLine("/addSecret/DATASET\t-- creates random (and hard to guess) PREFIX sequence");
				writeLine("                  \t-- adds a PREFIX-DATASET to this server");
				writeLine("                  \t-- returns either PREFIX-DATASET or ERROR textual response");
				writeLine("/remove/DATASET\t-- removes DATASET from this server");
				writeLine("               \t-- returns either OK or ERROR textual response");

				writeLine(newLine+"Dataset operations:");
				writeLine(        "-------------------");
				writeLine("/DATASET\t-- lists operations available for the DATASET");

				writeLine(newLine+"Details:");
				writeLine(        "--------");
				writeLine("DATASET -- is a text made of characters legal for the URL and file systems");
				writeLine("        -- maps into a folder on the server");
				writeLine("        -- is the only \"key\" to read/write access the data");
				writeLine("        -- should include long random substring in the name if you want");
				writeLine("           to make it hard to guess the name and gain unwanted access consequently");
			}

			@Override
			public void handleRequest(HttpServerExchange exchange)
			{
				exchange.getResponseSender().send(sb.toString());
			}
		};
	}
}
