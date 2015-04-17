package org.yamcs.yarch.streamsql;


import java.io.IOException;
import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.yarch.AbstractStream;
import org.yamcs.yarch.ColumnDefinition;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.HistogramReaderStream;
import org.yamcs.yarch.Stream;
import org.yamcs.yarch.TableDefinition;
import org.yamcs.yarch.TupleDefinition;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchException;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.NoneSpecifiedException;
import org.yamcs.yarch.streamsql.ResourceNotFoundException;
import org.yamcs.yarch.streamsql.StreamExpression;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlException.ErrCode;
import org.yamcs.yarch.streamsql.TupleSourceExpression;
/**
 * A source of tuples. Can be:
 *  - a reference to an existing stream objectName
 *  - a reference to a table objectName
 *  - a stream expression
 * @author nm
 *
 */
class TupleSourceExpression {
    String objectName=null;
    StreamExpression streamExpression=null;
    BigDecimal histogramMergeTime=null;
    
    //when histoColumn is set, the objectName must be a table having histograms on that column
    String histoColumn;

    //after binding
    TupleDefinition definition;

    static Logger log=LoggerFactory.getLogger(TupleSourceExpression.class.getName());

    public TupleSourceExpression(String name) {
        this.objectName=name;
    }

    public TupleSourceExpression(StreamExpression expr) {
        this.streamExpression=expr;
    }

    public void setHistogramColumn(String histoColumn) {
        this.histoColumn=histoColumn;
    }

    void bind(ExecutionContext c) throws StreamSqlException {
        if(streamExpression!=null) {
            streamExpression.bind(c);
            definition=streamExpression.getOutputDefinition();
        } else {
            YarchDatabase dict=YarchDatabase.getInstance(c.getDbName());
            TableDefinition tbl=dict.getTable(objectName);
            if(tbl!=null) {
                if(histoColumn==null) {
                    definition=tbl.getTupleDefinition();
                } else {
                    if(!tbl.hasHistogram()) 
                        throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN, "No histogram configured for table "+tbl.getName());
                    if(!tbl.getHistogramColumns().contains(histoColumn)) 
                        throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN, "Histogram is not configured for column "+histoColumn);
                    
                    definition=new TupleDefinition();
                    definition.addColumn(tbl.getColumnDefinition(histoColumn));
                    definition.addColumn(new ColumnDefinition("first", DataType.TIMESTAMP));
                    definition.addColumn(new ColumnDefinition("last", DataType.TIMESTAMP));
                    definition.addColumn(new ColumnDefinition("num", DataType.INT));
                }
            } else {
                Stream stream=dict.getStream(objectName);
                if(stream==null) throw new ResourceNotFoundException(objectName);
                if(histoColumn!=null) throw new StreamSqlException(ErrCode.INVALID_HISTOGRAM_COLUMN, "Cannot specify histogram option when selecting from a stream");
                definition=stream.getDefinition();
            }
        }
    }
    public void setHistogramMergeTime(BigDecimal mergeTime) {
        histogramMergeTime=mergeTime;
        
    }
    
    AbstractStream execute(ExecutionContext c) throws StreamSqlException {
        AbstractStream stream;
        if(streamExpression!=null) {        	
            stream=streamExpression.execute(c);
        } else if (objectName!=null) {
            YarchDatabase ydb=YarchDatabase.getInstance(c.getDbName());

            TableDefinition tbl=ydb.getTable(objectName);
            if(tbl!=null) {
                if(histoColumn==null) {
                    stream = ydb.getStorageEngine(tbl).newTableReaderStream(tbl);
                } else {
                    HistogramReaderStream histoStream;
					try {
						histoStream = new HistogramReaderStream(ydb, tbl, histoColumn, definition);
					} catch (YarchException e) {
						throw new StreamSqlException(ErrCode.ERROR, e.getMessage());
					}
                    if(histogramMergeTime!=null) {
                        histoStream.setMergeTime(histogramMergeTime.longValue());
                    }
                    stream=histoStream;
                }
            } else {
                stream=ydb.getStream(objectName);
                if(stream==null) throw new ResourceNotFoundException(objectName);
            }
        } else {
            throw new NoneSpecifiedException();
        }
        
        
        return stream;
    }

    TupleDefinition getDefinition() {
        return definition;
    }

   

}
