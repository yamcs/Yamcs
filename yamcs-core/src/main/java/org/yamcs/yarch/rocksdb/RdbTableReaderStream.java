package org.yamcs.yarch.rocksdb;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksIterator;
import org.yamcs.yarch.AbstractTableReaderStream;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.ColumnSerializer;
import org.yamcs.yarch.DbReaderStream;
import org.yamcs.yarch.IndexFilter;
import org.yamcs.yarch.Partition;
import org.yamcs.yarch.PartitioningSpec;
import org.yamcs.yarch.RawTuple;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.YarchDatabase;

public class RdbTableReaderStream extends AbstractTableReaderStream implements Runnable, DbReaderStream {
	static AtomicInteger count=new AtomicInteger(0);
    final PartitioningSpec partitioningSpec;
    final RdbPartitionManager partitionManager;
    final TableDefinition tableDefinition;
    
	protected RdbTableReaderStream(YarchDatabase ydb, TableDefinition tblDef, RdbPartitionManager partitionManager) {
		 super(ydb, tblDef, partitionManager);
		this.tableDefinition=tblDef;
        partitioningSpec=tblDef.getPartitioningSpec();
        this.partitionManager = partitionManager;
	}


    @Override 
    public void start() {
        (new Thread(this, "TcTableReader["+getName()+"]")).start();
    }

   
    /**
     * reads a file, sending data only that conform with the start and end filters. 
     * returns true if the stop condition is met
     * 
     * All the partitions are from the same time interval and thus from one single RocksDB database
     * 
     */
    protected boolean runPartitions(Collection<Partition> partitions, IndexFilter range) throws IOException {
        byte[] rangeStart=null;
        boolean strictStart=false;
        byte[] rangeEnd=null;
        boolean strictEnd=false;
        
        if(range!=null) {
            ColumnDefinition cd=tableDefinition.getKeyDefinition().getColumn(0);
            ColumnSerializer cs=tableDefinition.getColumnSerializer(cd.getName());
            if(range.keyStart!=null) {
                strictStart=range.strictStart;
                rangeStart=cs.getByteArray(range.keyStart);
            }
            if(range.keyEnd!=null) {
                strictEnd=range.strictEnd;
                rangeEnd=cs.getByteArray(range.keyEnd);
            }
        }
        
        
        log.debug("running partitions "+partitions);
        PriorityQueue<RdbRawTuple> orderedQueue=new PriorityQueue<RdbRawTuple>();
        try {
        	RDBFactory rdbf=RDBFactory.getInstance(ydb.getName());
        	RdbPartition p1 = (RdbPartition) partitions.iterator().next();
        	String dbDir = p1.dir;
        	log.debug("opening database "+ dbDir);
        	YRDB rdb = rdbf.getRdb(tableDefinition.getDataDir()+"/"+p1.dir, new ColumnValueSerializer(tableDefinition.getPartitioningSpec().valueColumnType), false);
        	List<ColumnFamilyHandle> cfhList = new ArrayList<ColumnFamilyHandle>();
        	for(Partition p: partitions) {
        		ColumnFamilyHandle cfh = rdb.getColumnFamilyHandle(p.getValue());
        		if(cfh!=null) {
        			cfhList.add(cfh);
        		}
        	}
        	int i=0;
            //create a cursor for all partitions
        	List<RocksIterator> iteratorList = rdb.newIterators(cfhList);
        	
            for(RocksIterator it:iteratorList) {
                boolean found=true;
                if(rangeStart!=null) {
                	it.seek(rangeStart);
                	if(it.isValid()) {                	
                        if((strictStart)&&(compare(rangeStart, it.key())==0)) {
                            //if filter condition is ">" we skip the first record if it is equal to the key
                        	it.next();
                            found=it.isValid();
                        }
                    } else {
                        found=false;
                    }
                    if(!found) log.debug("no record corresponding to the StartFilter");
                } else {
                	it.seekToFirst();
                    if(!it.isValid()) {
                        log.debug("tcb contains no record");
                        found=false;
                    }
                }
                if(!found) {
                	it.dispose();                                        
                } else {
                    orderedQueue.add(new RdbRawTuple(it.key(), it.value(), it, i++));
                }
            }
            log.debug("got one tuple from each partition, starting the business");

            //now continue publishing the first element from the priority queue till it becomes empty
            while((!quit) && orderedQueue.size()>0){
                RdbRawTuple rt=orderedQueue.poll();
                if(!emitIfNotPastStop(rt, rangeEnd, strictEnd)) {
                    return true;
                }
                rt.iterator.next();
                if(rt.iterator.isValid()) {
                   rt.key=rt.iterator.key();
                   rt.value=rt.iterator.value();
                   orderedQueue.add(rt);
                } else {
                    log.debug(rt.iterator+" finished");
                    rt.iterator.dispose();                    
                }
            }
            
            rdbf.dispose(rdb);
            return false;
        } catch (Exception e){
           e.printStackTrace();
           return false;
        } finally {
            for(RdbRawTuple rt:orderedQueue) {
                rt.iterator.dispose();                
            }
        }
    }
    
    class RdbRawTuple extends RawTuple {       
		int index;//used for sorting tuples with equals keys
        RocksIterator iterator;
        
        public RdbRawTuple(byte[] key, byte[] value, RocksIterator iterator, int index) {
        	super(key,value, index);
            this.iterator = iterator;
            this.index=index;
        }
    }    
}
