# The server
To have own server running, one needs to have the following:

- Hosting computer that is
  - accessible on a network and its hostname (or IP address) needs to be known (H),
  - has TCP port opened for incoming traffic on its firewall (P),
- Java 1.8 or newer installed (JAVA)
- Fiji with Mastodon installed (FIJI)
- Folder where server data will be stored (DATA)

JAVA represents the Java executable, e.g., java, java.exe, or full path to java
binary if `java` alone is not recognized on the command line. For example, JAVA
could stand for `/home/xulman/java1.8/jre1.8.0_271/bin/java`. To test it, try:
```
JAVA -version
```
which must not say anything like "command not found".
Instead, it must return something similar to
```
java version "1.8.0_181"
Java(TM) SE Runtime Environment...
```

The shortcut FIJI is a path to where your Fiji is installed. Such path will
typically end with `Fiji.app`. Similarly, DATA is a path to a folder where
contributed snapshot files will be accumulated as well as a couple of
auxiliary files (will be explained below). Finally, the shortcuts H and P
stand for hostname (or IP address) and port, respectively. For example,
H could be `helenos.fi.muni.cz`, and P could be `7070`.

To test if the server can be executed at all, run the following in your terminal:
```
JAVA -cp "FIJI/jars/*" org.mastodon.tomancak.net.DatasetServer
```
That should print out a "short help":
```
I need these arguments: datasetsRootFolder [hostname [port]]
```


## Starting the server
The server is then started for real as follows:
```
JAVA -cp "FIJI/jars/*" org.mastodon.tomancak.net.DatasetServer DATA H P
```
(Please, replace the placeholders JAVA, DATA, H and P with their actual content.)<br/>
Now, please, don't close the window with the terminal as it'd most likely result
in closing the server too.

On Unix-like systems (Linux, Mac), one can run the server in a `screen` session:
```
screen -dmS MastodonCollabServer JAVA -cp "FIJI/jars/*" org.mastodon.tomancak.net.DatasetServer DATA H P
```
This detaches you server from the current terminal, and so you can close
terminal's window.

The server occasionally reports about its work on the standard output, that is
directly into the terminal or to the virtual `screen`. The reports typically
announce that new snapshot has arrived. The reports are, however, visible
only to the server's administrator, collaborators cannot see them.


## Stopping the server
The running server is a normal process of your operating system. You can,
therefore, use any standard tool of your system to close it, e.g. by killing the
process from command line or from activities monitor. The server has no special
closing sequence, nor it requires any delicate handling.

If you are running it directly from the terminal, pressing Ctrl+C suffices...


## Restarting the server
One can, of course, stop the running server at any time, and start it later
again. Just start it with same command as before.

During its starting, the server will check the content of the DATA folder
in order to re-recognize the served projects.


# Data organization on the server
[Please, refer to projects section.](PROJECTS.md)


# Visual progress reporting
There are special features available in the server that are *currently* not
controllable from the command line, and are currently
[turned off by default](https://github.com/xulman/mastodon-collaborative/blob/38dcdadfe9ca91ecf248f203b0f5f03ccd5ea808/src/main/java/org/mastodon/tomancak/net/DatasetServer.java#L91-L92).
One would need to re-compile the server source code to enable/disable it (please
contact ulman@fi.muni.cz for help).

The features essentially only log the incoming snapshots, and display it either
as plot or in tabular view. Logging is always per project.
TODO: add example screenshot and explain

Note: The plotting is rendered using [gnuplot](http://www.gnuplot.info/) -- a free,
command-line driven graphing utility for Linux and other platforms. This tool
needs to be installed on the hosting computer in order to have the plotting
functional (after one enables it in the server).


