package org.mastodon.tomancak;

import java.nio.file.Paths;

import org.mastodon.tomancak.monitors.ProgressStore;
import org.mastodon.tomancak.net.DatasetListeners;
import org.mastodon.tomancak.net.DatasetServer;

public class listenersTest
{
    static void genericListeners()
    {
        final DatasetServer ds = new DatasetServer("/temp/MastCollabServer/");
        ds.listeners.addFileRequestedListeners((d,s) -> System.out.println("Down "+s+" from "+d));
        ds.listeners.addFileArrivedListeners((d,s) -> System.out.println("Up "+s+" to "+d));
        ds.listeners.addLineageArrivedListeners(
                (dset,d,u,s,l) -> System.out.println("Lineage from "+u+" to "+dset+" of ("+s+","+l+") at "+d));

        DatasetListeners zListeners = ds.listeners.getDatasetListeners("z");
        if (zListeners != null) zListeners.addLineageArrivedListeners(
                (d,u,s,l) -> System.out.println("Lineage from "+u+" to ZZ of ("+s+","+l+") at "+d));

        zListeners = ds.listeners.getDatasetListeners("c");
        if (zListeners != null) zListeners.addLineageArrivedListeners(
                (d,u,s,l) -> System.out.println("Lineage from "+u+" to ZZ of ("+s+","+l+") at "+d));
    }

    public static void main(String[] args)
    {
        //genericListeners();

        //start the server
        final DatasetServer ds = new DatasetServer("/temp/MastCollabServer/");
        ds.start();

        //setup the ProgressStore for chosen dataset, and connect it to the server
        final ProgressStore ps = new ProgressStore("x");
        //
        ps.gnuplotSetupDirs(Paths.get("/temp/MastCollabServer/x/"));
        ps.htmlOutputFile = Paths.get("/temp/MastCollabServer/x/status.html");
        //
        ps.attachToThisServer(ds);
    }
}
