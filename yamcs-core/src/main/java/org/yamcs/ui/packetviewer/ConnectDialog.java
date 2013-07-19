package org.yamcs.ui.packetviewer;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import org.hornetq.api.core.HornetQException;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Yamcs.YamcsInstance;
import org.yamcs.protobuf.Yamcs.YamcsInstances;


/**
 * Dialog for entering yamcs connection parameters. This is a copy of the YamcsConnectDialog with options 
 * to get also dbConfigName. We may replace the main one at some point.
 * @author nm
 *
 */
public class ConnectDialog extends JDialog implements ActionListener {
	private static final long serialVersionUID = 1L;
	private YamcsConnectData values;
	JTextField hostTextField;
	JTextField portTextField;
	JTextField usernameTextField;
	private JPasswordField passwordTextField;
	//JCheckBox sslCheckBox;
	private JComboBox instanceCombo, serverMdbConfigCombo, localMdbConfigCombo;
	boolean getInstance=false;
	boolean getMdbConfig=false;
	boolean getStreamName=false;
	
	String dbConfig;
	boolean isDbConfigLocal;
	int returnValue;
	Preferences prefs;
	boolean useServerMdb;
	JTextField streamName;
	
	
    /**
     * Return value if cancel is chosen.
     */
    public static final int CANCEL_OPTION = 1;
    /**
     * Return value if approve (yes, ok) is chosen.
     */
    public static final int APPROVE_OPTION = 0;
    
    final boolean authenticationEnabled;
	
	ConnectDialog(JFrame parent, boolean authenticationEnabled, boolean getInstance, boolean getStreamName, boolean getDbConfig) {
		super(parent, "Yamcs Connection", true);
		this.authenticationEnabled = authenticationEnabled;
		this.getInstance=getInstance;
		this.getMdbConfig=getDbConfig;
		this.getStreamName=getStreamName;
		installActions();
		
		values = new YamcsConnectData();
		values.load();
		prefs=Preferences.userNodeForPackage(this.getClass());
		
		
		JPanel inputPanel, buttonPanel;
		JLabel lab;
		JButton button;

		// input panel

		inputPanel = new JPanel(new GridBagLayout());
		GridBagConstraints ceast = new GridBagConstraints();
		ceast.anchor=GridBagConstraints.EAST;
		GridBagConstraints cwest = new GridBagConstraints();
		cwest.weightx=1; cwest.fill=GridBagConstraints.HORIZONTAL;
		
		inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
		getContentPane().add(inputPanel, BorderLayout.CENTER);

		lab = new JLabel("Host: ");
		lab.setHorizontalAlignment(SwingConstants.RIGHT);
		ceast.gridy=1;		inputPanel.add(lab,ceast);
		hostTextField = new JTextField(values.host);
		hostTextField.setPreferredSize(new Dimension(80, hostTextField.getPreferredSize().height));
		cwest.gridy=1;inputPanel.add(hostTextField,cwest);

		lab = new JLabel("Port: ");
		lab.setHorizontalAlignment(SwingConstants.RIGHT);
		ceast.gridy=2; inputPanel.add(lab,ceast);
		portTextField = new JTextField(Integer.toString(values.port));
		cwest.gridy=2; inputPanel.add(portTextField,cwest);
/*
		lab = new JLabel("Use SSL (not implemented): ");
		lab.setHorizontalAlignment(SwingConstants.RIGHT);
		c.gridy=3;c.gridx=0;c.anchor=GridBagConstraints.EAST;inputPanel.add(lab,c);
		sslCheckBox = new JCheckBox(); sslCheckBox.setSelected(values.ssl);
		c.gridy=3;c.gridx=1;c.anchor=GridBagConstraints.WEST;inputPanel.add(sslCheckBox,c);
 */
		if(authenticationEnabled) {
		    ceast.gridy++;
		    cwest.gridy++;
		    lab = new JLabel("Username: ");
		    lab.setHorizontalAlignment(SwingConstants.RIGHT);
		    inputPanel.add(lab,ceast);
		    usernameTextField = new JTextField(values.username);
		    usernameTextField.setPreferredSize(new Dimension(80, usernameTextField.getPreferredSize().height));
		    inputPanel.add(usernameTextField,cwest);

		    ceast.gridy++;
		    cwest.gridy++;
		    lab = new JLabel("Password: ");
		    lab.setHorizontalAlignment(SwingConstants.RIGHT);
		    inputPanel.add(lab,ceast);
		    passwordTextField = new JPasswordField();
		    passwordTextField.setPreferredSize(new Dimension(80, passwordTextField.getPreferredSize().height));
		    inputPanel.add(passwordTextField,cwest);
		}
		
		if(getInstance) {
		    lab = new JLabel("Instance: ");
		    lab.setHorizontalAlignment(SwingConstants.RIGHT);
		    
		    ceast.gridy++;
		    cwest.gridy++;
		    
		    inputPanel.add(lab, ceast);
		    instanceCombo = new JComboBox(new String[]{values.instance});
		    instanceCombo.setPreferredSize(hostTextField.getPreferredSize());
		    instanceCombo.setEditable(true);
		    
		    inputPanel.add(instanceCombo,cwest);
		    button = new JButton("get");
	        button.setActionCommand("getInstances");
	        button.addActionListener(this);
	        inputPanel.add(button,ceast);
		}
		
		if(getStreamName) {
		    ceast.gridy++;
		    cwest.gridy++;
		    
		    lab = new JLabel("Stream: ");
		    
            lab.setHorizontalAlignment(SwingConstants.RIGHT);
            inputPanel.add(lab, ceast);
            
            String name=prefs.get("streamName", "tm_realtime");
            streamName=new JTextField(name);            
            streamName.setEditable(true);
            
            inputPanel.add(streamName, cwest);
		}

		if(getDbConfig) {
		    ceast.gridy++;
		    cwest.gridy++;
		    
		    useServerMdb=prefs.getBoolean("useServerMdb", true);
            System.out.println("prefs useServerMdb: "+useServerMdb);
            
            ButtonGroup bgroup=new ButtonGroup();
            JRadioButton jrb=new JRadioButton("Server MDB: ");
            if(useServerMdb) jrb.setSelected(true);
            jrb.setActionCommand("use-server-mdb");
            jrb.addActionListener(this);
            bgroup.add(jrb);
     //       lab = new JLabel("Server MDB: ");
          //  lab.setHorizontalAlignment(SwingConstants.RIGHT);
            GridBagConstraints c = new GridBagConstraints();
            c.gridy=ceast.gridy; c.anchor=GridBagConstraints.WEST; 
            inputPanel.add(jrb, c);
            
            String selectedServerMdbConfig=prefs.get("selectedServerMdbConfig", null);
            serverMdbConfigCombo = new JComboBox(new String[]{selectedServerMdbConfig});
            serverMdbConfigCombo.setPreferredSize(hostTextField.getPreferredSize());
            serverMdbConfigCombo.setEditable(true);
            inputPanel.add(serverMdbConfigCombo, cwest);
            if(!useServerMdb) serverMdbConfigCombo.setEnabled(false);
            
            button = new JButton("get");
            button.setActionCommand("getInstances");
            button.addActionListener(this);
            inputPanel.add(button,ceast);
            
            //when changing the instance, change also the database (but not the other way around)
            instanceCombo.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					int i=instanceCombo.getSelectedIndex();
					if(serverMdbConfigCombo.getItemCount()>i)
						serverMdbConfigCombo.setSelectedIndex(i);
				}
			});

            

            ceast.gridy++;
            cwest.gridy++;
            jrb=new JRadioButton("Local MDB: ");
            jrb.setActionCommand("use-local-mdb");
            jrb.addActionListener(this);
            if(!useServerMdb) jrb.setSelected(true);
            bgroup.add(jrb);
            //lab = new JLabel("Local MDB: ");
            //lab.setHorizontalAlignment(SwingConstants.RIGHT);
            c.gridy=ceast.gridy; inputPanel.add(jrb, c);
            try {
                String[] dbconfigs=getLocalDbConfigs();
                localMdbConfigCombo = new JComboBox(dbconfigs);
                localMdbConfigCombo.setPreferredSize(hostTextField.getPreferredSize());
                localMdbConfigCombo.setEditable(false);

                String selectedLocalMdbConfig=prefs.get("selectedLocalMdbConfig", dbconfigs.length>0?dbconfigs[0]:null);
                localMdbConfigCombo.setSelectedItem(selectedLocalMdbConfig);
                inputPanel.add(localMdbConfigCombo, cwest);
                if(useServerMdb) localMdbConfigCombo.setEnabled(false);
            } catch (ConfigurationException e) {
                JOptionPane.showMessageDialog(this, "Cannot load local MDB configurations: "+e.getMessage(), "Cannot load local MDB configs", JOptionPane.ERROR_MESSAGE);
                String[] dbconfigs=new String[]{"unavailable"};
                localMdbConfigCombo = new JComboBox(dbconfigs);
                localMdbConfigCombo.setPreferredSize(hostTextField.getPreferredSize());
                localMdbConfigCombo.setEnabled(false);
                localMdbConfigCombo.setSelectedItem("unavailable");
                inputPanel.add(localMdbConfigCombo, cwest);
                jrb.setSelected(false);
                jrb.setEnabled(false);
                
            }
        }
        
        
        
        // button panel

		buttonPanel = new JPanel();
		getContentPane().add(buttonPanel, BorderLayout.SOUTH);

		button = new JButton("OK");
		button.setActionCommand("ok");
		button.addActionListener(this);
		getRootPane().setDefaultButton(button);
		buttonPanel.add(button);

		button = new JButton("Cancel");
		button.setActionCommand("cancel");
		button.addActionListener(this);
		buttonPanel.add(button);

		setMinimumSize(new Dimension(350, 100));
		setLocationRelativeTo(parent);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		pack();
	}

    private void installActions() {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "closeDialog");
        root.getActionMap().put("closeDialog", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                dispatchEvent(new WindowEvent(ConnectDialog.this, WindowEvent.WINDOW_CLOSING));
            }
        });
    }

	private String[] getLocalDbConfigs() throws ConfigurationException {
	    YConfiguration conf = YConfiguration.getConfiguration("mdb");
	    return conf.getKeys().toArray(new String[0]);
    }

    @Override
    public void actionPerformed( ActionEvent e ) {
	    String cmd=e.getActionCommand();
		if ( "ok".equals(cmd)) {
				values.host = hostTextField.getText();
				try {
				    values.port = Integer.parseInt(portTextField.getText());
				} catch (NumberFormatException x) {
				    JOptionPane.showMessageDialog(this, "Cannot parse port number; please enter a number", "Invalid port", JOptionPane.ERROR_MESSAGE);
				    return; // do not close the dialogue
				}
				//values.ssl= sslCheckBox.isSelected();
				if(authenticationEnabled) {
				    values.username = usernameTextField.getText();
				    values.password = passwordTextField.getText();
				    passwordTextField.setText("");

	                // Treat empty strings as null
	                if( "".equals( values.username ) ) values.username = null;
	                if( "".equals( values.password ) ) values.password = null;
	                
				} else {
					// If not authenticating, don't use last credentials
					values.username = null;
					values.password = null;
				}

				if(instanceCombo!=null) 	values.instance=(String)instanceCombo.getSelectedItem();
				values.save();
				prefs.putBoolean("useServerMdb", useServerMdb);
				if(useServerMdb) {
				    String selectedServerMbConfig=(String)serverMdbConfigCombo.getSelectedItem();
				    if(selectedServerMbConfig==null) {
				        JOptionPane.showMessageDialog(this, "Please enter the server MDB config that shall be used", "Invalid MDB config", JOptionPane.ERROR_MESSAGE);
	                    return; // do not close the dialogue
				    }
				    prefs.put("selectedServerMdbConfig", (String)serverMdbConfigCombo.getSelectedItem());
				} else {
				    prefs.put("selectedLocalMdbConfig", (String)localMdbConfigCombo.getSelectedItem());
				}
				returnValue=APPROVE_OPTION;
				setVisible(false);			
		} else if ("cancel".equals(cmd) ) {
			returnValue=CANCEL_OPTION;
			setVisible(false);
		} else if("getInstances".equals(cmd) ) {
		    try {
		        String host=hostTextField.getText();
		        int port=Integer.parseInt(portTextField.getText());
		        
		        String username = null;
		        String password = null;
		        if(authenticationEnabled) {
		        	username = usernameTextField.getText();
					password = passwordTextField.getText();
					// Treat empty strings as null
					if( "".equals( username ) ) username = null;
					if( "".equals( password ) ) password = null;
		        }
				
				YamcsSession ys=YamcsSession.newBuilder().setConnectionParams(host, port, username, password).build();
		        YamcsClient msgClient=ys.newClientBuilder().setRpc(true).build();
		        YamcsInstances ainst=(YamcsInstances)msgClient.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getYamcsInstances", null, YamcsInstances.newBuilder());
		        instanceCombo.removeAllItems();
		        serverMdbConfigCombo.removeAllItems();
		        System.out.println("got "+ainst);
		        for(YamcsInstance ai:ainst.getInstanceList()) {
		            if(getInstance) {
		                instanceCombo.addItem(ai.getName());
		            }
		            if(getMdbConfig) {
		                serverMdbConfigCombo.addItem(ai.getMissionDatabase().getConfigName());
		            }
		        }
		        msgClient.close();
		        ys.close();
		    } catch (NumberFormatException x) {
		        JOptionPane.showMessageDialog(this, "please enter a valid port number", x.getMessage(), JOptionPane.WARNING_MESSAGE);
            } catch (HornetQException e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: "+e1.getMessage(), e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            }  catch (YamcsException e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: "+e1.getMessage(), e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            } catch (YamcsApiException e1) {
                JOptionPane.showMessageDialog(this, "Cannot retrieve the archive instances: "+e1.getMessage(), e1.getMessage(), JOptionPane.WARNING_MESSAGE);
            }
		} else if("use-server-mdb".equals(cmd)) {
		    useServerMdb=true;
		    serverMdbConfigCombo.setEnabled(true);
		    localMdbConfigCombo.setEnabled(false);
		} else if("use-local-mdb".equals(cmd)) {
            useServerMdb=false;
            serverMdbConfigCombo.setEnabled(false);
            localMdbConfigCombo.setEnabled(true);
        }
	}

    public YamcsConnectData getConnectData() {
        return values.clone();
    }

    public boolean getUseServerMdb() {
        return useServerMdb;
    }

    public String getLocalMdbConfig() {
        return (String)localMdbConfigCombo.getSelectedItem();
    }
    
    public String getServerMdbConfig() {
        return (String) serverMdbConfigCombo.getSelectedItem();
    }
    
    public int showDialog() {
    	// Prevent caching of last returnValue
    	returnValue = CANCEL_OPTION;
        setVisible(true);
        return returnValue;
    }
    
    public static void main(String[] args){
        ConnectDialog ycd=new ConnectDialog(null, false, true, true, true);
        ycd.showDialog();
    }

   

}
