package org.yamcs.xtceproc;


import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ContainerExtractionResult;
import org.yamcs.ItemIdPacketConsumerStruct;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;

/**
 * 
 * Extracts parameters out of packets based on the XTCE description
 *
 * 
 *  @author mache
 * 
 */

public class XtceTmExtractor {
    private static final Logger log=LoggerFactory.getLogger(XtceTmExtractor.class);
    protected final Subscription subscription;
    private ProcessingStatistics stats=new ProcessingStatistics();

    public final XtceDb xtcedb;
    final SequenceContainer rootContainer;
    ParameterValueList paramResult=new ParameterValueList();
    ArrayList<ContainerExtractionResult> containerResult=new ArrayList<ContainerExtractionResult>();

    /**
     * Creates a TmExtractor extracting data according to the XtceDb
     * @param xtcedb
     */
    public XtceTmExtractor(XtceDb xtcedb) {
	this.xtcedb=xtcedb;
	this.subscription=new Subscription(xtcedb);
	rootContainer=xtcedb.getRootSequenceContainer();
    }

    /**
     * Adds a parameter to the current subscription list: 
     *  finds all the SequenceContainers in which this parameter may appear and adds them to the list.
     *  also for each sequence container adds the parameter needed to instantiate the sequence container.
     * @param param parameter to be added to the current subscription list 
     */
    public void startProviding(Parameter param) { 
	synchronized(subscription) {
	    subscription.addParameter(param);
	}
    }

    /**
     * Adds all containers and parameters to the subscription
     */
    public void startProvidingAll() {
	for (SequenceContainer c : xtcedb.getSequenceContainers()) {
	    if (c.getBaseContainer() == null) {
		subscription.addAll(c);
	    }
	}
    }

    public void stopProviding(Parameter param) {
	//TODO 2.0 do something here
    }

    /**
     * Extract one packet, starting at the root sequence container
     */
    public void processPacket(ByteBuffer bb, long generationTime) {
	processPacket(bb, generationTime, rootContainer);
    }

    /**
     * Extract one packet, starting at the specified container.
     */
    public void processPacket(ByteBuffer bb, long generationTime, SequenceContainer startContainer) {
	try {
	    paramResult=new ParameterValueList();
	    containerResult=new ArrayList<ContainerExtractionResult>();
	    synchronized(subscription) {
		long aquisitionTime=TimeEncoding.currentInstant(); //we do this in order that all the parameters inside this packet have the same acquisition time
		ProcessingContext pcontext=new ProcessingContext(bb, 0, 0, subscription, paramResult, containerResult, aquisitionTime, generationTime, stats);
		pcontext.sequenceContainerProcessor.extract(startContainer);
	    }
	} catch (Exception e) {
	    log.error("got exception in tmextractor ", e);
	}
    }

    public void resetStatistics() {
	stats.reset();
    }

    public ProcessingStatistics getStatistics(){
	return stats;
    }


    public void startProviding(SequenceContainer sequenceContainer) {
	synchronized(subscription) {
	    subscription.addSequenceContainer(sequenceContainer);
	}
    }

    public void stopProviding(SequenceContainer sequenceContainer) {
	//TODO
    }

    public void subscribePackets(List<ItemIdPacketConsumerStruct> iipcs) {
	synchronized(subscription) {
	    for(ItemIdPacketConsumerStruct i:iipcs) {
		subscription.addSequenceContainer(i.def);
	    }
	}
    }

    public ParameterValueList getParameterResult() {
	return paramResult;
    }

    public ArrayList<ContainerExtractionResult> getContainerResult() {
	return containerResult;
    }

    public Subscription getSubscription() {
	return subscription;
    }

    @Override
    public String toString() {
	return subscription.toString();
    }
}
