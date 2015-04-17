package org.yamcs.xtceproc;

import java.util.Comparator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ParameterValue;
import org.yamcs.parameter.ParameterValueList;
import org.yamcs.protobuf.Yamcs.Value;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.ParameterInstanceRef;

import com.google.common.primitives.UnsignedBytes;

/**
 * Implements XTCE processing for {@link org.yamcs.xtce.Comparison}
 * @author nm
 *
 */
public class ComparisonProcessor {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());
    final ParameterValueList params;

    /**
     * @param params pvals within 'scope'. Identifier resolution is performed against these pvals
     */
    public ComparisonProcessor(ParameterValueList params) {
	this.params = params;
    }

    public boolean matches(MatchCriteria mc) {
	if(mc instanceof ComparisonList) {
	    return matchesComparisonList((ComparisonList)mc);
	} else if(mc instanceof Comparison) {
	    Comparison c=(Comparison)mc;
	    switch(c.getValueType()) {
	    case LONG:
		return matchesIntegerComparison(c);
	    case DOUBLE:
		return matchesFloatComparison(c);
	    case STRING:
		return matchesStringComparison(c);
	    }
	}

	throw new UnsupportedOperationException("matching "+mc+" not supported");
    }

    private boolean matchesIntegerComparison(Comparison ic) {
	//Because there is no mechanism in place yet to decide which value of the parameter should be used
	// in case of multiple values present, traverse the list in reverse order to get the value of the most recently 
	// extracted parameter first.
	ParameterInstanceRef paraRef=ic.getParameterRef();
	ParameterValue pv = params.getLast(paraRef.getParameter());
	if(pv==null) return false;

	long value=ic.getLongValue();
	if(paraRef.useCalibratedValue()) {
	    switch (ic.getComparisonOperator()) {
	    case EQUALITY:
		return (pv.getEngValue().getUint32Value() == value);
	    case INEQUALITY:
		return (pv.getEngValue().getUint32Value() != value);
	    case LARGERTHAN:
		return (pv.getEngValue().getUint32Value() > value);
	    case LARGEROREQUALTHAN:
		return (pv.getEngValue().getUint32Value() >= value);
	    case SMALLERTHAN:
		return (pv.getEngValue().getUint32Value() < value);
	    case SMALLEROREQUALTHAN:
		return (pv.getEngValue().getUint32Value() <= value);
	    }
	} else {
	    Value pvraw=pv.getRawValue();
	    long upv;
	    if(pvraw.getType()==Value.Type.UINT32) {
		upv=pvraw.getUint32Value() & 0xFFFFFFFFL;
	    } else {
		upv=pvraw.getSint32Value();
	    }

	    switch (ic.getComparisonOperator()) {
	    case EQUALITY:
		return (pv.getRawValue().getUint32Value() == value);
	    case INEQUALITY:
		return (upv != value);
	    case LARGERTHAN:
		return (upv > value);
	    case LARGEROREQUALTHAN:
		return (upv >= value);
	    case SMALLERTHAN:
		return (upv < value);
	    case SMALLEROREQUALTHAN:
		return (upv <= value);
	    }
	}
	return false;
    }

    private boolean matchesFloatComparison(Comparison fc) {
	double value=fc.getDoubleValue();
	ParameterValue pv= params.getLast(fc.getParameterRef().getParameter());
	if(pv==null) return false;

	if(fc.getParameterRef().useCalibratedValue()) {
	    switch (fc.getComparisonOperator()) {
	    case EQUALITY:
		return (pv.getEngValue().getDoubleValue() == value);
	    case INEQUALITY:
		return (pv.getEngValue().getDoubleValue() != value);
	    case LARGERTHAN:
		return (pv.getEngValue().getDoubleValue() > value);
	    case LARGEROREQUALTHAN:
		return (pv.getEngValue().getDoubleValue() >= value);
	    case SMALLERTHAN:
		return (pv.getEngValue().getDoubleValue() < value);
	    case SMALLEROREQUALTHAN:
		return (pv.getEngValue().getDoubleValue() <= value);
	    }
	} else {
	    double pvd=pv.getRawValue().getDoubleValue();
	    switch (fc.getComparisonOperator()) {
	    case EQUALITY:
		return (pvd == value);
	    case INEQUALITY:
		return (pvd != value);
	    case LARGERTHAN:
		return (pvd > value);
	    case LARGEROREQUALTHAN:
		return (pvd >= value);
	    case SMALLERTHAN:
		return (pvd < value);
	    case SMALLEROREQUALTHAN:
		return (pvd <= value);
	    }
	}
	return false;
    }



    private boolean matchesStringComparison(Comparison sc) {//TODO perf
	//Because there is no mechanism in place yet to decide which value of the parameter should be used
	// in case of multiple values present, traverse the list in reverse order to get the value of the most recently 
	// extracted parameter first.

	ParameterInstanceRef paraRef=sc.getParameterRef();
	ParameterValue pv = params.getLast(paraRef.getParameter());
	if(pv==null) return false;

	if(paraRef.useCalibratedValue()) {
	    String value=sc.getStringValue();
	    String s=pv.getEngValue().getStringValue();
	    switch (sc.getComparisonOperator()) {
	    case EQUALITY:
		return (s.compareTo(value) == 0);
	    case INEQUALITY:
		return (s.compareTo(value) != 0);
	    case LARGERTHAN:
		return (s.compareTo(value) > 0);
	    case LARGEROREQUALTHAN:
		return (s.compareTo(value) >= 0);
	    case SMALLERTHAN:
		return (s.compareTo(value) < 0);
	    case SMALLEROREQUALTHAN:
		return (s.compareTo(value) <= 0);
	    }
	} else {
	    byte[] bvalue=sc.getBinaryValue();
	    Comparator<byte[]> comparator=UnsignedBytes.lexicographicalComparator();
	    byte[] pvb=pv.getRawValue().getBinaryValue().toByteArray();
	    switch (sc.getComparisonOperator()) {
	    case EQUALITY:
		return (comparator.compare(pvb, bvalue) == 0);
	    case INEQUALITY:
		return (comparator.compare(pvb, bvalue) != 0);
	    case LARGERTHAN:
		return (comparator.compare(pvb, bvalue) > 0);
	    case LARGEROREQUALTHAN:
		return (comparator.compare(pvb, bvalue) >= 0);
	    case SMALLERTHAN:
		return (comparator.compare(pvb, bvalue) < 0);
	    case SMALLEROREQUALTHAN:
		return (comparator.compare(pvb, bvalue) <= 0);
	    }
	}
	return false;
    }

    public boolean matchesComparisonList(ComparisonList cl) {
	for (Comparison c:cl.getComparisonList()) {
	    if(!matches(c)) return false;
	}
	return true;
    }
}
