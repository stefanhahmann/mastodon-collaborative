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
import static org.mastodon.tomancak.monitors.ProgressStore.createAttachedProgressStore;

import org.mastodon.tomancak.util.LineageFiles;
import org.mastodon.mamut.model.Model;
import java.time.LocalDateTime;

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
			new DatasetServer(args[0]).start();
		else if (args.length == 2)
			new DatasetServer(args[0]).setHostname(args[1]).start();
		else
			new DatasetServer(args[0]).setHostname(args[1]).setPort(Integer.parseInt(args[2])).start();
	}

	/** create the dataset with the only mandatory parameter: folder where data is and should be */
	public DatasetServer(final String datasetsRootFolder)
	{
		//setup all final attributes...
		this.datasetsRootFolder = Paths.get(datasetsRootFolder);
		if (!this.datasetsRootFolder.toFile().isDirectory())
			throw new RuntimeException("Cannot start server, folder does not exists: "+datasetsRootFolder);
		this.requestsRooter = Handlers.path();
	}

	/** optional: set the port on which the server will be listening */
	public DatasetServer setPort(final int port)
	{ this.port = port; return this; }

	/** optional: set the hostname on which the server will be listening */
	public DatasetServer setHostname(final String hostname)
	{ this.hostname = hostname; return this; }

	/** optional: enable that the server will be creating gnuplot's PNG to monitor reported progresses */
	public DatasetServer setUpdateGnuplotPngStats(final boolean enabled)
	{ this.updateGnuplotPngStats = enabled; return this; }

	/** optional: enable that the server will be creating HTML table to monitor reported progresses */
	public DatasetServer setUpdateHtmlTableStats(final boolean enabled)
	{ this.updateHtmlTableStats = enabled; return this; }

	/** the default port if none is provided to start a server */
	public static final int defaultPort = 7070;

	/** the actual port on which the server will start */
	private int port = defaultPort;
	private String hostname = "localhost";
	private boolean updateGnuplotPngStats = false;
	private boolean updateHtmlTableStats  = false;

	/** actually starts the server with the specific settings */
	public void start()
	{
		requestsRooter
		  .addPrefixPath("/add",       addDatasetHandler(false))
		  .addPrefixPath("/addSecret", addDatasetHandler(true))
		  .addPrefixPath("/remove",    removeDatasetHandler())
		  .addExactPath( "/",          helpListingHandler());

		Undertow server = Undertow.builder()
		  .addHttpListener(port, hostname)
		  .setHandler(requestsRooter)
		  .build();

		//setUpdateGnuplotPngStats(true);
		//setUpdateHtmlTableStats(true);

		System.out.println("Starting server "+hostname+":"+port+" over "+datasetsRootFolder);
		System.out.println("  will be updating gnuplot PNG: "+updateGnuplotPngStats
		                  +", HTML table: "+updateHtmlTableStats);
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
					requestsRooter.addPrefixPath("/"+datasetStr,
						FileServer.createDatasetHttpHandler( datasetPath, createListenersForDataset(datasetStr) ));
					createAttachedProgressStore(datasetPath,this,updateGnuplotPngStats,updateHtmlTableStats);
					System.out.println("Auto-created a dataset handler for files in "+datasetPath);
				}
			});
			datasetFolders.close();
		} catch (IOException e) { /* just ignore errors */ }
	}


	// --------------------- dataset management ---------------------
	final Path datasetsRootFolder;
	final PathHandler requestsRooter;

	HttpHandler addDatasetHandler(final boolean prefixWithSecret)
	{
		return new HttpHandler() {
			DatasetServer myDS; //needed for createAttachedProgressStore() below
			HttpHandler setDS(final DatasetServer ds)
			{ myDS = ds; return this; }

			@Override
			public void handleRequest(HttpServerExchange exchange)
			{
				String datasetStr = extractDatasetString(exchange);
				if (datasetStr == null) { respondERROR(exchange); return; }

				if (prefixWithSecret) datasetStr = java.util.UUID.randomUUID().toString() +'-'+ datasetStr;
				if (datasetStr.equals("add") || datasetStr.equals("addSecret") || datasetStr.equals("remove"))
				{
					//forbidden folder names
					System.out.println("Refused to create a dataset of the name "+datasetStr);
					respondERROR(exchange);
					return;
				}

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
				requestsRooter.addPrefixPath("/"+datasetStr,
					FileServer.createDatasetHttpHandler( datasetPath, createListenersForDataset(datasetStr) ));
				createAttachedProgressStore(datasetPath,myDS,updateGnuplotPngStats,updateHtmlTableStats);

				System.out.println("Created a dataset handler for files in "+datasetPath);
				exchange.getResponseSender().send(datasetStr);
				//respondOK(exchange);
			}
		}.setDS(this);
	}

	HttpHandler removeDatasetHandler()
	{
		return exchange -> {
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
				removeListenersForDataset(datasetStr);
				requestsRooter.removePrefixPath("/"+datasetStr);

				System.out.println("Removed a dataset handler and "+DAC.foldersCnt+" folders and "+DAC.filesCnt+" files in "+datasetPath);
				respondOK(exchange);
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


	// --------------------- listeners management ---------------------
	public final ServerListeners listeners = new ServerListeners();

	private
	DatasetListeners createListenersForDataset(final String dataset)
	{
		final DatasetListeners dsL = new DatasetListeners(dataset,listeners);
		listeners.datasetListeners.put(dataset,dsL);
		return dsL;
	}

	private
	void removeListenersForDataset(final String dataset)
	{
		listeners.datasetListeners.remove(dataset);
	}

	public
	void replayLineageArrivedOnDataset(DatasetListeners.LineageArrived replayListeners, final String onThisDataset)
	{
		final Model model = new Model(); //aux model
		final Path dsPath = datasetsRootFolder.resolve(onThisDataset);
		try {
			LineageFiles.listLineageFiles(dsPath).forEach( f -> {
				try {
					LineageFiles.loadLineageFileIntoModel(f,model);
				} catch (IOException e) {
					System.out.println("This is very surprising on file "+f+", should not happen:");
					e.printStackTrace();
					return; //skip over the rest of this block
				}

				//read out the date from the discovered file
				final String filename = f.getFileName().toString();
				final String date = LineageFiles.dateOfLineageFile(filename);
				LocalDateTime lDT = LocalDateTime.of(
						Integer.parseInt(date.substring(0,4)),
						Integer.parseInt(date.substring(5,7)),
						Integer.parseInt(date.substring(8,10)),
						Integer.parseInt(date.substring(12,14)),
						Integer.parseInt(date.substring(15,17)),
						Integer.parseInt(date.substring(18,20)) );
				replayListeners.action(lDT, LineageFiles.authorOfLineageFile(filename),
						model.getGraph().vertices().size(), model.getGraph().edges().size());
			});
		} catch (IOException e) {
			System.out.println("Path "+dsPath+" is most likely not accessible.");
		}
	}
}
