package org.yamcs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.LogManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.yamcs.utils.TimeEncoding;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;


/**
 * This class loads yamcs configurations. There are a number of "subsystems",
 *  each using a corresponding subsystem.yaml file
 *  
 *  There are three places where a configuration file is looked up in order:
 *  - in the prefix/file.yaml via the classpath if the prefix is set in the setup method (used in the unittests)
 *  - in the userConfigDirectory .yamcs/etc/file.yaml
 *  - in the file.yaml via the classpath.
 *  
 * @author nm
 */
@SuppressWarnings("rawtypes")
public class YConfiguration {
    Map<String, Object> root;
    static String userConfigDirectory; //This is used by the users to overwrite 
    private final String filename;


    private static Map<String, YConfiguration> configurations=new HashMap<String,YConfiguration>();
    static Logger log=LoggerFactory.getLogger(YConfiguration.class.getName());
    static String prefix=null;

    //keeps track of the configuration path so meaningful error messages can be printed
    //the path is someting like filename->key1->subkey2[3]->...
    static private IdentityHashMap<Object, String> confPath=new IdentityHashMap<Object, String>();


    @SuppressWarnings("unchecked")	
    private YConfiguration(String subsystem) throws IOException, ConfigurationException {
	Yaml yaml=new Yaml();
	filename=subsystem+".yaml";
	try {
	    Object o=yaml.load(getConfigurationStream("/"+filename));
	    if(o==null) {
		o=new HashMap<String, Object>(); //config file is empty, not an error
	    } else if(!(o instanceof Map<?, ?>)) {
		throw new ConfigurationException(filename, "top level structure must be a map and not a "+o);
	    }
	    root=(Map<String, Object>)o;
	    confPath.put(root, filename);
	} catch (YAMLException e) {
	    throw new ConfigurationException(filename, e.toString(), e);
	}
    }

    /**
     * If configPrefix is not null, sets up the configuration to search the classpath for files like "configPrefix/xyz.properties"
     * 
     * Also sets up the TimeEncoding configuration
     * 
     * @param configPrefix
     * @throws ConfigurationException
     */
    public synchronized static void setup(String configPrefix) throws ConfigurationException {
	prefix=configPrefix;
	configurations.clear();//forget any known config (useful in the maven unit tests called in the same VM)
	if(System.getenv("YAMCS_DAEMON")==null) {
	    userConfigDirectory=System.getProperty("user.home")+File.separatorChar+".yamcs";
	    File logDir = new File(userConfigDirectory+File.separatorChar+"log");
	    if (!logDir.exists()) {
		if (logDir.mkdirs()) {
		    System.err.println("Created directory: "+logDir);
		} else {
		    System.err.println("Cannot create directory: "+logDir);
		}
	    }
	    System.getProperties().put("cacheDirectory", userConfigDirectory+File.separatorChar);
	} else {
	    String yamcsDirectory=System.getProperty("user.home");
	    System.getProperties().put("cacheDirectory", yamcsDirectory+File.separatorChar+"cache"+File.separatorChar);
	    userConfigDirectory=yamcsDirectory+File.separatorChar+"etc";
	}

	if(System.getProperty("java.util.logging.config.file")==null) {
	    try {
		LogManager.getLogManager().readConfiguration(getConfigurationStream("/logging.properties"));
	    } catch (Exception e) {
		//do nothing, the default java builtin logging is used
	    }
	}
	TimeEncoding.setUp();	    
    }
    /**
     * calls setup(null)
     * 
     * @throws ConfigurationException
     */
    public synchronized static void setup() throws ConfigurationException {
	setup(null);
    }


    public synchronized static YConfiguration getConfiguration(String subsystem) throws ConfigurationException {
	if(subsystem.contains("..") || subsystem.contains("/")) throw new ConfigurationException("Invalid subsystem '"+subsystem+"'");
	YConfiguration c=configurations.get(subsystem);
	if(c==null) {
	    try {
		c=new YConfiguration(subsystem);
	    } catch (IOException e){
		throw new ConfigurationException("Cannot load configuration for subsystem "+subsystem+": "+e);
	    }
	    configurations.put(subsystem, c);
	}
	return c;
    }

    private static InputStream getConfigurationStream(String name) throws ConfigurationException {
	InputStream is;
	if(prefix!=null) {
	    if((is=YConfiguration.class.getResourceAsStream("/"+prefix+name))!=null) {
		log.info( "Reading configuration from "+new File( YConfiguration.class.getResource("/"+prefix+name).getFile() ).getAbsolutePath() );
		return is;
	    }
	}

	//see if the users has a own version of the file
	File f=new File(userConfigDirectory+name);
	if(f.exists()) {
	    try {
		is=new FileInputStream(f);
		log.info( "Reading configuration from "+f.getAbsolutePath() );
		return is;
	    } catch (FileNotFoundException e) {
		e.printStackTrace();
	    }
	}
	if((is=YConfiguration.class.getResourceAsStream(name))==null) {
	    throw(new ConfigurationException("Can not find resource "+name));
	}
	log.info( "Reading configuration from "+new File( YConfiguration.class.getResource(name).getFile() ).getAbsolutePath() );
	return is;
    }

    public String getGlobalProperty(String key) {
	return System.getProperty(key);
    }

    static private void checkKey(Map m, String key) throws ConfigurationException {
	if(!m.containsKey(key)) throw new ConfigurationException(confPath.get(m), "cannot find a mapping for key '"+key+"'");
	else if(m.get(key)==null) throw new ConfigurationException(confPath.get(m), key+" exists but is null");
    }



    public boolean containsKey(String key) {
	return root.containsKey(key);
    }

    public boolean containsKey(String key, String key1) throws ConfigurationException {
	Map<String, Object> m=getMap(key);
	return m.containsKey(key1);
    }


    /**
     * returns the first entry in the config file if it's a map. Otherwise throws an error
     * @return
     */
    public Map<String, Object> getFirstMap() throws ConfigurationException {
	Object o=root.values().iterator().next();
	if(o instanceof Map) {
	    return (Map)o;
	} else {
	    throw new ConfigurationException("the first entry in the config is of type "+o.getClass()+" and not Map");
	}
    }


    /**
     * returns the first entry(key) in the config file.
     * @return
     */
    public String getFirstEntry() throws ConfigurationException {
	return root.keySet().iterator().next();
    }

    public Set<String> getKeys() {
	return root.keySet();
    }


    private static String getUnqualfiedClassName(Object o) {
	String name=o.getClass().getName();
	if (name.lastIndexOf('.') > 0) {
	    name = name.substring(name.lastIndexOf('.')+1);  // Map$Entry
	}
	// The $ can be converted to a .
	name = name.replace('$', '.');      // Map.Entry
	return name;
    }


    /****************************** Map configs*/

    static public Map<String, Object> getMap(Map<String, Object> m, String key) throws ConfigurationException {
	checkKey(m, key);
	Object o=m.get(key);
	if(o instanceof Map) {
	    Map<String, Object> m1=(Map)o;
	    if(confPath.containsKey(m1)) {
		confPath.put(m1, confPath.get(m)+"->"+key);
	    }
	    return m1;
	} else {
	    throw new ConfigurationException(confPath.get(m), "mapping for key '"+key+"' is of type "+o.getClass().getCanonicalName()+" and not Map");
	}
    }

    public Map<String, Object> getMap(String key) throws ConfigurationException {
	return getMap(root, key);
    }

    public Map<String, Object> getMap(String key, String key1) throws ConfigurationException {
	Map<String, Object> m=getMap(key);
	return getMap(m, key1);
    }



    /***************************String configs*/

    /**
     * Returns m.get(key) if it exists and is of type string, otherwise throws an exception
     * @param m
     * @param key
     * @return
     * @throws ConfigurationException
     */
    static public String getString(Map m, String key) throws ConfigurationException {
	checkKey(m, key);

	Object o=m.get(key);
	if(o instanceof String) {
	    return (String)o;
	} else {
	    throw new ConfigurationException(confPath.get(m), "mapping for key '"+key+"' is of type "+getUnqualfiedClassName(o)+" and not String");
	}
    }

    public String getString(String key) throws ConfigurationException {
	return getString(root, key);
    }

    /*
     * The key has to point to a map that contains the subkey that points to a string
     */
    public String getString(String key, String subkey) throws ConfigurationException {
	Map<String, Object> m=getMap(key);
	return getString(m, subkey);
    }

    public String getString(String key, String key1, String key2) throws ConfigurationException {
	Map<String, Object> m=getMap(key,key1);
	return getString(m, key2);
    }

    public List getList(String key) throws ConfigurationException {
	return getList(root, key);
    }


    /*****************List configs*/
    /*
     * The key has to point to a list
     */
    static public  List getList(Map<String, Object> m, String key) throws ConfigurationException {
	checkKey(m, key);
	Object o=m.get(key);
	if(o instanceof List) {
	    List l=(List) o;
	    String parentPath=confPath.get(m);
	    for(int i=0; i<l.size();i++) {
		Object o1=l.get(i);
		if(!confPath.containsKey(o1)) {
		    confPath.put(o1, parentPath+"->"+key+"["+i+"]");
		}
	    }
	    return l;
	} else {
	    throw new ConfigurationException(confPath.get(m), "mapping for key '"+key+"' is of type "+getUnqualfiedClassName(o)+" and not List");
	}
    }

    public List getList(String key, String key1, String key2) throws ConfigurationException {
	Map<String, Object> m=getMap(key,key1);
	return getList(m, key2);
    }

    public List getList(String key, String key1) throws ConfigurationException {
	Map<String, Object> m=getMap(key);
	return getList(m, key1);
    }

    /**********************Boolean configs*/
    /**
     * Returns m.get(key) if it exists and is of type boolean, otherwise throws an exception
     * @param m
     * @param key
     * @return
     * @throws ConfigurationException
     */
    static public boolean getBoolean(Map<String, Object> m, String key, boolean defaultValue)  throws ConfigurationException {
	Object o=m.get(key);
	if((o!=null) && (o instanceof Boolean)) {
	    return (Boolean)o;
	} else {
	    return defaultValue;
	}
    }

    static public boolean getBoolean(Map<String, Object> m, String key)  throws ConfigurationException {
	checkKey(m, key);
	Object o=m.get(key);
	if(o instanceof Boolean) {
	    return (Boolean)o;
	} else {
	    throw new ConfigurationException(confPath.get(m), "mapping for key '"+key+"' is of type "+getUnqualfiedClassName(o)+" and not Boolean (use true or false without quotes)");
	}
    }


    public boolean getBoolean(String key) throws ConfigurationException {
	return getBoolean(root,key);
    }

    public boolean getBoolean(String key, String key1) throws ConfigurationException {
	Map<String, Object> m=getMap(key);
	return getBoolean(m, key1);
    }

    public boolean getBoolean(String key, String key1, String key2) throws ConfigurationException {
	Map<String, Object> m=getMap(key,key1);
	return getBoolean(m, key2);
    }



    /********************** int configs */
    static public int getInt(Map<String, Object> m, String key) throws ConfigurationException {
	checkKey(m, key);
	Object o=m.get(key);
	if(o instanceof Integer) {
	    return (Integer)o;
	} else {
	    throw new ConfigurationException(confPath.get(m), "mapping for key '"+key+"' is of type "+getUnqualfiedClassName(o)+" and not Integer");
	}
    }


    public int getInt(String key) throws ConfigurationException {
	return getInt(root,key);
    }

    public int getInt(String key, String key1) throws ConfigurationException {
	Map<String, Object> m=getMap(key);
	return getInt(m, key1);
    }
}
