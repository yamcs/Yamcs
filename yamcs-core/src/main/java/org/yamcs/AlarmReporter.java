package org.yamcs;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.yamcs.api.EventProducer;
import org.yamcs.api.EventProducerFactory;
import org.yamcs.parameter.ParameterConsumer;
import org.yamcs.parameter.ParameterRequestManager;
import org.yamcs.protobuf.Pvalue.MonitoringResult;
import org.yamcs.utils.StringConvertors;
import org.yamcs.xtce.AlarmReportType;
import org.yamcs.xtce.AlarmType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;

import com.google.common.util.concurrent.AbstractService;

/**
 * Generates realtime alarm events automatically, by subscribing to all relevant
 * parameters.
 * <p>
 * <b>Must be declared after {@link YarchChannel}</b>
 */
public class AlarmReporter extends AbstractService implements ParameterConsumer {
    
    private EventProducer eventProducer;
    private Map<Parameter, ActiveAlarm> activeAlarms=new HashMap<Parameter, ActiveAlarm>();
    // Last value of each param (for detecting changes in value)
    private Map<Parameter, ParameterValue> lastValuePerParameter=new HashMap<Parameter, ParameterValue>();
    final String yamcsInstance;
    final String channelName;
    
    public AlarmReporter(String yamcsInstance) {
        this(yamcsInstance, "realtime");
    }
    
    public AlarmReporter(String yamcsInstance, String channelName) {
    	this.yamcsInstance = yamcsInstance;
    	this.channelName = channelName;    			
        eventProducer=EventProducerFactory.getEventProducer(yamcsInstance);
        eventProducer.setSource("AlarmChecker");
    }
    
    @Override
    public void doStart() {
    	 Channel channel = Channel.getInstance(yamcsInstance, channelName);
    	 if(channel==null) {
    		 ConfigurationException e = new ConfigurationException("Cannot find a channel '"+channelName+"' in instance '"+yamcsInstance+"'");
    		 notifyFailed(e);
    		 return;
    	 }
         ParameterRequestManager prm = channel.getParameterRequestManager();
         prm.getAlarmChecker().enableReporting(this);
         
         // Auto-subscribe to parameters with alarms
         Set<Parameter> requiredParameters=new HashSet<Parameter>();
         try {
             XtceDb xtcedb=XtceDbFactory.getInstance(yamcsInstance);
             for (Parameter parameter:xtcedb.getParameters()) {
                 ParameterType ptype=parameter.getParameterType();
                 if(ptype!=null && ptype.hasAlarm()) {
                     requiredParameters.add(parameter);
                     Set<Parameter> dependentParameters = ptype.getDependentParameters();
                     if(dependentParameters!=null) {
                         requiredParameters.addAll(dependentParameters);
                     }
                 }
             }
         } catch(ConfigurationException e) {
        	 notifyFailed(e);
    		 return;
         }
         
         if(!requiredParameters.isEmpty()) {
             List<Parameter> params=new ArrayList<Parameter>(requiredParameters); // Now that we have uniques..
             try {
                 prm.addRequest(params, this);
             } catch(InvalidIdentification e) {
                 throw new RuntimeException("Could not register dependencies for alarms", e);
             }
         }
        notifyStarted();
    }
    
    @Override
    public void doStop() {
        notifyStopped();
    }
    
    @Override
    public void updateItems(int subscriptionId, ArrayList<ParameterValue> items) {
        // Nothing. The real business of sending events, happens while checking the alarms
        // because that's where we have easy access to the XTCE definition of the active
        // alarm. The PRM is only used to signal the parameter subscriptions.
    }

    /**
     * Sends an event if an alarm condition for the active context has been
     * triggered <tt>minViolations</tt> times. This configuration does not
     * affect events for parameters that go back to normal, or that change
     * severity levels while the alarm is already active.
     */
    public void reportNumericParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
        boolean sendUpdateEvent=false;
        
        if(alarmType.getAlarmReportType()==AlarmReportType.ON_VALUE_CHANGE) {
            ParameterValue oldPv=lastValuePerParameter.get(pv.def);
            if(oldPv!=null && hasChanged(oldPv, pv)) {
                sendUpdateEvent=true;
            }
            lastValuePerParameter.put(pv.def, pv);
        }
        
        if(pv.getMonitoringResult()==MonitoringResult.IN_LIMITS) {
            if(activeAlarms.containsKey(pv.getParameter())) {
                eventProducer.sendInfo("NORMAL", "Parameter "+pv.getParameter().getQualifiedName()+" is back to normal");
                activeAlarms.remove(pv.getParameter());
            }
        } else { // out of limits
            MonitoringResult previousMonitoringResult=null;
            ActiveAlarm activeAlarm=activeAlarms.get(pv.getParameter());
            if(activeAlarm==null || activeAlarm.alarmType!=alarmType) {
                activeAlarm=new ActiveAlarm(alarmType, pv.getMonitoringResult());
            } else {
                previousMonitoringResult=activeAlarm.monitoringResult;
                activeAlarm.monitoringResult=pv.getMonitoringResult();
                activeAlarm.violations++;
            }
            
            if(activeAlarm.violations==minViolations || (activeAlarm.violations>minViolations && previousMonitoringResult!=activeAlarm.monitoringResult)) {
                sendUpdateEvent=true;
            }
            
            activeAlarms.put(pv.getParameter(), activeAlarm);
        }
        
        if(sendUpdateEvent) {
            sendValueChangeEvent(pv);
        }
    }
    
    public void reportEnumeratedParameterEvent(ParameterValue pv, AlarmType alarmType, int minViolations) {
        boolean sendUpdateEvent=false;
        
        if(alarmType.getAlarmReportType()==AlarmReportType.ON_VALUE_CHANGE) {
            ParameterValue oldPv=lastValuePerParameter.get(pv.def);
            if(oldPv!=null && hasChanged(oldPv, pv)) {
                sendUpdateEvent=true;
            }
            lastValuePerParameter.put(pv.def, pv);
        }
        
        if(pv.getMonitoringResult()==MonitoringResult.IN_LIMITS) {
            if(activeAlarms.containsKey(pv.getParameter())) {
                eventProducer.sendInfo("NORMAL", "Parameter "+pv.getParameter().getQualifiedName()+" is back to a normal state ("+pv.getEngValue().getStringValue()+")");
                activeAlarms.remove(pv.getParameter());
            }
        } else { // out of limits
            MonitoringResult previousMonitoringResult=null;
            ActiveAlarm activeAlarm=activeAlarms.get(pv.getParameter());
            if(activeAlarm==null || activeAlarm.alarmType!=alarmType) {
                activeAlarm=new ActiveAlarm(alarmType, pv.getMonitoringResult());
            } else {
                previousMonitoringResult=activeAlarm.monitoringResult;
                activeAlarm.monitoringResult=pv.getMonitoringResult();
                activeAlarm.violations++;
            }
            
            if(activeAlarm.violations==minViolations || (activeAlarm.violations>minViolations&& previousMonitoringResult!=activeAlarm.monitoringResult)) {
                sendUpdateEvent=true;
            }
            
            activeAlarms.put(pv.getParameter(), activeAlarm);
        }
        
        if(sendUpdateEvent) {
            sendStateChangeEvent(pv);
        }
    }
    
    private void sendValueChangeEvent(ParameterValue pv) {
        switch(pv.getMonitoringResult()) {
        case WATCH_LOW:
        case WARNING_LOW:
            eventProducer.sendWarning(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too low");
            break;
        case WATCH_HIGH:
        case WARNING_HIGH:
            eventProducer.sendWarning(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too high");
            break;
        case DISTRESS_LOW:
        case CRITICAL_LOW:
        case SEVERE_LOW:
            eventProducer.sendError(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too low");
            break;
        case DISTRESS_HIGH:
        case CRITICAL_HIGH:
        case SEVERE_HIGH:
            eventProducer.sendError(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" is too high");
            break;
        case IN_LIMITS:
            eventProducer.sendInfo(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" has changed to value "+StringConvertors.toString(pv.getEngValue(), false));
            break;
        default:
            throw new IllegalStateException("Unexpected monitoring result: "+pv.getMonitoringResult());
        }
    }
    
    private void sendStateChangeEvent(ParameterValue pv) {
        switch(pv.getMonitoringResult()) {
        case WATCH:
        case WARNING:
            eventProducer.sendWarning(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" transitioned to state "+pv.getEngValue().getStringValue());
            break;
        case DISTRESS:
        case CRITICAL:
        case SEVERE:
            eventProducer.sendError(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" transitioned to state "+pv.getEngValue().getStringValue());
            break;
        case IN_LIMITS:
            eventProducer.sendInfo(pv.getMonitoringResult().toString(), "Parameter "+pv.getParameter().getQualifiedName()+" transitioned to state "+pv.getEngValue().getStringValue());
            break;
        default:
            throw new IllegalStateException("Unexpected monitoring result: "+pv.getMonitoringResult());
        }
    }
    
    private boolean hasChanged(ParameterValue pvOld, ParameterValue pvNew) {
        // Crude string value comparison.
        return !StringConvertors.toString(pvOld.getEngValue(), false)
                .equals(StringConvertors.toString(pvNew.getEngValue(), false));
    }
    
    private static class ActiveAlarm {
        MonitoringResult monitoringResult;
        AlarmType alarmType;
        int violations=1;
        ParameterValue lastValue;
        
        ActiveAlarm(AlarmType alarmType, MonitoringResult monitoringResult) {
            this.alarmType=alarmType;
            this.monitoringResult=monitoringResult;
        }
    }
}
