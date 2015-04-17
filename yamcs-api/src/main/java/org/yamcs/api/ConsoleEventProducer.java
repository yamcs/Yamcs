package org.yamcs.api;

import java.util.Queue;

import org.yamcs.protobuf.Yamcs.Event;

/**
 * prints all the events to the console (to be used by unit tests)
 * @author nm
 *
 */
public class ConsoleEventProducer extends AbstractEventProducer {
    Queue<Event> mockupQueue;

    @Override
    public void sendEvent(Event event) {
        System.out.println(event);
    }


    @Override
    public void close() {
    }

}
