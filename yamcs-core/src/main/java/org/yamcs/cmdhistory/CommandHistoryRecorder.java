package org.yamcs.cmdhistory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.archive.TcUplinkerAdapter;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.StreamSubscriber;
import org.yamcs.yarch.TableWriter;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;

import com.google.common.util.concurrent.AbstractService;

/**
 * Records command history
 * the key is formed by generation time, origin and sequence number
 * the value is formed by a arbitrary number of attributes
 * 
 * 
 * @author nm
 *
 */
public class CommandHistoryRecorder extends AbstractService {
    final String instance;
    static TupleDefinition eventTpdef; 
    final Logger log;
    final public static String TABLE_NAME="cmdhist"; 
    
    public CommandHistoryRecorder(String instance) {
        this.instance=instance;
        log=LoggerFactory.getLogger(this.getClass().getName()+"["+instance+"]");
    }
    

    @Override
    protected void doStart() {
        YarchDatabase ydb=YarchDatabase.getInstance(instance);

        String keycols=TcUplinkerAdapter.TC_TUPLE_DEFINITION.getStringDefinition1();
        try {
            if(ydb.getTable("cmdhist")==null) {
                String q="create table cmdhist ("+keycols+", PRIMARY KEY(gentime, origin, seqNum)) histogram(cmdName) partition by time(gentime) table_format=compressed";
                ydb.execute(q);
            }
            
            Stream stream=ydb.getStream(YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME);
            if(stream==null) {
                log.error("The stream "+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME+" has not been found");
                notifyFailed(new Exception("The stream "+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME+" has not been found"));
                return;
            }
            ydb.execute("insert_append into "+TABLE_NAME+" select * from "+YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME);
            
            stream=ydb.getStream(YarchCommandHistoryAdapter.DUMP_CMDHIST_STREAM_NAME);
            if(stream==null) {
                log.error("The stream "+YarchCommandHistoryAdapter.DUMP_CMDHIST_STREAM_NAME+" has not been found");
                notifyFailed(new Exception("The stream "+YarchCommandHistoryAdapter.DUMP_CMDHIST_STREAM_NAME+" has not been found"));
                return;
            }
            ydb.execute("insert_append into "+TABLE_NAME+" select * from "+YarchCommandHistoryAdapter.DUMP_CMDHIST_STREAM_NAME);
            
            
        } catch (Exception e) {
            //e.printStackTrace();
            log.error("Failed to setup the recording",e);
            notifyFailed(e);
            return;
        }
        
        notifyStarted();
    }


    @Override
    protected void doStop() {
        YarchDatabase ydb=YarchDatabase.getInstance(instance);
        Stream stream=ydb.getStream(YarchCommandHistoryAdapter.REALTIME_CMDHIST_STREAM_NAME);
        for(StreamSubscriber ss:stream.getSubscribers()) {
            if(ss instanceof TableWriter) {
                if("cmdhist".equals(((TableWriter)ss).getTableDefinition().getName()))
                    stream.removeSubscriber(ss);
            }
        }
        notifyStopped();
    }
}