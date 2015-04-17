package org.yamcs.tctm;

import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.cmdhistory.CommandHistory;
import org.yamcs.commanding.CommandReleaser;
import org.yamcs.commanding.PreparedCommand;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;
/**
 * Sends commands to yarch streams
 * @author nm
 *
 */
public class YarchTcCommandReleaser extends AbstractService implements CommandReleaser {
    Stream stream;
    String streamName;
    String yamcsInstance; 
    
    volatile long sentTcCount;
    
    public YarchTcCommandReleaser(String yamcsInstance, Map<String, String> config) throws ConfigurationException {
		this.yamcsInstance = yamcsInstance;
		if(!config.containsKey("stream")) {
			throw new ConfigurationException("Please specify the stream in the config (args)");
		}
		this.streamName = config.get("stream");
    }

	@Override
	public void releaseCommand(PreparedCommand pc) {
		stream.emitTuple(pc.toTuple());
        sentTcCount++;
	}
	
    @Override
	protected void doStart() {
    	YarchDatabase ydb = YarchDatabase.getInstance(yamcsInstance);
    	stream = ydb.getStream(streamName);
    	if(stream==null) {
    		ConfigurationException e = new ConfigurationException("Cannot find stream '"+streamName+"'");
    		notifyFailed(e);
    	}
    	notifyStarted();
	}
    

    @Override
    public void setCommandHistory(CommandHistory commandHistoryListener) {
        
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }


}