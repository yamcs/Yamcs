package org.yamcs.ui;

import static org.yamcs.api.Protocol.DATA_TYPE_HEADER_NAME;
import static org.yamcs.api.Protocol.decode;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.CharArrayReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import org.hornetq.api.core.SimpleString;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.ConnectionParameters;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsSession;
import org.yamcs.protobuf.Pvalue.ParameterData;
import org.yamcs.protobuf.Pvalue.ParameterValue;
import org.yamcs.protobuf.Yamcs.EndAction;
import org.yamcs.protobuf.Yamcs.NamedObjectId;
import org.yamcs.protobuf.Yamcs.NamedObjectList;
import org.yamcs.protobuf.Yamcs.ParameterReplayRequest;
import org.yamcs.protobuf.Yamcs.ProtoDataType;
import org.yamcs.protobuf.Yamcs.ReplayRequest;
import org.yamcs.protobuf.Yamcs.StringMessage;
import org.yamcs.utils.ParameterFormatter;
import org.yamcs.utils.PetParameterFormatter;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.MdbMappings;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * @author nm
 * GUI for requesting parameter dumps
 */
public class ParameterRetrievalGui extends JFrame implements MessageHandler, ConnectionListener, ParameterListener, ParameterSelectDialogListener {
	JCheckBox printTime, printRaw, printUnique, keepValues, ignoreInvalidParameters, printFullLines, timeWindow;
	JTextField timeWindowSize;
	JButton startButton;
	JFileChooser loadfileChooser, outfileChooser;
	long start, stop;
	String archiveInstance;
	Component parent;
	ProgressMonitor progressMonitor;
	JTextArea paramText;
	JTextField fileNameTextField;
	private BufferedWriter writer;
	private Preferences prefs;
	final int maxRecent = 10;
	ArrayList<String> recentFiles;
	JPopupMenu recentPopup;
	JButton recentButton;
	ConnectionParameters connectionParams;
    private ParameterFormatter parameterFormatter;
    JCheckBox petOutput;
    ParameterSelectDialog selectDialog;
    
    YamcsSession ysession;
	YamcsClient yclient;

	/**
	 * Creates a new window that requests parameter delivieries
	 * @param app
	 * @param parent
	 */
    public ParameterRetrievalGui(ConnectionParameters connectionParameters, Component parent) {
		super("Parameter Retrieval");
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		prefs = Preferences.userNodeForPackage(getClass());
		recentFiles = new ArrayList<String>();
		for (int i = 1; ; ++i) {
			String s = prefs.get("recentFile" + i, "");
			if (s.equalsIgnoreCase("")) {
				break;
			} else {
				recentFiles.add(s);
			}
		}

		this.connectionParams=connectionParameters;
		this.parent=parent;

		final String home = System.getProperties().getProperty("user.home");
		loadfileChooser=new JFileChooser();
		loadfileChooser.setApproveButtonText("Load");
		loadfileChooser.setDialogTitle("Select a file containing parameter names");
		loadfileChooser.setCurrentDirectory(new File(prefs.get("loadPath", home)));
		outfileChooser=new JFileChooser();
		outfileChooser.setApproveButtonText("Choose");
		outfileChooser.setDialogTitle("Select output file");
		outfileChooser.setCurrentDirectory(new File(prefs.get("outputPath", home)));

		GridBagLayout gridbag = new GridBagLayout();
		setLayout(gridbag);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;gbc.anchor=GridBagConstraints.NORTH;
		
		JLabel label=new JLabel("Dump the selected telemetry parameters into a file.");
		gbc.gridwidth=GridBagConstraints.REMAINDER;
		//gbc.ipadx=5; gbc.ipady=5;
		gbc.insets=new Insets(5,5,5,5);
		getContentPane().add(label,gbc);
		
		//options
		Box optionsPanel=Box.createVerticalBox();
		optionsPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Options"),BorderFactory.createEmptyBorder(5,5,5,5)));
		printTime=new JCheckBox("Print the generation time");
		printTime.setSelected(true);
		optionsPanel.add(printTime);
		printRaw=new JCheckBox("Print the raw value");
		optionsPanel.add(printRaw);
		printUnique=new JCheckBox("Print only the unique lines");
		optionsPanel.add(printUnique);
		printFullLines=new JCheckBox("Print only the full lines");
		optionsPanel.add(printFullLines);
		keepValues=new JCheckBox("Keep previous values");
		optionsPanel.add(keepValues);
		Box timeWindowPanel=Box.createHorizontalBox();
		timeWindow=new JCheckBox("Merging time window of");
		timeWindowPanel.add(timeWindow);
		timeWindowSize=new JTextField(5);
		timeWindowSize.setText("500");
		timeWindowSize.setMaximumSize(timeWindowSize.getPreferredSize());
		timeWindowPanel.add(timeWindowSize);
		JLabel l=new JLabel("ms");
		timeWindowPanel.add(l);
		timeWindowPanel.setAlignmentX(LEFT_ALIGNMENT);
		optionsPanel.add(timeWindowPanel);
		ignoreInvalidParameters=new JCheckBox("Ignore Invalid Parameters");
		optionsPanel.add(ignoreInvalidParameters);
		petOutput=new JCheckBox("Output in PET format");
		optionsPanel.add(petOutput);

		gbc.gridwidth=1;gbc.weighty = 1.0;gbc.weightx = 0.0;
		getContentPane().add(optionsPanel,gbc);
		
		Box paramPanel=Box.createVerticalBox();
		paramPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Parameters"),BorderFactory.createEmptyBorder(5,5,5,5)));
		paramText = new JTextArea(15, 30);
		TextAreaListener listener = new TextAreaListener() {
			@Override
            public void updated() {
				startButton.setEnabled(
					(!paramText.getText().equalsIgnoreCase("")) && !fileNameTextField.getText().equalsIgnoreCase("")
				);
			}
		};
		paramText.getDocument().addDocumentListener(listener);
		JScrollPane scrollPane = new JScrollPane(paramText);
		paramPanel.add(scrollPane);

		JPanel parambuttonPanel=new JPanel();
		paramPanel.add(parambuttonPanel);
		JButton button=new JButton("Open");
		button.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(ActionEvent arg0) {
				loadParameters();
			}
		});
		parambuttonPanel.add(button);

		button=new JButton("Save");
		button.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(ActionEvent arg0) {
				saveParameters();
			}
		});
		parambuttonPanel.add(button);

		recentPopup = new JPopupMenu();
		recentButton = new JButton("Recent");
		populateRecentMenu();
		recentButton.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(ActionEvent event) {
				recentPopup.show(recentButton, 0, recentButton.getSize().height);
			}
		});
		parambuttonPanel.add(recentButton);
		
		// Provide method to select parameters from a hierarchy
		JButton selectParamButton = new JButton( "Select" );
		selectParamButton.addActionListener( new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				selectParameters();
			}
		});
		parambuttonPanel.add( selectParamButton );

		gbc.weighty = 1.0;gbc.weightx = 1.0;gbc.gridwidth=GridBagConstraints.REMAINDER;
		getContentPane().add(paramPanel,gbc);
		
		//gbc.insets=new Insets(2,2,2,2);
		Box outputPanel=Box.createHorizontalBox();
		outputPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Output File"),BorderFactory.createEmptyBorder(5,5,5,5)));
		fileNameTextField=new JTextField();
		outputPanel.add(fileNameTextField);
		button=new JButton("Choose");
		button.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(ActionEvent arg0) {
				chooseOutputFile();
			}
		});
		outputPanel.add(button);

		gbc.gridwidth=GridBagConstraints.REMAINDER; gbc.weighty = 0.0;gbc.weightx = 1.0;gbc.anchor=GridBagConstraints.CENTER;
		getContentPane().add(outputPanel,gbc);

		JPanel actionbuttonPanel=new JPanel();
		startButton=new JButton("Start Retrieval");
		startButton.setEnabled(false);
		actionbuttonPanel.add(startButton);
		startButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent arg0) {
				startRetrieval();
			}
		});
		button=new JButton("Close");
		actionbuttonPanel.add(button);
		button.addActionListener(new ActionListener() {
			@Override
            public void actionPerformed(ActionEvent arg0) {
				setVisible(false);
			}
		});

		gbc.gridwidth=GridBagConstraints.REMAINDER; gbc.weighty = 0.0;gbc.weightx = 1.0;gbc.anchor=GridBagConstraints.CENTER;
		getContentPane().add(actionbuttonPanel,gbc);

		pack();
	}

    /**
     * Uses blocking method to get list of parameters using a hierarchical display.
     */
    private void selectParameters() {
    	YamcsConnectData ycd=(YamcsConnectData)connectionParams;
        ycd.instance=archiveInstance;
        if( selectDialog == null ) {
        	selectDialog = new ParameterSelectDialog(this,ycd);
        	selectDialog.addListener( this );
        }
    	List<String> params = selectDialog.showDialog();
    	if( params != null ) {
    		setParameters( params );
    	}
    }
    
    @Override
	public void parametersAdded( List<String> opsnames ) {
    	addParameters( opsnames );
	}

    /**
     * Convenience method to add opsnames to the displayed list without
     * duplicating any existing entries.
     * 
     * @param opsnames
     */
    public void addParameters( List<String> opsnames ) {
    	List<String> currentOpsnames = Arrays.asList( paramText.getText().split( "\n" ) );
    	for( String opsname : opsnames ) {
    		if( ! currentOpsnames.contains( opsname ) ) {
    			paramText.append( opsname );
        		paramText.append( "\n" );
    		}
    	}
    }
    
    /**
     * Convenience method to set current parameter list.
     * 
     * @param opsnames
     */
    public void setParameters( List<String> opsnames ) {
    	paramText.setText("");
    	for( String opsname : opsnames ) {
    		paramText.append( opsname );
    		paramText.append( "\n" );
    	}
    }
    
    private void populateRecentMenu() {
		recentPopup.removeAll();
		for (int i = 0; i < recentFiles.size(); ++i) {
			final String s = recentFiles.get(i);
			recentPopup.add(new AbstractAction(s) {
				public void actionPerformed(ActionEvent arg0) {
					loadParameters(new File(s));
				}
			});
		}
		recentPopup.pack();
		recentButton.setEnabled(recentPopup.getComponentCount() > 0);
	}

	private void addRecentFile(File file) {
		final String filename = file.getAbsolutePath();
		if (recentFiles.indexOf(filename) == -1) {
			while (recentFiles.size() > maxRecent) {
				recentFiles.remove(0);
			}
			recentFiles.add(filename);
			for (int i = 0; i < recentFiles.size(); ++i) {
				prefs.put("recentFile" + (i + 1), recentFiles.get(i));
			}
			populateRecentMenu();
		}
	}

	public void setValues(String archiveInstance, long start, long stop) {
		this.start=start;
		this.stop=stop;
		this.archiveInstance=archiveInstance;

		String startWinCompatibleDateTime = TimeEncoding.toWinCompatibleDateTime(start);
		String stopWinCompatibleDateTime  = TimeEncoding.toWinCompatibleDateTime(stop);			
		fileNameTextField.setText(String.format("parameters_%s_%s.dump"
													,startWinCompatibleDateTime
													,stopWinCompatibleDateTime));
	}

	private void showErrorMessage(final String s) {
	    SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JOptionPane.showMessageDialog(getParent(), s, getTitle(), JOptionPane.ERROR_MESSAGE);
            }
        });		
	}

	private void showMessage(String s) {
		JOptionPane.showMessageDialog(getParent(), s, getTitle(), JOptionPane.INFORMATION_MESSAGE);
	}

	private void loadParameters() {
		int returnVal=loadfileChooser.showOpenDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File f = loadfileChooser.getSelectedFile();
			prefs.put("loadPath", loadfileChooser.getCurrentDirectory().getAbsolutePath());
			loadParameters(f);
		}
	}

	private void loadParameters(File f) {
	    try {
	        startButton.setEnabled(false);
	        paramText.setText("");
	        List<NamedObjectId> paraList=loadParameters(new BufferedReader(new FileReader(f)));
	        for(NamedObjectId id:paraList) {
	            if(MdbMappings.MDB_OPSNAME.equals(id.getNamespace())) {
	                paramText.append(id.getName());    
	            } else {
	                paramText.append(id.getNamespace()+":"+id.getName());
	            }
	            paramText.append("\n");
	        }
	        addRecentFile(f);
	    } catch (IOException e) {
	        showErrorMessage("Cannot load parameters: "+e.getMessage());
	    }
	}

	public static List<NamedObjectId> loadParameters(BufferedReader reader) throws IOException {
	    List<NamedObjectId> paramList=new ArrayList<NamedObjectId>();
	    String line=reader.readLine();

	    if(line==null) return paramList; //hmm, empty file
	    if(line.contains("<")) {
	        //read an xml file
	        Pattern p1 = Pattern.compile(".*\\<TelemetryName context=\"CGS_ALIAS\"\\>(\\w+)\\<\\/TelemetryName\\>.*");
	        Pattern p2 = Pattern.compile(".*<string>Opsname</string>.*");
	        Pattern p3 = Pattern.compile(".*\\<string\\>(\\w+)\\<\\/string\\>.*");

	        do {
	            Matcher m1=p1.matcher(line);
	            if(m1.matches()) {
	                paramList.add(NamedObjectId.newBuilder()
	                        .setNamespace(MdbMappings.MDB_OPSNAME)
	                        .setName(m1.group(1)).build());
	            } else {
	                Matcher m2=p2.matcher(line);
	                if(m2.matches()) {
	                    line=reader.readLine();
	                    if (line==null) break;
	                    Matcher m3=p3.matcher(line);
	                    if(m3.matches()) {
	                        paramList.add(NamedObjectId.newBuilder()
	                                .setNamespace(MdbMappings.MDB_OPSNAME)
	                                .setName(m3.group(1)).build());
	                    }
	                }
	            }

	        } while ((line=reader.readLine())!=null);
	    } else {
	        //parameters separated by new lines, spaces or tabs
	        do {
	            String[] params=line.split("[ \t]+");
	            for(String p:params) {
	                if(p.length()>0) {
	                    paramList.add(NamedObjectId.newBuilder()
                                .setNamespace(MdbMappings.MDB_OPSNAME)
                                .setName(line).build());
	                }
	            }
	        } while ((line=reader.readLine())!=null);
	    }
	    return paramList;
	}
	
	
	private void saveParameters() {
		int returnVal=loadfileChooser.showSaveDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File f=loadfileChooser.getSelectedFile();
			try {
				PrintWriter w = new PrintWriter(f);
				w.print(paramText.getText());
				w.close();
				addRecentFile(f);
			} catch (IOException e) {
				showErrorMessage("Cannot save parameters file: "+e.getMessage());
			}
		}
	}

	private void chooseOutputFile() {
		outfileChooser.setSelectedFile(new File(outfileChooser.getCurrentDirectory(), fileNameTextField.getText()));
		int returnVal=outfileChooser.showOpenDialog(this);
		if (returnVal == JFileChooser.APPROVE_OPTION) {
			File outputFile=outfileChooser.getSelectedFile();
			if(outputFile.exists()) {
				if(JOptionPane.showConfirmDialog(this, "Are you sure you want to overwrite "+outputFile,getTitle(),JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)
						==JOptionPane.NO_OPTION) {
					return;
				}
			}
			fileNameTextField.setText(outputFile.getAbsolutePath());
			prefs.put("outputPath", outfileChooser.getCurrentDirectory().getAbsolutePath());
			if ((!paramText.getText().equalsIgnoreCase("")) && !fileNameTextField.getText().equalsIgnoreCase("")) {
				startButton.setEnabled(true);
			}
		}
	}

	private void startRetrieval() {
	    final List<NamedObjectId> paramList;
		try {
		    paramList=loadParameters(new BufferedReader(new CharArrayReader(paramText.getText().toCharArray())));
		} catch (IOException e) {
			showErrorMessage("Cannot apply parameters: "+e.getMessage());
			return;
		}
		setVisible(false);
		progressMonitor=new ProgressMonitor(parent,"Saving parameters","0 lines saved",0,(int)((stop-start)/1000));
		try {
		    writer=new BufferedWriter(new FileWriter(fileNameTextField.getText()));
		    if(petOutput.isSelected()) {
		        parameterFormatter=new PetParameterFormatter(writer, paramList);
		    } else {
		        parameterFormatter=new ParameterFormatter(writer, paramList);
		    }
	        parameterFormatter.setPrintTime(printTime.isSelected());
	        parameterFormatter.setPrintRaw(printRaw.isSelected());
	        parameterFormatter.setPrintUnique(printUnique.isSelected());

            if(timeWindow.isSelected()) {
                try {
                    parameterFormatter.setTimeWindow(Integer.parseInt(timeWindowSize.getText()));
                } catch (NumberFormatException e) {
                    showErrorMessage("Cannot parse number: "+e.getMessage()+". Please make sure that the number is integer");
                }
            } else {
                parameterFormatter.resetTimeWindow();
            }
            parameterFormatter.setAllParametersPresent(printFullLines.isSelected());
            parameterFormatter.setKeepValues(keepValues.isSelected());

            YamcsConnectData ycd=(YamcsConnectData)connectionParams;
            ycd.instance=archiveInstance;

            ysession=YamcsSession.newBuilder().setConnectionParams(ycd).build();
            yclient=ysession.newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            ParameterReplayRequest prr=ParameterReplayRequest.newBuilder().addAllNameFilter(paramList).build();
            ReplayRequest rr=ReplayRequest.newBuilder().setEndAction(EndAction.QUIT)
                .setParameterRequest(prr).setStart(start).setStop(stop).build();
            
            StringMessage answer=(StringMessage) yclient.executeRpc(Protocol.getYarchReplayControlAddress(ycd.instance), "createReplay", rr, StringMessage.newBuilder());
            SimpleString replayAddress=new SimpleString(answer.getMessage());
            
            yclient.dataConsumer.setMessageHandler(this);
            yclient.executeRpc(replayAddress, "start", null, null);
		    
		} catch(YamcsException e) {
		    if("InvalidIdentification".equals(e.getType())) {
		        try {
                    NamedObjectList nol=(NamedObjectList) e.decodeExtra(NamedObjectList.newBuilder());
                    // Prevent error message causing dialog bigger than the screen
                    StringBuffer errorMessage = new StringBuffer( "The following parameters are invalid:\n" );
                    int count = 0;
                    for( NamedObjectId noi : nol.getListList() ) {
                    	errorMessage.append( noi.getName()+", " );
                    	if( count > 4 ) { count = 0; errorMessage.append( '\n' ); }
                    	count ++;
                    }
                    showErrorMessage( errorMessage.toString() );
		        } catch (InvalidProtocolBufferException e1) {
                    gotException(e1);
                }
		    } else {
		    	gotException( e );
		    }
		} catch (Exception e) {
			gotException(e);
		} 
	}
	
	@Override
    public void onMessage(ClientMessage msg) {
        int t=msg.getIntProperty(DATA_TYPE_HEADER_NAME);
        ProtoDataType pdt=ProtoDataType.valueOf(t);
        if(pdt==ProtoDataType.STATE_CHANGE) {
            replayFinished();
            return;
        }
        if(pdt!=ProtoDataType.PARAMETER) {
            exception(new Exception("Unexpected data type "+t));
            return;
        }
        try {
            ParameterData data=(ParameterData)decode(msg, ParameterData.newBuilder());
            updateParameters(data.getParameterList());
        } catch (YamcsApiException e) {
            exception(e);
            e.printStackTrace();
            return;
        }
    }
	
	
	@Override
    public void updateParameters(List<ParameterValue> paramList) {
	    try {
	        parameterFormatter.writeParameters(paramList);
	        int linesReceived=parameterFormatter.getLinesReceived();
	        long time=paramList.get(0).getGenerationTime();
	        int progr=(int)((time-start)/1000);
	        if(linesReceived%100==0) progressMonitor.setNote(getNote());
	        progressMonitor.setProgress(progr);

	    } catch (IOException e) {
	        gotException(e);
	    }
	}
	
    @Override
    public void replayFinished() {
	    try {
	        yclient.close();
	        ysession.close();
            parameterFormatter.close();
        } catch (Exception e) {
            gotException(e);
        }
	    
		SwingUtilities.invokeLater(
				new Runnable() {
					@Override
                    public void run() {
						if(progressMonitor.isCanceled()) {
							showMessage("Retrieval cancelled. "+getNote());
						} else {
							progressMonitor.close();
							showMessage("The parameter retrieval finished successfully. "+getNote()+" in "+fileNameTextField.getText());
						}
					}
				});
	}
	
	private String getNote() {
	    int linesSaved=parameterFormatter.getLinesSaved();
	    int linesReceived=parameterFormatter.getLinesReceived();
		if(linesReceived!=linesSaved) {
			return linesReceived+" lines received, "+linesSaved+" lines saved";
		} else {
			return linesReceived+" lines received";
		}
	}
	
	public void gotException(final Exception e) {
		final String message;
		e.printStackTrace();
		message="Error when retrieving parameters: "+e;
		showErrorMessage(message);
	}
	
	@Override
    public boolean isCanceled() {
		return progressMonitor.isCanceled();
	}

	

    @Override
    public void connecting(String url) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void connected(String url) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        showErrorMessage("Connection to "+url+" failed: "+exception);
        
    }

    @Override
    public void disconnected() {
        showErrorMessage("Disconnected");
        
    }

    @Override
    public void log(String message) {
        System.err.println(message);
    }

    @Override
    public void exception(Exception e) {
        gotException(e);
        
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(
                new Runnable() {
                    public void run() {
                        new ParameterRetrievalGui(null,null);
                    }
                });
    }
    
    abstract class TextAreaListener implements DocumentListener {
        public void changedUpdate(DocumentEvent e) { updated(); }
        public void insertUpdate(DocumentEvent e) { updated(); }
        public void removeUpdate(DocumentEvent e) { updated(); }
        abstract public void updated();
    }
}