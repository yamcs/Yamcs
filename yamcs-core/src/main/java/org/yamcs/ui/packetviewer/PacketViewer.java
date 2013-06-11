package org.yamcs.ui.packetviewer;

import static org.yamcs.api.Protocol.decode;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.TimeZone;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.jboss.netty.buffer.ChannelBufferInputStream;
import org.yamcs.ConfigurationException;
import org.yamcs.ParameterListener;
import org.yamcs.ParameterValue;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsException;
import org.yamcs.api.ConnectionListener;
import org.yamcs.api.ConnectionParameters;
import org.yamcs.api.Protocol;
import org.yamcs.api.YamcsApiException;
import org.yamcs.api.YamcsClient;
import org.yamcs.api.YamcsConnectData;
import org.yamcs.api.YamcsConnector;
import org.yamcs.archive.PacketWithTime;
import org.yamcs.protobuf.Yamcs.MissionDatabaseRequest;
import org.yamcs.protobuf.Yamcs.TmPacketData;
import org.yamcs.usoctools.XtceUtil;
import org.yamcs.utils.CcsdsPacket;
import org.yamcs.utils.StringConvertors;
import org.yamcs.utils.TimeEncoding;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.MdbMappings;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.XtceDb;
import org.yamcs.xtceproc.XtceDbFactory;
import org.yamcs.xtceproc.XtceTmProcessor;

public class PacketViewer extends JFrame implements ActionListener,
TreeSelectionListener, ParameterListener, ConnectionListener {

    static final ImageIcon ICON_DOWN = new ImageIcon(PacketViewer.class.getResource("/org/yamcs/images/down.png"));
    static final ImageIcon ICON_UP = new ImageIcon(PacketViewer.class.getResource("/org/yamcs/images/up.png"));
    static final ImageIcon ICON_CLOSE = new ImageIcon(PacketViewer.class.getResource("/org/yamcs/images/close.png"));
    static final String hexstring = "0123456789abcdef";
    static final StringBuilder asciiBuf = new StringBuilder(), hexBuf = new StringBuilder();
    static PacketViewer theApp;
    static int maxLines = 1000;
    XtceDb xtcedb;

    File lastFile;
    JSplitPane hexSplit;
    JTextPane hexText;
    StyledDocument hexDoc;
    Style fixedStyle, highlightedStyle;
    JMenuItem miAutoScroll, miAutoSelect;
    JTextArea logText;
    JScrollPane logScrollpane;
    PacketsTable packetsTable;
    ParametersTable parametersTable;
    JTree structureTree;
    DefaultMutableTreeNode structureRoot;
    DefaultTreeModel structureModel;
    JSplitPane mainsplit;
    JPanel findBar;
    JTextField searchField;
    ListPacket currentPacket;
    FileAndDbChooser fileChooser;
    static Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    static final SimpleDateFormat dateTimeFormatFine = new SimpleDateFormat("yyyy.MM.dd/DDD HH:mm:ss.SSS");
    YamcsConnector yconnector;
    YamcsClient yclient;
    ConnectDialog connectDialog;
    static final KeyStroke KEY_CTRL_F = KeyStroke.getKeyStroke(KeyEvent.VK_F, KeyEvent.CTRL_MASK);
    static final KeyStroke KEY_ESC = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0); 

    ConnectionParameters connectionParams;
    XtceUtil xtceutil;
    XtceTmProcessor tmProcessor;
    boolean authenticationEnabled = false;

    public PacketViewer() throws ConfigurationException {
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        YConfiguration config = YConfiguration.getConfiguration("yamcs-ui");
        if(config.containsKey("authenticationEnabled")) {
            authenticationEnabled = config.getBoolean("authenticationEnabled");
        }

        // application menu

        JMenuBar menuBar = new JMenuBar();
        setJMenuBar(menuBar);

        JMenu menu = new JMenu("File");
        menu.setMnemonic(KeyEvent.VK_F);
        menuBar.add(menu);



        JMenuItem menuitem = new JMenuItem("Open File...", KeyEvent.VK_O);
        menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_O, ActionEvent.CTRL_MASK));
        menuitem.setActionCommand("open file");
        menuitem.addActionListener(this);
        menu.add(menuitem);

        menuitem = new JMenuItem("Open Yamcs connection...");
        menuitem.setMnemonic(KeyEvent.VK_C);
        menuitem.setDisplayedMnemonicIndex(11);
        //menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N, ActionEvent.CTRL_MASK));
        menuitem.setActionCommand("connect-yamcs");
        menuitem.addActionListener(this);
        menu.add(menuitem);

        menu.addSeparator();

        /*menuitem = new JMenuItem("Preferences", KeyEvent.VK_COMMA);
		menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_COMMA, ActionEvent.CTRL_MASK));
		menu.add(menuitem);
		menu.addSeparator();*/

        menuitem = new JMenuItem("Quit", KeyEvent.VK_Q);
        menuitem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Q, ActionEvent.CTRL_MASK));
        menuitem.setActionCommand("quit");
        menuitem.addActionListener(this);
        menu.add(menuitem);

        menu = new JMenu("View");
        menu.setMnemonic(KeyEvent.VK_V);
        menuBar.add(menu);

        miAutoScroll = new JCheckBoxMenuItem("Auto-Scroll To Last Packet");
        miAutoScroll.setSelected(true);
        menu.add(miAutoScroll);

        miAutoSelect = new JCheckBoxMenuItem("Auto-Select Last Packet");
        miAutoSelect.setSelected(false);
        menu.add(miAutoSelect);

        menu.addSeparator();

        menuitem = new JMenuItem("Clear", KeyEvent.VK_C);
        menuitem.setActionCommand("clear");
        menuitem.addActionListener(this);
        menu.add(menuitem);
        //
        // window contents
        //

        // table to the left which shows one row per packet

        packetsTable = new PacketsTable(this);
        packetsTable.setMaxLines(1000);
        JScrollPane packetScrollpane = new JScrollPane(packetsTable);

        // table to the right which shows one row per parameter in the selected packet

        parametersTable = new ParametersTable(this);
        JScrollPane tableScrollpane = new JScrollPane(parametersTable);

        // tree to the right which shows the container structure of the selected packet

        structureRoot = new DefaultMutableTreeNode();
        structureModel = new DefaultTreeModel(structureRoot);
        structureTree = new JTree(structureModel);
        structureTree.setEditable(false);
        structureTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        structureTree.addTreeSelectionListener(this);
        JScrollPane treeScrollpane = new JScrollPane(structureTree);

        Insets oldInsets = UIManager.getInsets("TabbedPane.contentBorderInsets");
        UIManager.put("TabbedPane.contentBorderInsets", new Insets(0, 0, 0, 0));

        JTabbedPane tabpane = new JTabbedPane();
        UIManager.put("TabbedPane.contentBorderInsets", oldInsets);
        tabpane.add("Parameters", tableScrollpane);
        tabpane.add("Structure", treeScrollpane);

        searchField = new JTextField(25);
        ImageIconButton downButton = new ImageIconButton(ICON_DOWN);

        ActionListener searchListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String searchTerm = searchField.getText();
                if (searchTerm != null && !searchTerm.trim().equals("")) {
                    parametersTable.nextSearchResult(searchTerm.toLowerCase());
                }
            }
        };

        AbstractAction closeFindBarAction = new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                findBar.setVisible(false);
                parametersTable.clearSearchResults();
                parametersTable.requestFocusInWindow();
            }
        };

        searchField.getInputMap().put(KEY_ESC, "closeFindBar");
        searchField.getActionMap().put("closeFindBar", closeFindBarAction);

        searchField.addActionListener(searchListener);
        downButton.addActionListener(searchListener);

        ImageIconButton upButton = new ImageIconButton(ICON_UP);
        upButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String searchTerm = searchField.getText();
                if (searchTerm != null && !searchTerm.trim().equals("")) {
                    parametersTable.previousSearchResult(searchTerm.toLowerCase());
                }
            }
        });

        searchField.setPreferredSize(downButton.getPreferredSize());

        JPanel findBar_left = new JPanel(new FlowLayout(FlowLayout.LEFT));
        findBar_left.add(new JLabel("Find:"));
        findBar_left.add(searchField);
        findBar_left.add(downButton);
        findBar_left.add(upButton);

        ImageIconButton closeFindBarButton = new ImageIconButton(ICON_CLOSE);
        closeFindBarButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                findBar.setVisible(false);
                parametersTable.clearSearchResults();
                parametersTable.requestFocusInWindow();
            }
        });

        // GridBag, just for the vertical alignment..
        JPanel findBar_right = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = GridBagConstraints.RELATIVE;
        findBar_right.add(closeFindBarButton, gbc);

        findBar = new JPanel(new BorderLayout());
        findBar.add(findBar_left, BorderLayout.CENTER);
        findBar.add(findBar_right, BorderLayout.EAST);
        findBar.setVisible(false);

        JPanel parameterPanel = new JPanel(new BorderLayout());
        parameterPanel.add(tabpane, BorderLayout.CENTER);
        parameterPanel.add(findBar, BorderLayout.SOUTH);

        // hexdump panel

        hexText = new JTextPane() {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return false; // disable line wrap
            }
        };
        hexDoc = hexText.getStyledDocument();
        final Style defStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        fixedStyle = hexDoc.addStyle("fixed", defStyle);
        StyleConstants.setFontFamily(fixedStyle, Font.MONOSPACED);
        highlightedStyle = hexDoc.addStyle("highlighted", fixedStyle);
        StyleConstants.setBackground(highlightedStyle, Color.YELLOW.darker());
        hexText.setEditable(false);

        JScrollPane hexScrollpane = new JScrollPane(hexText);        
        hexScrollpane.getViewport().setBackground(hexText.getBackground());
        hexSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, parameterPanel, hexScrollpane);
        removeBorders(hexSplit);
        hexSplit.setResizeWeight(0.7);

        mainsplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, packetScrollpane, hexSplit);
        removeBorders(mainsplit);
        mainsplit.setResizeWeight(0.0);

        // log text

        logText = new JTextArea(3, 20);
        logText.setEditable(false);
        logScrollpane = new JScrollPane(logText);
        JSplitPane logsplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, mainsplit, logScrollpane);
        removeBorders(logsplit);
        logsplit.setResizeWeight(1.0);
        logsplit.setContinuousLayout(true);

        getContentPane().add(logsplit, BorderLayout.CENTER);

        configureGlobalKeyboardShortcuts();

        clearWindow();
        updateTitle();
        pack();
        setVisible(true);
    }

    private void configureGlobalKeyboardShortcuts() {
        KeyboardFocusManager kfm = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        kfm.addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                KeyStroke keyStroke = KeyStroke.getKeyStrokeForEvent(e);
                if (keyStroke.equals(KEY_CTRL_F)) {
                    findBar.setVisible(true);
                    searchField.selectAll();
                    searchField.requestFocusInWindow();
                    return true;
                }
                return false;
            }
        });
    }

    void updateTitle() {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                StringBuilder title = new StringBuilder("Yamcs Packet Viewer");
                if (connectionParams != null) {
                    title.append(" [").append(connectionParams.getUrl()).append("]");
                } else if (lastFile != null) {
                    title.append(" - ");
                    title.append(lastFile.getName());
                } else {
                    title.append(" (no file loaded)");
                }
                setTitle(title.toString());
            }
        });
    }

    static void debugLogComponent(String name, JComponent c) {
        Insets in = c.getInsets();
        System.out.println("component " + name + ": "
                + "min(" + c.getMinimumSize().width + "," + c.getMinimumSize().height + ") "
                + "pref(" + c.getPreferredSize().width + "," + c.getPreferredSize().height + ") "
                + "max(" + c.getMaximumSize().width + "," + c.getMaximumSize().height + ") "
                + "size(" + c.getSize().width + "," + c.getSize().height + ") "
                + "insets(" + in.top + "," + in.left + "," +in.bottom + "," +in.right + ")");
    }

    @Override
    public void log(final String s) {
        javax.swing.SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                logText.append(s + "\n");
                logScrollpane.getVerticalScrollBar().setValue(logScrollpane.getVerticalScrollBar().getMaximum());
            }
        });
    }

    void showMessage(String msg) {
        JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.PLAIN_MESSAGE);
    }

    void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, getTitle(), JOptionPane.ERROR_MESSAGE);
    }


    @Override
    public void actionPerformed(ActionEvent ae) {
        String cmd = ae.getActionCommand();
        if (cmd.equals("quit")) {
            processWindowEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING));
        } else if (cmd.equals("clear")) {
            clearWindow();
        } else if (cmd.equals("open file")) {
            if(fileChooser==null) {
                try {
                    fileChooser = new FileAndDbChooser();
                } catch (ConfigurationException e) {
                    showError("Cannot load local mdb config: "+e.getMessage());
                    return;
                }
            }
            int returnVal = fileChooser.showDialog(this);
            if (returnVal == FileAndDbChooser.APPROVE_OPTION) {
                disconnect();
                lastFile = new File(fileChooser.getSelectedFile());
                if(loadLocalXtcedb(fileChooser.getSelectedDbConfig())) {
                    loadFile();
                }
            }
        } else if (cmd.equals("connect-yamcs")) {
            if(connectDialog==null) connectDialog=new ConnectDialog(this, authenticationEnabled, true, true, true);
            int ret=connectDialog.showDialog();
            if(ret==ConnectDialog.APPROVE_OPTION) {
                connectYamcs(connectDialog.getConnectData());
            }
        }
    }

    private static class ShortReadException extends Exception{
        public ShortReadException(long needed,  long read, long offset) {
            super();
            this.needed = needed;
            this.offset = offset;
            this.read = read;
        }
        long needed;
        long read;
        long offset;
        @Override
        public String toString() {
            return String.format("short seek %d/%d at offset %d", read, needed, offset);
        }
    }

    private boolean loadLocalXtcedb(String configName) {
        if(tmProcessor!=null) tmProcessor.stop();
        log("Loading local XTCE db "+configName);
        try {
            xtcedb=XtceDbFactory.getInstanceByConfig(configName);
        } catch (ConfigurationException e) {
            showError(e.getMessage());
            return false;
        }

        xtceutil=XtceUtil.getInstance(xtcedb);

        tmProcessor=new XtceTmProcessor(xtcedb);
        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.start();
        log("Loaded "+xtcedb.getSequenceContainers().size()+" sequence containers and "+xtcedb.getParameterNames().size()+" parameters");
        return true;
    }


    private boolean loadRemoteXtcedb(String configName) {
        if(tmProcessor!=null) tmProcessor.stop();
        log("Loading remote XTCE db "+configName);
        MissionDatabaseRequest mdr = MissionDatabaseRequest.newBuilder().setDbConfigName(configName).build();
        try {
            YamcsClient yc=yconnector.getSession().newClientBuilder().setRpc(true).setDataConsumer(null, null).build();
            yc.executeRpc(Protocol.YAMCS_SERVER_CONTROL_ADDRESS, "getMissionDatabase", mdr, null);
            ClientMessage msg=yc.dataConsumer.receive(5000);
            ObjectInputStream ois=new ObjectInputStream(new ChannelBufferInputStream(msg.getBodyBuffer().channelBuffer()));
            Object o=ois.readObject();
            xtcedb=(XtceDb) o;
            yc.close();
        } catch (Exception e) {
            showError(e.getMessage());
            return false;
        }

        xtceutil=XtceUtil.getInstance(xtcedb);

        tmProcessor=new XtceTmProcessor(xtcedb);
        tmProcessor.setParameterListener(this);
        tmProcessor.startProvidingAll();
        tmProcessor.start();

        log("Loaded "+xtcedb.getSequenceContainers().size()+" sequence containers and "+xtcedb.getParameterNames().size()+" parameters");
        return true;
    }


    void loadFile() {
        new SwingWorker<Void, ListPacket>() {
            ProgressMonitor progress;
            @Override
            protected Void doInBackground() throws Exception {
                boolean isPacts=false;
                long r;

                try {
                    FileInputStream reader = new FileInputStream(lastFile);
                    byte[] fourb = new byte[4];
                    ListPacket ccsds;
                    ByteBuffer buf;
                    int res;
                    long len, offset = 0;

                    clearWindow();
                    progress = new ProgressMonitor(theApp,
                            String.format("Loading %s", lastFile.getName()),
                            null, 0, (int)(lastFile.length()>>10));

                    while (!progress.isCanceled()) {
                        res = reader.read(fourb, 0, 4);
                        if (res != 4) break;
                        buf = ByteBuffer.allocate(16);
                        if((fourb[2]==0) && (fourb[3]==0)) { //hrdp packet - first 4 bytes are packet size in little endian
                            if((r=reader.skip(6))!=6) throw new ShortReadException(6, r, offset);
                            offset+=10;
                            if((r=reader.read(buf.array()))!=16) throw new ShortReadException(16, r, offset);
                        } else if ((fourb[0] & 0xe8) == 0x08) {// CCSDS packet
                            buf.put(fourb, 0, 4);
                            if((r=reader.read(buf.array(),4,12))!=12) throw new ShortReadException(16, r, offset);
                        } else {//pacts packet
                            isPacts=true;
                            //System.out.println("pacts packet");
                            // read ASCII header up to the second blank
                            int i, j;
                            StringBuffer hdr = new StringBuffer();
                            j = 0;
                            for(i=0;i<4;i++) {
                                hdr.append((char)fourb[i]);
                                if ( fourb[i] == 32 ) ++j;
                            }
                            offset+=4;
                            while((j < 2) && (i < 20)) {
                                int c = reader.read();
                                if(c==-1)throw new ShortReadException(1, 0, offset);
                                offset++;
                                hdr.append((char)c);
                                if ( c == 32 ) ++j;
                                i++;
                            }
                            if((r=reader.read(buf.array()))!=16) throw new ShortReadException(16,r,offset);
                        }
                        ccsds = new ListPacket(buf, offset);
                        len = ccsds.getCccsdsPacketLength() + 7;
                        r = reader.skip(len - 16);
                        if (r != len - 16) throw new ShortReadException(len-16, r, offset);
                        offset += len;
                        if(isPacts) {
                            if(reader.skip(1)!=1) throw new ShortReadException(1, 0, offset);
                            offset+=1;
                        }
                        publish(ccsds);

                        progress.setProgress((int)(offset>>10));
                    }
                    reader.close();
                } catch (Exception x) {
                    final String msg = String.format("Error while loading %s: %s", lastFile.getName(), x.getMessage());
                    log(msg);
                    showError(msg);
                    clearWindow();
                    lastFile = null;
                }
                return null;
            }

            @Override
            protected void process(final List<ListPacket> chunks) {
                for (ListPacket ccsds:chunks) {
                    packetsTable.addRow(new Object[] {
                            TimeEncoding.toCombinedFormat(ccsds.getInstant()),
                            ccsds.getAPID(),
                            ccsds,
                            ccsds.getCccsdsPacketLength() + 7
                    });
                }
            }

            @Override
            protected void done() {
                if (progress != null) {
                    if (progress.isCanceled()) {
                        clearWindow();
                        log(String.format("Cancelled loading %s", lastFile.getName()));
                    } else {
                        log(String.format("Loaded %d packets from \"%s\".", packetsTable.getRowCount(), lastFile.getPath()));
                    }
                    progress.close();
                }
                updateTitle();
            }
        }.execute();
    }

    void clearWindow() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                packetsTable.clear();
                parametersTable.clear();
                packetsTable.revalidate();
                parametersTable.revalidate();
                structureRoot.removeAllChildren();
                structureTree.setRootVisible(false);
            }
        });
    }

    void highlightBitRanges(Range[] highlightBits) {
        final int linesize = 5 + 5 * 8 + 16 + 1;
        int n, tmp, textoffset, binHighStart, binHighStop, ascHighStart, ascHighStop;

        hexDoc.setCharacterAttributes(0, hexDoc.getLength(), fixedStyle, true); // reset styles throughout the document

        // apply style for highlighted parts

        for (Range bitRange:highlightBits) {
            if (bitRange == null) continue;
            final int highlightStartNibble = bitRange.offset / 4;
            final int highlightStopNibble = (bitRange.offset + bitRange.size + 3) / 4;
            for (n = highlightStartNibble / 32 * 32 ; n < highlightStopNibble; n += 32) {

                binHighStart = 5;
                ascHighStart = 5 + 5 * 8;
                tmp = highlightStartNibble - n;
                if (tmp > 0) {
                    binHighStart += tmp + (tmp / 4);
                    ascHighStart += tmp / 2;
                }

                binHighStop = 5 + 5 * 8 - 1;
                ascHighStop = 5 + 5 * 8 + 16;
                tmp = n + 32 - highlightStopNibble;
                if (tmp > 0) {
                    binHighStop -= tmp + (tmp / 4);
                    ascHighStop -= tmp / 2;
                }

                textoffset = linesize * (n / 32);
                //System.out.println(String.format("setCharacterAttributes %d/%d %d %d %d/%d %d/%d",
                //  highlightStartNibble, highlightStopNibble, n, textoffset, binHighStart, binHighStop, ascHighStart, ascHighStop));
                hexDoc.setCharacterAttributes(textoffset + binHighStart, binHighStop - binHighStart, highlightedStyle, true);
                hexDoc.setCharacterAttributes(textoffset + ascHighStart, ascHighStop - ascHighStart, highlightedStyle, true);
            }
        }

        // put the caret into the position of the first item (caret makes itself visible by default)
        final int hexScrollPos = (highlightBits.length == 0 || highlightBits[0] == null) ? 0 : (linesize * (highlightBits[0].offset / 128));
        hexText.setCaretPosition(hexScrollPos);
    }


    void connectYamcs(YamcsConnectData ycd) {
        disconnect();
        connectionParams=ycd;
        yconnector=new YamcsConnector();
        yconnector.addConnectionListener(this);
        yconnector.connect(ycd);
        updateTitle();
    }


    void disconnect() {
        connectionParams = null;
        updateTitle();
    }

    @Override
    public void valueChanged(TreeSelectionEvent e)	{
        TreePath[] paths = structureTree.getSelectionPaths();
        Range[] bits=null;
        if(paths==null) {
            bits=new Range[0];
        } else {
            bits = new Range[paths.length];
            for (int i = 0; i < paths.length; ++i) {
                Object last = paths[i].getLastPathComponent();
                if (last instanceof TreeEntry) {
                    TreeEntry te = (TreeEntry)last;
                    bits[i] = new Range(te.bitOffset, te.bitSize);
                } else {
                    bits[i] = null;
                }
            }
        }
        highlightBitRanges(bits);
    }

    @Override
    public void update(final Collection<ParameterValue> params) {
        SwingUtilities.invokeLater(new Runnable() {
            Hashtable<String,TreeContainer> containers = new Hashtable<String,TreeContainer>();
            DefaultMutableTreeNode getTreeNode(SequenceContainer sc) {
                if (sc.getBaseContainer() == null) {
                    return structureRoot;
                }
                TreeContainer tc = containers.get(sc.getOpsName());
                if (tc == null) {
                    tc = new TreeContainer(sc);
                    containers.put(sc.getOpsName(), tc);
                }
                getTreeNode(sc.getBaseContainer()).add(tc);
                return tc;
            }
            @Override
            public void run() {
                String[] vec = new String[parametersTable.getColumnCount()];
                DataEncoding encoding;
                Calibrator calib;
                Object paramtype;
                String name;

                parametersTable.clear();
                structureRoot.removeAllChildren();

                for (ParameterValue value:params) {

                    // add new leaf to the structure tree
                    // parameters become leaves, and sequence containers become nodes recursively

                    name = value.getParameter().getOpsName();
                    getTreeNode(value.getParameterEntry().getSequenceContainer()).add(new TreeEntry(value));

                    // add new row for parameter table

                    vec[0] = name;
                    vec[1] = StringConvertors.toString(value.getEngValue(), false);
                    vec[2] = StringConvertors.toString(value.getRawValue(), false);

                    vec[3] = value.getWarningRange() == null ? "" : Double.toString(value.getWarningRange().getMinInclusive());
                    vec[4] = value.getWarningRange() == null ? "" : Double.toString(value.getWarningRange().getMaxInclusive());;


                    vec[5] = value.getCriticalRange() == null ? "" : Double.toString(value.getCriticalRange().getMinInclusive());
                    vec[6] = value.getCriticalRange() == null ? "" : Double.toString(value.getCriticalRange().getMaxInclusive());
                    vec[7] = String.valueOf(value.getAbsoluteBitOffset());
                    vec[8] = String.valueOf(value.getBitSize());

                    paramtype = value.getParameter().getParameterType();
                    if (paramtype instanceof EnumeratedParameterType) {
                        vec[9] = ((EnumeratedParameterType)paramtype).getCalibrationDescription();
                    } else if (paramtype instanceof BaseDataType) {
                        encoding = ((BaseDataType)paramtype).getEncoding();
                        calib = null;
                        if (encoding instanceof IntegerDataEncoding) {
                            calib = ((IntegerDataEncoding) encoding).getDefaultCalibrator();
                        } else if (encoding instanceof FloatDataEncoding) {
                            calib = ((FloatDataEncoding) encoding).getDefaultCalibrator();
                        }
                        vec[9] = calib == null ? "IDENTICAL" : calib.toString();
                    }

                    parametersTable.addRow(vec);
                }

                structureRoot.setUserObject(currentPacket);
                structureModel.nodeStructureChanged(structureRoot);
                structureTree.setRootVisible(true);

                // expand all nodes
                for (TreeContainer tc:containers.values()) {
                    structureTree.expandPath(new TreePath(tc.getPath()));
                }

                // build hexdump text
                currentPacket.hexdump();
                hexText.setCaretPosition(0);
            }
        });
    }

    public void setSelectedPacket(ListPacket listPacket) {
        currentPacket = listPacket;
        try {
            currentPacket.load();
            ByteBuffer bb=currentPacket.getByteBuffer();
            tmProcessor.processPacket(new PacketWithTime(TimeEncoding.currentInstant(), CcsdsPacket.getInstant(bb), currentPacket.getByteBuffer()));
        } catch (IOException x) {
            final String msg = String.format("Error while loading %s: %s", lastFile.getName(), x.getMessage());
            log(msg);
            showError(msg);
        }
    }

    class TreeContainer extends DefaultMutableTreeNode {
        TreeContainer(SequenceContainer sc) {
            super(sc.getOpsName(), true);
        }
    }

    class TreeEntry extends DefaultMutableTreeNode {
        int bitOffset, bitSize;
        TreeEntry(ParameterValue value) {
            super(String.format("%d/%d %s", value.getAbsoluteBitOffset(), value.getBitSize(), value.getParameter().getOpsName()), false);
            bitOffset = value.getAbsoluteBitOffset();
            bitSize = value.getBitSize();
        }
    }

    protected class Range {
        int offset, size;
        Range(int offset, int size) {
            this.offset = offset;
            this.size = size;
        }
    }

    class ListPacket extends CcsdsPacket {
        String opsname;
        long fileOffset;

        ListPacket(ByteBuffer bb) {
            this(bb, -1);
        }

        ListPacket(ByteBuffer bb, long fileOffset) {
            super(bb);
            this.fileOffset = fileOffset;
            opsname = xtceutil.getPacketNameByApidPacketid(getAPID(), getPacketID(), MdbMappings.MDB_OPSNAME);
            if(opsname==null) opsname = xtceutil.getPacketNameByPacketId(getPacketID(), MdbMappings.MDB_OPSNAME);
            if (opsname == null) opsname = String.format("Packet ID %d", getPacketID());
        }

        @Override
        public String toString() {
            return opsname;
        }

        void load() throws IOException {
            if (bb.capacity() == 16) {
                FileInputStream reader = new FileInputStream(lastFile);
                int len = getCccsdsPacketLength() + 7;
                byte[] data = new byte[len];
                reader.skip(fileOffset + 16);
                int res = reader.read(data, 16, len - 16);
                if(res!=len-16) throw new IOException("short read, expected "+(len-16)+", got "+res);
                reader.close();
                bb.rewind();
                bb.get(data, 0, 16);
                bb = ByteBuffer.wrap(data);
            }
        }

        void hexdump() {
            try {
                byte b;
                char c;
                int i, j;

                hexDoc.remove(0, hexDoc.getLength());

                for (i = 0; i < bb.capacity();) {

                    // build one row of hexdump: offset, hex bytes, ascii bytes

                    asciiBuf.setLength(0);
                    hexBuf.setLength(0);
                    hexBuf.append(hexstring.charAt(i>>12));
                    hexBuf.append(hexstring.charAt((i>>8) & 0x0f));
                    hexBuf.append(hexstring.charAt((i>>4) & 0x0f));
                    hexBuf.append(hexstring.charAt(i & 0x0f));
                    hexBuf.append(' ');

                    for (j = 0; j < 16; ++j, ++i) {
                        if (i < bb.capacity()) {
                            b = bb.get(i);
                            hexBuf.append(hexstring.charAt((b>>4) & 0x0f));
                            hexBuf.append(hexstring.charAt(b & 0x0f));
                            if ((j & 1) == 1) hexBuf.append(' ');
                            c = (b < 32) || (b > 126) ? '.' : (char)b;
                            asciiBuf.append(c);
                        } else {
                            hexBuf.append((j & 1) == 1 ? "   " : "  ");
                            asciiBuf.append(' ');
                        }
                    }

                    hexBuf.append(asciiBuf);
                    hexBuf.append('\n');
                    hexDoc.insertString(hexDoc.getLength(), hexBuf.toString(), fixedStyle);
                }

            } catch (BadLocationException x) {
                System.out.println("cannot format hexdump of "+opsname+": "+x.getMessage());
            }
        }
    }

    @Override
    public void connected(String url) {
        YamcsConnectData ycd=yconnector.getConnectionParams();
        try {
            log("connected to "+url);
            if(connectDialog.getUseServerMdb()) {
                if(!loadRemoteXtcedb(connectDialog.getServerMdbConfig())) return;
            } else {
                if(!loadLocalXtcedb(connectDialog.getLocalMdbConfig())) return;
            }
            yclient=yconnector.getSession().newClientBuilder().setRpc(false).
                    setDataConsumer(Protocol.getPacketRealtimeAddress(ycd.instance), null).build();
            yclient.dataConsumer.setMessageHandler(new MessageHandler() {
                @Override
                public void onMessage(ClientMessage msg) {
                    TmPacketData data;
                    try {
                        data = (TmPacketData)decode(msg, TmPacketData.newBuilder());
                        packetsTable.packetReceived(new CcsdsPacket(data.getPacket().asReadOnlyByteBuffer()));
                    } catch (YamcsApiException e) {
                        log(e.toString());
                        e.printStackTrace();
                    }
                }
            });
        } catch (HornetQException e) {
            log(e.toString());
            e.printStackTrace();
        }

    }

    @Override
    public void connecting(String url) {
        log("connecting to "+url);

    }

    @Override
    public void connectionFailed(String url, YamcsException exception) {
        log("connection to "+url+" failed: "+exception);
    }

    @Override
    public void disconnected() {
        log("disconnected");
    }

    private void removeBorders(JSplitPane splitPane) {
        SplitPaneUI ui = splitPane.getUI();
        if(ui instanceof BasicSplitPaneUI) { // We don't want to mess with other L&Fs
            ((BasicSplitPaneUI)ui).getDivider().setBorder(null);
            splitPane.setBorder(BorderFactory.createEmptyBorder());
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: packetviewer.sh [-h] [-l n] [url]");
        System.err.println("-h:\tShow this help text");
        System.err.println("-l:\tMaximum number of packet lines to keep (only for realtime connection), default 1000");
        System.err.println("url:\tConnect at startup to the given url");
        System.err.println("Example:\n\tpacketviewer.sh yamcs://localhost:5445/yops");
        System.exit(1);
    }

    public static void main(String[] args) throws ConfigurationException, URISyntaxException {
        String initialUrl = null;

        try {
            for(int i=0;i<args.length;i++) {
                if(args[i].startsWith("yamcs://")) {
                    initialUrl=args[i];
                } else if("-l".equals(args[i])) {
                    if(i+1==args.length) printUsageAndExit();
                    maxLines=Integer.valueOf(args[++i]);
                } else if(args[i].equals("-h")) {
                    printUsageAndExit();
                } else {
                    printUsageAndExit();
                }
            } 
        } catch (NumberFormatException e) {
            System.err.println("Illegal number: "+e.getMessage());
            printUsageAndExit();
        }

        YConfiguration.setup();
        theApp = new PacketViewer();

        if (initialUrl != null) {
            YamcsConnectData ycd=YamcsConnectData.parse(initialUrl);
            theApp.connectYamcs(ycd);
        }
    }
}
