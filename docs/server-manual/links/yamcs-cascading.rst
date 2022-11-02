Yamcs Cascading Link
====================

The Yamcs Cascading Link functions as a client to an upstream Yamcs server. It provides the following data:
- TM packet reception in realtime and archive
- Parameter reception in realtime
- Event reception in realtime
- Command sending and Command History provision

The link is configured with one entry in the links section of the yamcs.<instance>.conf configuration file.


Class Name
----------

:javadoc:`org.yamcs.cascading.YamcsLink`

In the examples/cascading directory of the main yamcs repository there is a configuration with two Yamcs instances "upstream"
 and "downstream" demonstrating the cascading functionality.

Configuration Options
---------------------

upstreamName (string)
  **Required.** The name of the upstream Yamcs server. The name is used on the local Yamcs for the command history entries and for the sytem (/yamcs) parrameters.

yamcsUrl (string)
  **Required.** The URL to connect to the upstream Yamcs server; The URL has to include http or https.

username (string)
  Username to connect to the upstream Yamcs server (if authentication is enabled); has to be set together with password.

password (string)
  Password to connect to the upstream Yamcs server (if authentication is enabled); has to be set together with username.

upstreamInstance (string)
  **Required.** The instance of Yamcs on the upstream server.

verifyTls (boolean)
    If the connection is over SSL (when the yamcsUrl starts with https), this option can enable/disable the verification of the server certificate against
    local accepted CA list. Default: true

upstreamProcessor (string)
  The processor to connect to on the upstream Yamcs server. Default: realtime
  
tm (boolean)
  Subscribe telemetry containers (packets). The list of containers (packets) has to be specified using the containers option. Default: true

containers (list of strings)
  **Required if tm is true.** The list of containers(packets) to subscribe to. The list has to contain fully qualified names of containers.
   At this moment both the local (downstream) MDB and the upstream MDB have to contain definitions for the containers specified in this list.
   However, the local MDB can contain a more refined version. 
   For example the upstream MDB may define the container with just the header or a few parameters whereas the local MDB may define it in full and additionally
   other derived containers. 

tmRealtimeStream (string)
  Stream to which the TM packets will be sent. Default: "tm_realtime".

archiveTm (boolean)
  Enables TM archival. Default: true.

tmArchiveStream (string)
  Stream to which the TM packets will be archived. Default: "tm_dump".

gapFillingInterval (integer)
  Number of seconds between each archive gap filling attempt. Default: 300.

pp (boolean) 
  Subscribe parameters (pp stands for "processed parameters"). The list of parameters has to be specified using the parameters option. Default: true

parameters (list of strings)
  **Required if pp is true.** The list of parameters has to subscribe to. The list should contain fully qualified name of parameters which
  have to be present both in the local MDB and in the remote(upstream) MDB. Wildcards using glob patterns can be used.
  The requirement to have the parameters in both MDBs is a a current limitation due to the fact that we do not add parameters dynamically to the MDB.
  One exception is the Yamcs system parameters (those in the /yamcs namespace) - these do not have to be present in the local MDB, they are created on the fly.
  The /yamcs system parameters will be renamed such that /yamcs/a/b/c/parameter_name is saved in the local archive as /yamcs/upstreamName_a/b/c/parameter_name.

ppRealtimeStream (string)
  Stream to which the parameter packets will be sent. Default: "pp_realtime".

tc (boolean)
  Allow to send TC and subscribe to command history.
  All the command history entries received from the upstream server will be renamed to the shape yamcs<uspstreamName>_OriginalEntryName.
  Exception make those added in the keepUpstreamAcks configuration.
  Default: true

keepUpstreamAcks (list of strings)
  List of command acknowledgements names received from the upstream server to keep unmodified. 
  Default is "ccsds-seqcount" - this key is used by one of the CCSDS links to set the sequence count associated to the command and 
  required in the simulation configuration to be able to verify the command execution (because the sequence count is reported in returning telelmetry
   containing the command execution status).

event (boolean)
   Subscribe to realtime events. The events on the upstream server will be mirrorred to the local server.
   Default: true

eventRealtimeStream (string)
  Stream to which the events will be sent. Default: "events_realtime".

connectionAttempts (integer), OptionType.INTEGER).withDefault(20) 
  How many times to attempt reconnection if the connection fails. Reconnection will only be attempted once if the authentication fails.
  Link disable/enable is required to reattempt the connection once this number has passed.

reconnectionDelay (integer)
   If the connection fails or breaks, the time (in milliseconds) to wait before reconnection.
