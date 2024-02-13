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

        //start the server, and instruct it use "full" ProgressStores for every dataset
        new DatasetServer("/tmp/MastCollabServer/")
               .setUpdateHtmlTableStats(true)
               .setUpdateGnuplotPngStats(false)
               .start();
    }
}
