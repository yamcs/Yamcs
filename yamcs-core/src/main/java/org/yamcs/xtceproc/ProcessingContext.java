package org.yamcs.xtceproc;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.yamcs.ContainerExtractionResult;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.CriteriaEvaluator;


/**
 * Keeps track of where we are when processing a packet.
 * @author nm
 *
 */
public class ProcessingContext {	
	ByteBuffer bb;
	public int bitPosition;
	
	//Keeps track of the absolute offset of the container where the processing takes place. 
	//Normally 0, but if the processing takes place inside a subcontainer, it reflects the offset of that container with respect to the primary container where the processing started 
	int containerAbsoluteByteOffset; 	
	
	Subscription subscription;
	
	//this is the result of the processing
	public ParameterValueList paramResult;
	public ArrayList<ContainerExtractionResult> containerResult;
	
	public long acquisitionTime;
	public long generationTime;
	public long expirationTime = TimeEncoding.INVALID_INSTANT;
	
	ProcessingStatistics stats;
	
	public SequenceContainerProcessor sequenceContainerProcessor=new SequenceContainerProcessor(this);
	public SequenceEntryProcessor sequenceEntryProcessor=new SequenceEntryProcessor(this);
	public ParameterTypeProcessor parameterTypeProcessor=new ParameterTypeProcessor(this);
	public DataEncodingDecoder dataEncodingProcessor=new DataEncodingDecoder(this);
	public ValueProcessor valueProcessor=new ValueProcessor(this);
	public CriteriaEvaluator criteriaEvaluator;
	
	public ProcessingContext(ByteBuffer bb, int containerAbsoluteByteOffset, int bitPosition, Subscription subscription, 
		ParameterValueList params, ArrayList<ContainerExtractionResult> containers, 
	        long acquisitionTime, long generationTime, ProcessingStatistics stats) {
		this.bb = bb;
		this.containerAbsoluteByteOffset=containerAbsoluteByteOffset;
		this.bitPosition = bitPosition;
		this.subscription = subscription;
		this.paramResult = params;
		this.containerResult=containers;
		this.acquisitionTime = acquisitionTime;
		this.generationTime = generationTime;
		this.stats = stats;
		this.criteriaEvaluator = new CriteriaEvaluatorImpl(paramResult);
	}
}
