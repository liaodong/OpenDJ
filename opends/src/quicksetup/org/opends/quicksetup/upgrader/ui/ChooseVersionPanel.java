/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.upgrader.ui;

import org.opends.quicksetup.UserData;
import org.opends.quicksetup.ui.CustomHTMLEditorKit;
import org.opends.quicksetup.ui.FieldName;
import org.opends.quicksetup.ui.GuiApplication;
import org.opends.quicksetup.ui.QuickSetupStepPanel;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.quicksetup.ui.WebProxyDialog;
import org.opends.quicksetup.upgrader.Build;
import org.opends.quicksetup.upgrader.RemoteBuildManager;
import org.opends.quicksetup.upgrader.Upgrader;
import org.opends.quicksetup.util.BackgroundTask;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This panel allows the user to select a remote or local build for upgrade.
 */
public class ChooseVersionPanel extends QuickSetupStepPanel {

  static private final Logger LOG =
          Logger.getLogger(ChooseVersionPanel.class.getName());

  static private final long serialVersionUID = -6941309163077121917L;

  private JRadioButton rbRemote = null;
  private JRadioButton rbLocal = null;
  private ButtonGroup grpRemoteLocal = null;
  private JComboBox cboBuild = null;
  private JTextField tfFile = null;
  private boolean loadBuildListAttempted = false;

  /**
   * Creates an instance.
   *
   * @param application this panel represents.
   */
  public ChooseVersionPanel(GuiApplication application) {
    super(application);
  }

  /**
   * {@inheritDoc}
   */
  public void beginDisplay(UserData data) {
    super.beginDisplay(data);
    if (!loadBuildListAttempted) {
      loadBuildList();
    }
  }

  /**
   * {@inheritDoc}
   */
  public Object getFieldValue(FieldName fieldName) {
    Object value = null;
    if (FieldName.UPGRADE_DOWNLOAD.equals(fieldName)) {
      value = new Boolean(rbRemote.isSelected());
    } else if (FieldName.UPGRADE_BUILD_TO_DOWNLOAD.equals(fieldName)) {
      value = cboBuild.getSelectedItem();
    } else if (FieldName.UPGRADE_FILE.equals(fieldName)) {
      value = new File(tfFile.getText());
    }
    return value;
  }

  /**
   * {@inheritDoc}
   */
  protected Component createInputPanel() {
    Component c;

    JPanel p = UIFactory.makeJPanel();

    rbRemote = UIFactory.makeJRadioButton(
            getMsg("upgrade-choose-version-remote-label"),
            getMsg("upgrade-choose-version-remote-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    rbLocal = UIFactory.makeJRadioButton(
            getMsg("upgrade-choose-version-local-label"),
            getMsg("upgrade-choose-version-local-tooltip"),
            UIFactory.TextStyle.SECONDARY_FIELD_VALID);

    grpRemoteLocal = new ButtonGroup();
    grpRemoteLocal.add(rbRemote);
    grpRemoteLocal.add(rbLocal);
    grpRemoteLocal.setSelected(rbRemote.getModel(), true);

    cboBuild = UIFactory.makeJComboBox();
    cboBuild.setEditable(false);

    // TODO: use UIFactory
    tfFile = new JTextField();
    tfFile.setColumns(20);

    JPanel pnlBrowse = Utilities.createBrowseButtonPanel(
            UIFactory.makeJLabel(null,
                    getMsg("upgrade-choose-version-local-path"),
                    UIFactory.TextStyle.SECONDARY_FIELD_VALID),
            tfFile,
            UIFactory.makeJButton(getMsg("browse-button-label"),
                    getMsg("browse-button-tooltip")));

    p.setLayout(new GridBagLayout());
    // p.setBorder(BorderFactory.createLineBorder(Color.RED));
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = GridBagConstraints.REMAINDER;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = 15; // non-standard but looks better
    gbc.anchor = GridBagConstraints.FIRST_LINE_START;
    p.add(rbRemote, gbc);

    gbc.gridy = 1;
    gbc.gridwidth = 1;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 2;
    p.add(cboBuild, gbc);

    gbc.gridy = 1;
    gbc.gridx = 1;
    gbc.weightx = 1.5;
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = UIFactory.getEmptyInsets();
    JPanel fill = UIFactory.makeJPanel();
    // fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(fill, gbc);

    gbc.gridy = 2;
    gbc.gridx = 0;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = 15; // UIFactory.TOP_INSET_RADIOBUTTON;
    p.add(rbLocal, gbc);

    gbc.gridy = 3;
    gbc.insets = UIFactory.getEmptyInsets();
    gbc.insets.top = UIFactory.TOP_INSET_RADIO_SUBORDINATE;
    gbc.insets.left = UIFactory.LEFT_INSET_RADIO_SUBORDINATE;
    p.add(pnlBrowse, gbc);

    gbc.gridy = 4;
    gbc.weighty = 1.0;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.anchor = GridBagConstraints.LINE_START;
    JPanel fill2 = UIFactory.makeJPanel();
    //fill.setBorder(BorderFactory.createLineBorder(Color.BLUE));
    p.add(fill2, gbc);

    c = p;
    return c;
  }

  /**
   * {@inheritDoc}
   */
  protected String getTitle() {
    return getMsg("upgrade-choose-version-panel-title");
  }

  /**
   * {@inheritDoc}
   */
  protected String getInstructions() {
    return getMsg("upgrade-choose-version-panel-instructions");
  }

  private void loadBuildList() {
    RemoteBuildListComboBoxModelCreator bld =
            new RemoteBuildListComboBoxModelCreator();
    bld.startBackgroundTask();
  }

  private void specifyProxy(final Component parent) {
    Runnable proxySpecifier = new Runnable() {
      public void run() {
        String host = null;
        Integer port = null;
        RemoteBuildManager rbm =
                ((Upgrader) getApplication()).getRemoteBuildManager();
        Proxy proxy = rbm.getProxy();
        if (proxy != null) {
          SocketAddress address = proxy.address();
          if (address instanceof InetSocketAddress) {
            host = ((InetSocketAddress) address).getHostName();
            port = ((InetSocketAddress) address).getPort();
          }
        }
        String user = rbm.getProxyUserName();
        char[] pw = rbm.getProxyPassword();
        WebProxyDialog dlg;
        if (parent instanceof Dialog) {
          dlg = new WebProxyDialog((Dialog) parent, host, port, user, pw);
        } else if (parent instanceof Frame) {
          dlg = new WebProxyDialog((Frame) parent, host, port, user, pw);
        } else {
          dlg = new WebProxyDialog((Frame) null, host, port, user, pw);
        }
        dlg.setVisible(true);
        SocketAddress address = dlg.getSocketAddress();
        if (address != null) {
          proxy = new Proxy(Proxy.Type.HTTP, address);
          rbm.setProxy(proxy);
          rbm.setProxyUserName(dlg.getUserName());
          rbm.setProxyPassword(dlg.getPassword());
        }
      }
    };
    if (SwingUtilities.isEventDispatchThread()) {
      proxySpecifier.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(proxySpecifier);
      } catch (InterruptedException e) {
        LOG.log(Level.INFO, "error", e);
      } catch (InvocationTargetException e) {
        LOG.log(Level.INFO, "error", e);
      } catch (Throwable t) {
        LOG.log(Level.INFO, "error", t);
      }
    }
  }

  /**
   * Renders the combo box when there has been an error downloading
   * the build information.
   */
  private class BuildListErrorComboBoxRenderer extends JLabel
          implements ListCellRenderer {

    /**
     * The serial version identifier required to satisfy the compiler because
     * this * class extends a class that implements the
     * {@code java.io.Serializable} interface.  This value was generated using
     * the {@code serialver} command-line utility included with the Java SDK.
     */
    private static final long serialVersionUID = -7075573664472711599L;

    /**
     * Creates a default instance.
     */
    public BuildListErrorComboBoxRenderer() {
      super("Error accessing build information",
              UIFactory.getImageIcon(UIFactory.IconType.ERROR),
              SwingConstants.LEFT);
      UIFactory.setTextStyle(this, UIFactory.TextStyle.SECONDARY_FIELD_INVALID);
      setOpaque(true);
      setBackground(Color.WHITE);
    }

    /**
     * {@inheritDoc}
     */
    public Component getListCellRendererComponent(JList jList,
                                                  Object object,
                                                  int i,
                                                  boolean b,
                                                  boolean b1) {
      return this;
    }
  }

  /**
   * This panel represents the big error message the pops up when the
   * panel can't download the build information.
   */
  private class BuildListDownloadErrorPanel extends JPanel {

    private RemoteBuildManager rbm = null;
    private Throwable reason = null;

    /**
     * The serial version identifier required to satisfy the compiler because
     * this * class extends a class that implements the
     * {@code java.io.Serializable} interface.  This value was generated using
     * the {@code serialver} command-line utility included with the Java SDK.
     */
    private static final long serialVersionUID = -5606673656068527646L;

    /**
     * Creates an instance.
     * @param rbm RemoteBuildManager that is having trouble.
     */
    public BuildListDownloadErrorPanel(RemoteBuildManager rbm,
                                       Throwable reason) {
      this.rbm = rbm;
      this.reason = reason;
      layoutPanel();
    }

    private void layoutPanel() {
      setLayout(new GridBagLayout());

      String proxyString = "None";
      Proxy proxy = rbm.getProxy();
      if (proxy != null) {
        SocketAddress addr = proxy.address();
        proxyString = addr.toString();
      }

      String baseContext = "Unspecified";
      URL url = rbm.getBaseContext();
      if (url != null) {
        baseContext = url.toString();
      }

      String html = getMsg("upgrade-choose-version-build-list-error",
              new String[]{
                      baseContext,
                      reason.getLocalizedMessage(),
                      proxyString});

      /* This helps with debugger the HTML rendering
      StringBuffer content = new StringBuffer();
      try {
        FileInputStream fis = new FileInputStream("/tmp/error-html");
        BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
        String line = null;
        while (null != (line = reader.readLine())) {
          content.append(line);
        }
        html = content.toString();
      } catch (IOException e) {
        e.printStackTrace();
      }
      */

      CustomHTMLEditorKit ek = new CustomHTMLEditorKit();
      ek.addActionListener(new ActionListener() {
        public void actionPerformed(ActionEvent ev) {
          specifyProxy(getParent());

          // Since the proxy info may change we need
          // to regenerate the text
          removeAll();
          layoutPanel();
          repaint();
          validate();
        }
      });
      add(UIFactory.makeHtmlPane(html, ek, UIFactory.INSTRUCTIONS_FONT));
    }

  }

  /**
   * Uses the remote build manager is a separate thread to create
   * and populate the combo box model with build information.  Contains
   * the loop and dialog prompting that happens if there is a problem
   * accessing the remote build repository.
   */
  private class RemoteBuildListComboBoxModelCreator
          extends BackgroundTask<java.util.List<Build>> {

    private RemoteBuildManager rbm = null;

    /**
     * {@inheritDoc}
     */
    public java.util.List<Build> processBackgroundTask() throws Exception {
      rbm = ((Upgrader)getApplication()).getRemoteBuildManager();
      return rbm.listBuilds(getMainWindow(), "Loading build information");
    }

    /**
     * {@inheritDoc}
     */
    public void backgroundTaskCompleted(java.util.List<Build> buildList,
                                        Throwable throwable) {
      ComboBoxModel cbm = null;
      if (throwable == null) {
        cbm = new DefaultComboBoxModel(buildList.toArray());
      } else {
        try {
        String[] options = { "Retry", "Close" };
        int i = JOptionPane.showOptionDialog(getMainWindow(),
                new BuildListDownloadErrorPanel(rbm, throwable),
                "Network Error",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                null);
        if (i == JOptionPane.NO_OPTION ||
                i == JOptionPane.CLOSED_OPTION) {
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              cboBuild.setRenderer(new BuildListErrorComboBoxRenderer());
              // Disable the remote widgets
              cboBuild.setEnabled(false);
              rbLocal.setSelected(true);
              rbRemote.setSelected(false);
              // grpRemoteLocal.setSelected(rbRemote.getModel(), false);
              rbRemote.setEnabled(false);
            }
          });
        } else {
          loadBuildList();
        }
        } catch (Throwable t) {
          t.printStackTrace();
        }
      }
      final ComboBoxModel cbmFinal = cbm;
      if (cbm != null) {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            loadBuildListAttempted = true;
            cboBuild.setModel(cbmFinal);
            cboBuild.setRenderer(new DefaultListCellRenderer());
            // Disable the remote widgets
            cboBuild.setEnabled(true);
            rbLocal.setSelected(false);
            rbRemote.setSelected(true);
            // grpRemoteLocal.setSelected(rbRemote.getModel(), false);
            rbRemote.setEnabled(true);
          }
        });
      }
    }
  }

}
