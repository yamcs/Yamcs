package org.yamcs.yarch;

import java.io.FileNotFoundException;


public abstract class TableWriter implements StreamSubscriber {         
    public enum InsertMode { //UPSERT and UPSERT_APPEND are not yet implemented TODO
        INSERT, //insert rows whose key do not exist, ignore the others
    //    UPSERT, //insert rows as they come, overwriting old values if the key already exist
        INSERT_APPEND, //like INSERT but if the row already exist, append to it all the columns that are not already there
    //    UPSERT_APPEND, //like INSERT_APPEND but if the row already exists, add all the columns from the new row, overwriting old values if necessary
    }
    
    final protected TableDefinition tableDefinition;
    final protected InsertMode mode;
    final protected YarchDatabase ydb;
    
    public TableWriter(YarchDatabase ydb, TableDefinition tableDefinition, InsertMode mode) throws FileNotFoundException {
        this.tableDefinition=tableDefinition;
        this.mode=mode;
        this.ydb=ydb;
    }
    
    public TableDefinition getTableDefinition() {
        return tableDefinition;
    }

}