# What is mastodon-collaborative?
A suite of Mastodon plugins originally developed to foster collaborative tracking.

## The idea
The original purpose of this project was to enable tracking cells in the same
image data by multiple users (collaborators) independently, that is, on their
own laptops at their own pace. To make this effort an efficient one, ideally
users work in "separate corners" of the data so that no cell is tracked twice.
Users then somewhat regularly submit their current state of the work to a server.

The server is only a repository of tracking data from multiple users. It
collects contributions from users and offers it to anyone to download and to
incorporate into one's own tracking. Mastodon features simple-to-use plugins for
exactly these tasks. By design, the server does not automagically merge
anything into one giant tracking as this process can become fragile and must be
supervised if you want to trust the outcome of the collaborative tracking.

This concept requires a bit of an organization.
Ideally there is a project leader that
- prepares the Mastodon project,
- defines "separate (non-overlapping) corners" in the data,
- distribute project data to collaborators,
- explains and assigns work to collaborators,
- somewhat regularly merges recent contributions from the collaborators<br/>
  to check for issues and to detect overlaps rather soon,
- stops the collaboration and merges to obtain the final tracking.

To avoid having (potentially large) image data copied on every laptop, one may
want to set up a [BigDataServer](https://imagej.net/BigDataServer) to [host the
image data, and adjust Mastodon project path accordingly](BDS.md).
Collaborators, when tracking, download online a piece of the data that is
currently processed (displayed) in their laptops.

[Here it is explained in 2.5 min long video-presentation.](https://www.fi.muni.cz/~xulman/files/Mastodon/Collab__whatIsItAbout.mkv)

## The snapshots
At the heart of these plugins is the ability to create-and-store a snapshot of
an annotation of one's own images, as well as to load-and-merge-in a snapshot.
The annotation consists of a lineage trees and tag sets, it does not involve
the image data per se. The snapshot of an annotation is the exact content of it
(all spots and their positions, labels, colors and links) available for the
given images at the given time.

A snapshot can be understood as a lightweight alternative to saving the full
project. The snapshot itself does not hold any reference to the original
project -- one can, for example, merge snapshot from different project into
a current one.

Snapshots can be used to store progress of an on-going annotation, can be used
as named points of restore, and can be exchanged among annotators --
collaborating Mastodon users.

<!--
In the latter setting, users can agree to annotate mutually different
portions of the data and can work on their tasks simultaneously. This is
essentially a collaborative annotation of the data, which is further fostered
with a dedicated server implemented here to ease the snapshots exchange.
-->


## The server
The server is merely a convenient point of exchange of snapshots (or any files),
all organized into multiple projects.

To have own server running, one needs to have the following:
- Hosting computer accessible on a network
- Java 1.8 or newer installed
- Fiji with Mastodon installed
- Folder where server data will be stored

The hosting computer can be anything where Java runs. In particular it can be
Windows, Mac or Linux computer. And it can run only on local network unless one
wants to enable his collaborators to contribute from their homes or invite
collaborators from different institutions. In the latter two cases, please refer
to your system administrators to help you start the server on appropriately
accessible computers (e.g., via VPN, or with public IP address).

To start the server, one only needs to know how to open a command line, which could
be named variously in your operating system such as console, terminal or shell.
And then [look here to find the one-line command to start the server.](SERVER.md)

[It could be worthwhile to serve your image data too.](BDS.md)


# The project
Clearly, one server has to serve multiple collaborative endeavours, which is to
say, to serve multiple projects. It must separate and protect them from each other.
Here, it is achieved very straightforwardly.

A project is defined with a `string`, sequence of characters without blank spaces.
Examples can be from simple nickname-likes, e.g., "thirdEmbryo", to rather descriptive
ones such as "VladoLab_exp33_DAPIstainedNuclei_t2min_temp36C".

The `string` addresses a project. Whenever collaborators interfere with the
server, this `string` needs to be passed along for the server to understand
which project is the current communication related to. [Technical details
regarding projects are summarized on another page.](PROJECTS.md)

The server provides no mean for collaborators to list over projects it hosts.
Exception is the server administrator who has, of course, access to everything.
In general, however, one cannot access content without knowing a particular
`string`. To decrease a chance of guessing other project's `string`, we advice
to include long randomized sequence of characters into the `string` (Fiji
plugins from this suite can do this for you). This is popular concept used in
many online services.


## The project on a server
[Refer to this page to learn how projects are managed on the server.](PROJECTS.md)


## The project inside Mastodon
adding own project - own page
removing own project

uploading snapshot

When creating a snapshot, it is always created locally next to the main project
file (the `.mastodon` file). Snapshot files use `.mstdn` extension. One can,
however, also upload a snapshot to a dedicated server; and later download
snapshots from that server. This way, more people can be annotating (tracking)
the same data, every one only its portion,

downloading snapshot

# Customizing the plugins
The plugins come with no predefined short-cuts but you can assign shortcuts
yourself the usual Mastodon way: In the Mastodon preferences, Keymap,
filter for "lineage".


--------------------
old texts:

In Plugins->Collaborative->Save Current Lineage you can report your current state on a
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

In Plugins->Collaborative->Load External Lineage you can also download and inspect all
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
