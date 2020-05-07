package org.mastodon.tomancak;

import java.nio.file.Paths;

import org.mastodon.tomancak.monitors.StatusStore;
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

        final DatasetServer ds = new DatasetServer("/temp/MastCollabServer/");
        final StatusStore ss = new StatusStore(ds.listeners, "x");

        //this activates the gnuplot outputs... but:
        // - the output folder must be already existing
        // - a special gnuplot script has to be placed in there
        ss.gnuplotOutputFolder = Paths.get("/temp/MastCollabServer/x/gnuplot");
    }
}
