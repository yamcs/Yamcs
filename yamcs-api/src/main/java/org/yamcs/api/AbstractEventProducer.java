package org.yamcs.api;

import java.io.InputStream;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
import org.yamcs.protobuf.Yamcs.Event;
import org.yamcs.protobuf.Yamcs.Event.EventSeverity;
import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;

/**
 * Default implementation of an EventProducer that provides shortcut methods for
 * sending message of different severity types. By default, repeated message are
 * detected and reduced, resulting in pseudo events with a message like 'last event
 * repeated X times'. This behaviour can be turned off.
 */
public abstract class AbstractEventProducer implements EventProducer {
    private static final String CONF_REPEATED_EVENT_REDUCTION = "repeatedEventReduction";
    
    SimpleString address;
    String source;
    AtomicInteger seqNo = new AtomicInteger();

    private boolean repeatedEventReduction; // Wether to check for message repetitions
    private Event originalEvent; // Original evt of a series of repeated events
    private Event lastRepeat; // Last evt of a series of repeated events
    private int repeatCounter = 0;
    
    // Flushes the Event Buffer about each minute
    private Timer flusher;
    
    public AbstractEventProducer() {
        String configFile = "/event-producer.yaml";
        InputStream is=EventProducerFactory.class.getResourceAsStream(configFile);
        
        Object o = new Yaml().load(is);
        if(!(o instanceof Map<?,?>)) throw new RuntimeException("event-producer.yaml does not contain a map but a "+o.getClass());

        @SuppressWarnings("unchecked")
        Map<String,Object> m = (Map<String, Object>) o;

        if (m.containsKey(CONF_REPEATED_EVENT_REDUCTION)) {
            repeatedEventReduction = (Boolean) m.get(CONF_REPEATED_EVENT_REDUCTION);
        } else {
            repeatedEventReduction = true;
        }
        if (repeatedEventReduction) setRepeatedEventReduction(true);
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#setSource(java.lang.String)
     */
    @Override
    public void setSource(String source) {
        this.source=source;
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#setSeqNo(int)
     */
    @Override
    public void setSeqNo(int sn) {
        this.seqNo.set(sn);
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendError(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendError(String type, String msg) {
        sendMessage(EventSeverity.ERROR, type, msg);
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendWarning(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendWarning(String type, String msg) {
        sendMessage(EventSeverity.WARNING, type, msg);
    }
    
    /* (non-Javadoc)
     * @see org.yamcs.api.EventProducer#sendInfo(java.lang.String, java.lang.String)
     */
    @Override
    public synchronized void sendInfo(String type, String msg) {
        sendMessage(EventSeverity.INFO, type, msg);
    }
    
    private void sendMessage(EventSeverity severity, String type, String msg) {
        try {
            Event e = newEvent().setSeverity(severity).setType(type).setMessage(msg).build();
            if (!repeatedEventReduction) {
                sendEvent(e);
            } else {
                if (originalEvent == null) {
                    sendEvent(e);
                    originalEvent = e;
                } else if (isRepeat(e)) {
                    if (flusher == null) { // Prevent buffering repeated events forever
                        flusher = new Timer(true);
                        flusher.scheduleAtFixedRate(new TimerTask() {
                            @Override
                            public void run() {
                                flushEventBuffer(false);
                            }
                        }, 60000, 60000);
                    }
                    lastRepeat = e;
                    repeatCounter++;
                } else { // No more repeats
                    if (flusher != null) {
                        flusher.cancel();
                        flusher = null;
                    }
                    flushEventBuffer(true);
                    sendEvent(e);
                    originalEvent = e;
                    lastRepeat = null;
                }
            }
        } catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    /** 
     * By default event repetitions are checked for possible reduction. Disable if
     * 'realtime' events are required.
     */
    public synchronized void setRepeatedEventReduction(boolean repeatedEventReduction) {
        this.repeatedEventReduction = repeatedEventReduction;
        if (!repeatedEventReduction) {
            if (flusher != null) {
                flusher.cancel();
                flusher = null;
            }
            flushEventBuffer(true);
        }
    }
      
    protected synchronized void flushEventBuffer(boolean startNewSequence) {
        try {
            if (repeatCounter > 1) {
                sendEvent(Event.newBuilder(lastRepeat)
                        .setMessage("last event repeated "+repeatCounter+" times")
                        .build());
            } else if (repeatCounter == 1) {
                sendEvent(lastRepeat);
                lastRepeat = null;
            }
            if (startNewSequence) originalEvent = null;
            repeatCounter = 0;
        } catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Checks whether the specified Event is a repeat of the previous Event.
     */
    private boolean isRepeat(Event e) {
        if (originalEvent == e) return true;
        return originalEvent.getMessage().equals(e.getMessage())
                && originalEvent.getSeverity().equals(e.getSeverity())
                && originalEvent.getSource().equals(e.getSource())
                && originalEvent.getType().equals(e.getType());
    }

    private Event.Builder newEvent() {
        long t=TimeEncoding.currentInstant();
        return Event.newBuilder().setSource(source).
            setSeqNumber(seqNo.getAndIncrement()).setGenerationTime(t).
            setReceptionTime(t);
    }
}
