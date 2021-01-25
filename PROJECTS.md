# The project on a server

As explained, certain `string` identifies a project on the server. Let's denote
it as S in here. Further, recall [from the server setup page](SERVER.md) the
terms DATA, H, P (for the folder with the project data, for the hostname and
for the port, respectively).

Technically, a project S is represented with the folder DATA/S, and associated
to it is the URL: `http://H:P/S`. One can inspect project's content with any web
browser, just open that URL in it.


## Server commands
The server recognizes a couple of commands. The current set with short help
can always be obtained from the server itself, just point a web browser on it:
```
http://H:P
```

One command, for example, is to `add/DATASET`. Here, `DATASET` is our S, the
`string` that identifies a project. If you want to open a new project
`testProject` on the server, just put into your web browser the following line:
```
http://H:P/add/testProject
```
The opened page will report status of this operation. OK-status repeats the
project name under which the server will recognize it.

Alternatively, one can create *secret project* -- just use `addSecret` command
instead of plain `add`. The OK-status gives then slightly enhanced project name
that *needs to be used from this moment on*. For example:
```
http://192.168.3.128:7070/addSecret/testProject
```
returned `ef79147b-197f-439c-9529-b5261752e300-testProject`.

Of course, this suite includes Fiji plugin offering convenient GUI to create a
new project (which underneath does exactly what has just been described). The
"URL-way" is meant for troubleshooting or for scripts.

Similarly, an up-to-date list of commands relevant for a project can be obtained:
```
http://H:P/S
```
To continue with our example, try:
```
http://H:P/testProject
```


## Serving files
From the project-relevant commands, the most interesting is probably only the
`files` command. It lists content of the project folder in the returned web
page, every file is downloadable, every folder opens a new page with the new
folder listing.
```
http://H:P/S/files
```
This is ideal if one wants to
- inspect the project folder remotely (any collaborator can do that),
- share whatever data with collaborators
  (of interest could be, e.g., the main `.mastodon` project file).

One cannot, however, easily upload any file to the server (feel free to request
such feature). However, server administrator can place any file into the project
folder and that file will become immediately downloadable from the server, the
server needs not be restarted/notified or alike, just reload the relevant web page
in your web browser to test it.


## Status files
The server can be set up to to actually produce some files. When [Visual
progress reporting](SERVER.md) is enabled, the server re-creates `status.png`
and `status.html` files with every arriving snapshot. One can read the current
status on the following URLs:
```
http://H:P/S/files/status.png
http://H:P/S/files/status.html
```
For example:
```
http://helenos.fi.muni.cz:7070/Mette_2ndEmbryo/files/status.html
```
