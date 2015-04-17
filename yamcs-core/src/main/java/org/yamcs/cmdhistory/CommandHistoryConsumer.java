package org.yamcs.cmdhistory;

import org.yamcs.commanding.PreparedCommand;
import org.yamcs.protobuf.Commanding.CommandId;
import org.yamcs.protobuf.Yamcs.Value;

/**
 * Interface implemented by all the classes that want to receive command history events.
 * @author mache
 *
 */
public interface CommandHistoryConsumer {
	/**
	 * Called when a new command matching the filters has been added to the history
	 * @param che
	 */
	void addedCommand(PreparedCommand pc);
	
	
	/**
	 * Called when the history of a command matching the filters has been updated
	 * @param kvp
	 */
	void updatedCommand(CommandId cmdId, long changeDate, String key, Value value);

}
