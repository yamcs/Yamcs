package org.yamcs.yarch;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.yarch.management.ManagementService;
import org.yamcs.yarch.rocksdb.RdbStorageEngine;
import org.yamcs.yarch.streamsql.ExecutionContext;
import org.yamcs.yarch.streamsql.ParseException;
import org.yamcs.yarch.streamsql.StreamSqlException;
import org.yamcs.yarch.streamsql.StreamSqlParser;
import org.yamcs.yarch.streamsql.StreamSqlResult;
import org.yamcs.yarch.streamsql.StreamSqlStatement;
import org.yamcs.yarch.streamsql.TokenMgrError;
import org.yamcs.yarch.tokyocabinet.TCBFactory;
import org.yamcs.yarch.tokyocabinet.TcStorageEngine;
import org.yaml.snakeyaml.Yaml;



/**
 * Synchronization policy: to avoid problems with stream disappearing when clients connect to them, all
 * the creation/closing/subscription to streams/tables shall be done while acquiring a lock on the YarchDatabase
 * object. This is done in the StreamSqlStatement.java
 * 
 * Delivery of tuples does not require locking, this means subscription can change while delivering 
 *  (for that a concurrent list is used in Stream.java)
 * 
 * @author nm
 *
 */
public class YarchDatabase {

    Map<String,TableDefinition> tables;
    transient Map<String,AbstractStream> streams;
    static Logger log=LoggerFactory.getLogger(YarchDatabase.class.getName());
    static YConfiguration config;
    private static String home;
    private TCBFactory tcbFactory=TCBFactory.getInstance();

    static Map<String, StorageEngine> storageEngines=new HashMap<String, StorageEngine>();
    public static String TC_ENGINE_NAME="tokyocabinet";	
    public static String RDB_ENGINE_NAME="rocksdb";

    public static String DEFAULT_STORAGE_ENGINE=TC_ENGINE_NAME;

    static {
	try {
	    config=YConfiguration.getConfiguration("yamcs");
	    home=config.getString("dataDir");

	} catch (ConfigurationException e) {
	    throw new RuntimeException(e);
	}
    } 

    final ManagementService managementService;


    static Map<String,YarchDatabase> databases=new HashMap<String,YarchDatabase>();
    private String dbname;

    @SuppressWarnings("unchecked")
    private YarchDatabase(String dbname) throws YarchException {
	this.dbname=dbname;
	managementService=ManagementService.getInstance();
	tables=new HashMap<String,TableDefinition>();
	streams=new HashMap<String,AbstractStream>();


	List<String> se;
	if(config.containsKey("storageEngines")) {
	    se = config.getList("storageEngines");
	} else {
	    se = Arrays.asList(TC_ENGINE_NAME);
	}
	if(se!=null) {
	    for(String s:se) {
		if(TC_ENGINE_NAME.equalsIgnoreCase(s)) {
		    storageEngines.put(TC_ENGINE_NAME, new TcStorageEngine(this));					
		} else if(RDB_ENGINE_NAME.equalsIgnoreCase(s)) {
		    storageEngines.put(RDB_ENGINE_NAME, new RdbStorageEngine(this));			
		}					
	    }
	}
	loadTables();
    }

    static synchronized public YarchDatabase getInstance(String dbname) {
	YarchDatabase instance=databases.get(dbname);
	if(instance==null) {
	    try {
		instance=new YarchDatabase(dbname);
	    } catch (YarchException e) {
		throw new RuntimeException("Cannot create database '"+dbname+"'", e);
	    }
	    databases.put(dbname, instance);
	}
	return instance;
    }

    static public boolean hasInstance(String dbname) {
	return databases.containsKey(dbname);
    }
    /**
     * 
     * @return the instance name
     */
    public String getName() {
	return dbname;
    }

    /**
     * loads all the .def files from the disk. The ascii def file is structed as follows
     * col1 type1, col2 type2, col3 type3     <- definition of the columns
     * col1, col2                             <- definition of the primary key
     * @throws YarchException 
     */
    void loadTables() throws YarchException {
	File dir=new File(getRoot());
	if(dir.exists() ) {
	    File[] dirFiles=dir.listFiles();
	    if(dirFiles==null) return; //no tables found
	    for(File f:dirFiles) {
		String fn=f.getName();
		if(fn.endsWith(".def")) {
		    try {
			TableDefinition tblDef=deserializeTableDefinition(f);
			StorageEngine storageEngine = getStorageEngine(tblDef);
			if(storageEngine==null) {
			    throw new YarchException("Do not have a storage engine '"+tblDef.getStorageEngineName()+"'. Check storageEngines key in yamcs.yaml");
			}
		    
			getStorageEngine(tblDef).loadTable(tblDef);
			managementService.registerTable(dbname, tblDef);
			//System.out.println("loaded table: "+tblDef);
			tables.put(tblDef.getName(), tblDef);
			log.debug("loaded table definition "+tblDef.getName()+" from "+f);
		    } catch (IOException e) {
			log.warn("Got exception when reading the table definition from "+f+": ", e);
			throw new YarchException("Got exception when reading the table definition from "+f+": ", e);
		    } catch (ClassNotFoundException e) {
			log.warn("Got exception when reading the table definition from "+f+": ", e);
			throw new YarchException("Got exception when reading the table definition from "+f+": ", e);
		    } 
		}
	    }
	} else {
	    log.info("Creating directory for db "+dbname+": "+dir.getAbsolutePath());
	    if(!dir.mkdirs()) {
		log.error("Cannot create directory: "+dir);
	    }
	}
    }

    TableDefinition deserializeTableDefinition(File f) throws FileNotFoundException, IOException, ClassNotFoundException {	    
	String fn=f.getName();
	String tblName=fn.substring(0,fn.length()-4);
	Yaml yaml = new Yaml(new TableDefinitionConstructor());
	FileInputStream fis=new FileInputStream(f);
	TableDefinition tblDef=(TableDefinition) yaml.load(fis); 
	fis.close();
	tblDef.setName(tblName);
	tblDef.setDb(this);
	if(!tblDef.hasCustomDataDir()) tblDef.setDataDir(getRoot());

	log.debug("loaded table definition "+tblName+" from "+fn);
	return tblDef;
    }

    /**
     * serializes to disk to the rootDir/name.def
     * @param def
     */
    void serializeTableDefinition(TableDefinition td) {
	String fn=getRoot()+"/"+td.getName()+".def";
	try {
	    Yaml yaml = new Yaml(new TableDefinitionRepresenter());
	    FileOutputStream fos=new FileOutputStream(fn);
	    Writer w=new BufferedWriter(new OutputStreamWriter(fos));
	    yaml.dump(td, w);
	    w.flush();
	    fos.getFD().sync();
	    w.close();
	} catch (IOException e) {
	    log.error("Got exception when writing table definition to "+fn+": ", e);
	    e.printStackTrace();
	}
    }

    /**
     *  add a table to the dictionary
     *  throws exception if a table or a stream with the same name already exist
     *  
     */
    public void createTable(TableDefinition def) throws YarchException {
	if(tables.containsKey(def.getName())) throw new YarchException("A table named '"+def.getName()+"' already exists");
	if(streams.containsKey(def.getName())) throw new YarchException("A stream named '"+def.getName()+"' already exists");

	StorageEngine se = storageEngines.get(def.getStorageEngineName());
	if(se==null) throw new YarchException("Invalid storage engine '"+def.getStorageEngineName()+" specified. Valid names are: "+storageEngines.keySet());
	se.createTable(def);

	tables.put(def.getName(),def);
	def.setDb(this);
	serializeTableDefinition(def);
	managementService.registerTable(dbname, def);		
    }



    /**
     * Adds a stream to the dictionary making it "official"
     * @param s
     * @throws YarchException
     */
    public void addStream(AbstractStream stream) throws YarchException {
	if(tables.containsKey(stream.getName())) throw new YarchException("A table named '"+stream.getName()+"' already exists");
	if(streams.containsKey(stream.getName())) throw new YarchException("A stream named '"+stream.getName()+"' already exists");
	streams.put(stream.getName(), stream);
	managementService.registerStream(dbname, stream);
    }

    public TableDefinition getTable(String name) {
	return tables.get(name);
    }

    public boolean streamOrTableExists(String name) {
	if(streams.containsKey(name)) return true;
	if(tables.containsKey(name)) return true;
	return false;
    }

    public AbstractStream getStream(String name) {
	return streams.get(name);
    }



    public void dropTable(String tblName) throws YarchException {
	log.info("dropping table "+tblName);
	TableDefinition tbl=tables.remove(tblName);
	if(tbl==null) {
	    throw new YarchException("There is no table named '"+tblName+"'");
	}
	managementService.unregisterTable(dbname, tblName);
	if(tbl.hasPartitioning()) {
	    getStorageEngine(tbl).dropTable(tbl);
	} else {
	    String file=tbl.getDataDir()+"/"+tblName+".tcb";
	    File f=new File(file);
	    if(f.exists() && (!f.delete())) throw new YarchException("Cannot remove "+file);
	    tcbFactory.delete(file);
	}
	File f=new File(getRoot()+"/"+tblName+".def");
	if(!f.delete()) {
	    throw new YarchException("Cannot remove "+f);
	}
    }


    public synchronized void removeStream(String name) {
	Stream s=streams.remove(name);
	if(s!=null) managementService.unregisterStream(dbname, name);
    }

    public StorageEngine getStorageEngine(TableDefinition tbldef) {
	return storageEngines.get(tbldef.getStorageEngineName());	    
    }


    public Collection<AbstractStream> getStreams() {
	return streams.values();
    }

    public Collection<TableDefinition> getTableDefinitions() {
	return tables.values();
    }

    /**
     * Returns the root directory for this database instance.
     *  It is usually home/instance_name.
     * @return
     */
    public String getRoot() {
	return getHome()+"/"+dbname;
    }

    public static void setHome(String home) {
	YarchDatabase.home = home;
    }

    public static String getHome() {
	return home;
    }

    public TCBFactory getTCBFactory() {
	return tcbFactory;
    }

    /**to be used for testing**/
    public static void removeInstance(String dbName) {
	databases.remove(dbName);
    }

    public StreamSqlResult execute(String query) throws StreamSqlException, ParseException {
	ExecutionContext context=new ExecutionContext(dbname);
	StreamSqlParser parser=new StreamSqlParser(new java.io.StringReader(query));
	try {
	    StreamSqlStatement s =  parser.OneStatement();
	    return s.execute(context);
	} catch (TokenMgrError e) {
	    throw new ParseException(e.getMessage());
	}
    }
}
