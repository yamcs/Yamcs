package org.yamcs.derivedvalues;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.Channel;
import org.yamcs.ConfigurationException;
import org.yamcs.DVParameterConsumer;
import org.yamcs.InvalidIdentification;
import org.yamcs.InvalidRequestIdentification;
import org.yamcs.ParameterValue;
import org.yamcs.YConfiguration;
import org.yamcs.parameter.ParameterProvider;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.parameter.ParameterRequestManagerIf;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.NamedDescriptionIndex;
import org.yamcs.xtce.Parameter;

import com.google.common.util.concurrent.AbstractService;

/**
 * 
 * Takes care of derived values (i.e. algorithms).
 * 
 * Has been implemented before we had the XTCE algorithms. Can be used to implement derived values in Java. The values are not necessarily registered in XTCE (like the algorithms are)
 *
 * @author mache
 *
 */
public class DerivedValuesManager extends AbstractService implements ParameterProvider, DVParameterConsumer {
    Logger log=LoggerFactory.getLogger(this.getClass().getName());

    //the id used for suscribing to the parameterManager
    int subscriptionId;
    List<DerivedValue> derivedValues=new ArrayList<DerivedValue>();
    NamedDescriptionIndex<Parameter> dvIndex=new NamedDescriptionIndex<Parameter>();

    ArrayList<DerivedValue> requestedValues=new ArrayList<DerivedValue>();
    ParameterRequestManager parameterRequestManager;

    public DerivedValuesManager(String yamcsInstance) {
	//do nothing here, all the work is done in init
    }


    @SuppressWarnings("unchecked")
    @Override
    public void init(Channel channel) throws ConfigurationException {
	this.parameterRequestManager = channel.getParameterRequestManager();

	try {
	    subscriptionId=parameterRequestManager.addRequest(new ArrayList<Parameter>(0), this);
	} catch (InvalidIdentification e) {
	    log.error("InvalidIdentification while subscribing to the parameterRequestManager with an empty subscription list", e);
	}

	addAll(new DerivedValues_XTCE(channel.getXtceDb()).getDerivedValues());
	YConfiguration yconf=YConfiguration.getConfiguration("yamcs."+channel.getInstance());
	String mdbconfig=yconf.getString("mdb");
	YConfiguration conf=YConfiguration.getConfiguration("mdb");
	if(conf.containsKey(mdbconfig+"_derivedValuesProviders")) {
	    List<String> providers=conf.getList(mdbconfig+"_derivedValuesProviders");
	    for(String p:providers) {
		Class<DerivedValuesProvider> c;
		try {
		    c = (Class<DerivedValuesProvider>) Class.forName(p);
		    DerivedValuesProvider provider=c.newInstance();
		    addAll(provider.getDerivedValues());
		} catch (ClassNotFoundException e) {
		    throw new ConfigurationException("Cannot load derived value provider from class "+p, e);
		} catch (InstantiationException e) {
		    e.printStackTrace();
		    throw new ConfigurationException("Cannot load derived value provider from class "+p, e);
		} catch (IllegalAccessException e) {
		    throw new ConfigurationException("Cannot load derived value provider from class "+p, e);
		}
	    }
	} else {
	    log.info("No derived value provider defined in MDB.yaml");
	}

    }

    public void addAll(Collection<DerivedValue> dvalues) {
	derivedValues.addAll(dvalues);
	for(DerivedValue dv:dvalues) {
	    dvIndex.add(dv.def);
	}
    }

    public int getSubscriptionId() {
	return subscriptionId;
    }

    @Override
    public void startProviding(Parameter paramDef) {
	for (DerivedValue dv:derivedValues){
	    if(dv.def==paramDef) {
		requestedValues.add(dv);
		try {
		    parameterRequestManager.addItemsToRequest(subscriptionId, Arrays.asList(dv.getArgumentIds()));
		} catch (InvalidIdentification e) {
		    log.error("InvalidIdentification caught when subscribing to the items required for the derived value "+dv.def+"\n\t The invalid items are:"+e.invalidParameters, e);
		} catch (InvalidRequestIdentification e) {
		    log.error("InvalidRequestIdentification caught when subscribing to the items required for the derived value "+dv.def, e);
		}
		return;
	    }
	}
    }

    @Override
    public void startProvidingAll() {
	// TODO Auto-generated method stub

    }
    //TODO 2.0 unsubscribe from the requested values
    @Override
    public void stopProviding(Parameter paramDef) {
	for (Iterator<DerivedValue> it=requestedValues.iterator();it.hasNext(); ){
	    DerivedValue dv=it.next();
	    if(dv.def==paramDef) {
		it.remove();
		return;
	    }
	}
    }

    @Override
    public boolean canProvide(NamedObjectId itemId) {
	try {
	    getParameter(itemId) ;
	} catch (InvalidIdentification e) {
	    return false;
	}
	return true;
    }
    
    @Override
    public boolean canProvide(Parameter param) {
	return dvIndex.get(param.getQualifiedName())!=null;
    }


    
    @Override
    public Parameter getParameter(NamedObjectId paraId) throws InvalidIdentification {
	Parameter p;
	if(paraId.hasNamespace()) {
	    p=dvIndex.get(paraId.getNamespace(), paraId.getName());
	} else {
	    p=dvIndex.get(paraId.getName());
	}
	if(p!=null) {
	    return p;
	} else {
	    throw new InvalidIdentification();
	}
    }

    @Override
    public ArrayList<ParameterValue> updateParameters(int subcriptionid, ArrayList<ParameterValue> items) {
	HashSet<DerivedValue> needUpdate=new HashSet<DerivedValue>();
	for(Iterator<ParameterValue> it=items.iterator();it.hasNext();) {
	    ParameterValue pvwi=it.next();
	    for(Iterator<DerivedValue> it1=requestedValues.iterator();it1.hasNext();) {
		DerivedValue dv=it1.next();
		for(int i=0;i<dv.getArgumentIds().length;i++) {
		    if(dv.getArgumentIds()[i]==pvwi.getParameter()) {
			dv.args[i]=pvwi;
			needUpdate.add(dv);
		    }
		}
	    }
	}
	long acqTime=TimeEncoding.currentInstant();

	ArrayList<ParameterValue> r=new ArrayList<ParameterValue>();
	for(DerivedValue dv:needUpdate) {
	    try{
		dv.setAcquisitionTime(acqTime);
		dv.updateValue();
		if(dv.isUpdated()) {
		    r.add(dv);
		    dv.setGenerationTime(items.get(0).getGenerationTime());
		}
	    } catch (Exception e) {
		log.warn("got exception when updating derived value "+dv.def+": "+Arrays.toString(e.getStackTrace()));
	    }
	}
	return r;
    }

    @Override
    public void setParameterListener(ParameterRequestManagerIf parameterRequestManager) {
	// do nothing,  everything is done in the updateDerivedValues method
    }

    @Override
    protected void doStart() {
	notifyStarted();
    }

    @Override
    protected void doStop() {
	notifyStopped();
    }
}
