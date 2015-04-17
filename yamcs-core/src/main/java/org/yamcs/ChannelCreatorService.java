package org.yamcs;

import java.util.Map;

import org.yamcs.parameter.RealtimeParameterService;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;

import com.google.common.util.concurrent.AbstractService;

/**
 * Used in yamcs.instance.yaml to create channels at yamcs startup
 * @author nm
 *
 */
public class ChannelCreatorService extends AbstractService {
    String channelName;
    String channelType;
    String channelSpec;

    Channel channel;
    String yamcsInstance;

    RealtimeParameterService realtimeParameterService;
    
    
    public ChannelCreatorService(String yamcsInstance, Map<String, String> config) throws ConfigurationException, StreamSqlException, ChannelException, ParseException {
	this.yamcsInstance = yamcsInstance;

	if(!config.containsKey("type")) {
	    throw new ConfigurationException("Did not specify the channel type");
	}
	this.channelType = config.get("type");
	if(!config.containsKey("name")) {
	    throw new ConfigurationException("Did not specify the channel name");
	}
	this.channelName = config.get("name");

	if(config.containsKey("spec")) {
	    channelSpec = config.get("spec");
	}

    }
    @Override
    protected void doStart() {
	try {
	    channel =  ChannelFactory.create(yamcsInstance, channelName, channelType, "system", channelSpec);
	    channel.setPersistent(true);
	    realtimeParameterService = new RealtimeParameterService(channel);
	    channel.start();
	    notifyStarted();
	} catch (Exception e) {
	    notifyFailed(e);
	}
    }

    @Override
    protected void doStop() {
	channel.quit();
	realtimeParameterService.quit();
	notifyStopped();
    }
}
