package org.yamcs.tctm;

import org.yamcs.cmdhistory.CommandHistoryPublisher;
import org.yamcs.commanding.PreparedCommand;

/**
 * Interface implemented by components that send commands to the outer universe
 * 
 * @author nm
 *
 */
public interface TcDataLink extends Link {
    
    void sendTc(PreparedCommand preparedCommand);

    void setCommandHistoryPublisher(CommandHistoryPublisher commandHistoryPublisher);

	/**
	 * Get the name of the stream to receive telecommands from.
	 *
	 * @return name of stream
	 */
	public String getTcStreamName();
}
