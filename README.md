# mastodon-collaborative
Mastodon plugins to foster collaborative annotations


ere [1], you can download a standalone ImageJ2 with the most recent Mastodon
and with the progress reporting and inspecting plugins. You need to have Java
1.8 or later installed on your computer, e.g. this one [2].

[1] http://helenos.fi.muni.cz:7070/racingMastodon.zip
[2] https://www.java.com/en/download/
[2b] https://www.java.com/en/download/manual.jsp

To run this, unzip the .zip file and run start.sh if you're on Mac or Linux, or
start.bat if you're on Windows. "Fiji" and Mastodon window should open.
Then load the project as usual, and off you go...

In Plugins->Racing->Save Current Lineage you can report your current state on a
server. Check, please, your nickname in the dialog. The rest shall be preset, in
particular the "remote monitor" URL should be: http://helenos.fi.muni.cz:7070
(that is computer exclusive under my control running in a 24/7 with public IP,
once all is debugged and settled, I would love to run this on the bds.mpi-cbg.de)

Whenever you submit a report, a copy of what has been submitted is stored next
to your .mastodon project file (one .mstdn file per every report).

You can see what reports are available by opening the http://helenos.fi.muni.cz:7070 in your web browser.
For instance, http://helenos.fi.muni.cz:7070/log.txt, will tell you what has been uploaded and when.
Soon, the http://helenos.fi.muni.cz:7070/hello.html will have some sensible content...

You can even download any report/snapshot file (.mstdn) and save it next to your .mastodon file manually...

In Plugins->Racing->Load External Lineage you can also download and inspect all
reports available on the server. When you open the plugin, a dialog pops up and
lists what files are available. The files can come in three categories: "Local
only", "Synced" and "Remote only". "Synced" are those that exist both on your
local drive and on the server too. Only "Remote only" are new to your
installation -- and they turn "Synced" if you choose them because any chosen
file is first downloaded (and kept forever) and only then opened. Since the files
are supposed to be _snapshots_, they should not be changed anymore after they
are created (but you know, it is only a file on a hard drive, you can do it any
harm you want...). Finally, you decide how you want to present the chosen file:
Replace the current content (not resetting the view and windows layout) or Merge
to the current content. In any case, your current lineage will be changed, and
nothing is saved. The plugin does not touch your proper .mastodon file (not
even for reading). The merging is done via the standard Merging plugin.

The server and the snapshots are stupid -- they have no information to what
project they belong to. Please don't update snapshots from different projects to
the server/monitor now.

Any authentication, encryption or other means of security will come later...
after extending the server part with charts-creator module (don't wait for it,
submit already, regularly :-) and project-autosaving plugin.

The both plugins come with no predefined short-cuts, you can assign the shortcut
yourself: In the Mastodon preferences, Keymap, filter for "lineage"...

I'm around to support you.
Vladimir Ulman


--------------------
Here's some more details/motivation:

The original intention was to monitor progress of everyone.
The plan was that people are sending somewhat regularly their lineages to the server --
seeing how much they added w.r.t. others should be a motivating factor.

In normal operation, people need not to merge anything, inspect etc.
However, it does not hurt to do so to check if you are not crossing tracking with somebody else.

The *main person* can check how people are doing, if they are on the right tracks, etc,
and to be able to check again somewhat regularly. That person can see, just like everybody, everything that is submitted so far.

But the reporting does not happen automatically.
When update is sent, your complete lineage is transferred, no updates/increments, always the full thing;
and it sends it only when you ask it to do so.
