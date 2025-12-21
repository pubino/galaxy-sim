package astro.galaxy;

import java.awt.*;
import java.awt.event.*;
// import java.awt.image.*;
// import java.util.*;
// import java.io.*;
import java.net.URI;
// import java.text.*;
import javax.swing.*;

import myutil.*;

public class Galaxy extends JPanel implements SimApp, ActionListener, WindowListener,
						ItemListener
{	
        JTextField ROTRATE;
	JTextField DT;
	JTextField WARP;
	JTextField N;

	JButton START;
	JButton PAUSEPLAY;
	JButton TRAIL;
	JButton APPLY;
	JComboBox SELECTION;
	JComboBox INTEGRATOR;

	// Menu item reference for syncing with button
	CheckboxMenuItem menuTrailsItem;

	JPanel gp;
	JPanel tp;
	JPanel bp;
	GalaxyCanvas gc;

	double rotrate;
	double dt;
	int warp, n;
	int integrator;
	final int ANIMATE=0;
	final int TRAPNEWT=1;
	final int MIDPOINT=2;
	final int RUNGEKUTTA=3;
	final int HERMITE=4;
	final int EULER=5;

        public static void main(String[] args) {
                // Enable macOS screen menu bar
                System.setProperty("apple.laf.useScreenMenuBar", "true");
                System.setProperty("apple.awt.application.name", "Galaxy Simulator");

                Galaxy galaxy = new Galaxy();
                AppletFrame frame = new AppletFrame(galaxy, 1000, 1000);

                // Create macOS menu bar
                galaxy.setupMenuBar(frame);
        }

	public void init()
	{	
	    int j;

	    setFont(new Font("Courier", Font.BOLD, 12));
	    setBackground(Color.white);

	    ROTRATE = new JTextField(6);
	    DT = new JTextField(6);
	    WARP = new JTextField(6);
	    N = new JTextField(6);
	    ROTRATE.setText("0.0");
	    DT.setText("1.0e-6");
	    WARP.setText("500");
	    N.setText("7");

	    START   = new JButton("Start");
	    PAUSEPLAY = new JButton("Pause");
	    TRAIL = new JButton("Show Trails");
	    APPLY = new JButton("Apply");
	    SELECTION = new JComboBox();
	    SELECTION.addItem("--Select One--");
	    SELECTION.addItem("L1-L5 M/m=2");
	    SELECTION.addItem("L1-L5 M/m=40");
	    SELECTION.addItem("Lagrange2");
	    SELECTION.addItem("Lagrange3");
	    SELECTION.addItem("Lagrange3a");
	    SELECTION.addItem("FigureEight3");
	    //SELECTION.addItem("BorderCollie3");
	    SELECTION.addItem("Ducati3");
	    SELECTION.addItem("Hill15");
	    SELECTION.addItem("DoubleDouble5");
	    SELECTION.addItem("DoubleDouble10");
	    SELECTION.addItem("DoubleDouble20");
	    SELECTION.addItem("OrthQuasiEllipse4");
	    SELECTION.addItem("Rosette4");
	    SELECTION.addItem("Braid4");
	    SELECTION.addItem("Trefoil4");
	    SELECTION.addItem("FigureEight4");
	    SELECTION.addItem("FoldedTriLoop4");
	    SELECTION.addItem("PlateSaucer4");
	    SELECTION.addItem("BorderCollie4");
	    SELECTION.addItem("Trefoil5");
	    SELECTION.addItem("FigureEight5");
	    SELECTION.addItem("FigureEight21");
	    SELECTION.addItem("Lagrange20");
	    SELECTION.addItem("LagrangeN");
	    SELECTION.addItem("LagrangeNa");
	    SELECTION.addItem("Saturn");
	    SELECTION.addItem("Saturn2");
	    SELECTION.addItem("Ducati3_2");
	    SELECTION.addItem("Ducati3_0.5");
	    SELECTION.addItem("Ducati3_0.1");
	    SELECTION.addItem("Ducati3_10");
	    SELECTION.addItem("Ducati3_1.2");
	    SELECTION.addItem("Ducati3_1.3");
	    SELECTION.addItem("Ducati3_alluneq");
	    SELECTION.addItem("Ducati3_alluneq2");
	    //SELECTION.addItem("Test1");
	    SELECTION.addItem("Hill3_2");
	    SELECTION.addItem("Hill3_3");
	    SELECTION.addItem("Hill3_0.5");
	    SELECTION.addItem("L5");
	    SELECTION.addItem("Test2");
	    SELECTION.addItem("Test3");
	    SELECTION.addItem("Test");

	    INTEGRATOR = new JComboBox();
	    INTEGRATOR.addItem("Hermite");
	    INTEGRATOR.addItem("Midpoint (Leapfrog)");
	    INTEGRATOR.addItem("Euler");
	    INTEGRATOR.addItem("RungeKutta4");
	    INTEGRATOR.addItem("TrapNewt");
	    INTEGRATOR.addItem("Animate Only");

	    START.addActionListener( this );
	    PAUSEPLAY.addActionListener( this );
	    TRAIL.addActionListener( this );
	    APPLY.addActionListener( this );
	    SELECTION.addItemListener( this );
	    INTEGRATOR.addItemListener( this );

	    START.setToolTipText("Start the simulation");
	    PAUSEPLAY.setToolTipText("Pause the simulation");
	    TRAIL.setToolTipText("Show historical trails");
	    APPLY.setToolTipText("Apply the parameters dt and warp");

	    gp = new JPanel();
	    gp.setLayout(new BoxLayout(gp, BoxLayout.X_AXIS));
	    gp.add(new JLabel("Instance:"));
	    gp.add(SELECTION);
	    gp.add(Box.createRigidArea(new Dimension(5,5)));
	    gp.add(new JLabel("Integrator:"));
	    gp.add(INTEGRATOR);

	    tp = new JPanel();
	    tp.setLayout(new BoxLayout(tp, BoxLayout.X_AXIS));
	    tp.add(Box.createRigidArea(new Dimension(5,5)));
	    tp.add(new JLabel("FrameRotationRate="));
	    tp.add(ROTRATE);
	    tp.add(Box.createRigidArea(new Dimension(5,5)));
	    tp.add(new JLabel("dt="));
	    tp.add(DT);
	    tp.add(Box.createRigidArea(new Dimension(5,5)));
	    tp.add(new JLabel("warp="));
	    tp.add(WARP);
	    tp.add(Box.createRigidArea(new Dimension(5,5)));
	    tp.add(new JLabel("n="));
	    tp.add(N);

	    bp = new JPanel();
	    bp.setLayout(new BoxLayout(bp, BoxLayout.X_AXIS));
	    bp.add(START);
	    bp.add(PAUSEPLAY);
	    bp.add(TRAIL);
	    bp.add(APPLY);

	    gc = new GalaxyCanvas();
	    // gc.addWindowListener(this);
	    gc.setPreferredSize(new Dimension(800, 800));
	    gc.setVisible(true);

	    JPanel controlPanel = new JPanel();
	    controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
	    controlPanel.add(gp);
	    controlPanel.add(tp);
	    controlPanel.add(bp);

	    Container c = this;
	    c.setLayout(new BorderLayout());
	    c.add(gc, BorderLayout.CENTER);
	    c.add(controlPanel, BorderLayout.SOUTH);
	    // add(gc);
	    // gc.show();
	}

	public void start() {
		// No-op for this simulation
	}

	public void itemStateChanged(ItemEvent e) {
	  if (e.getItem().equals("L1-L5 M/m=2")) {rotrate=0; dt=1.0e-6; warp=200; n=7; N.enable();} else 
	  if (e.getItem().equals("LagrangeN")) {rotrate=0; dt=0.001; warp=5; n=100; N.enable();} else 
	  if (e.getItem().equals("Lagrange2")) {rotrate=0; dt=0.001; warp=20; n=2; N.disable();} else 
	  if (e.getItem().equals("Lagrange3")) {rotrate=0; dt=0.001; warp=20; n=3; N.disable();} else 
	  if (e.getItem().equals("Lagrange20")) {rotrate=0; dt=0.001; warp=20; n=20; N.disable();} else 
	  if (e.getItem().equals("FigureEight3")) {rotrate=0; dt=0.001; warp=20;n=3; N.disable();} else 
	  if (e.getItem().equals("FigureEight5")) {rotrate=0; dt=0.0001; warp=200;n=5; N.disable();} else 
	  if (e.getItem().equals("FigureEight21")) {rotrate=0; dt=0.0001; warp=200;n=21; N.disable();} else 
	  if (e.getItem().equals("Trefoil5")) {rotrate=0; dt=0.0001; warp=200;n=5; N.disable();} else 
	  if (e.getItem().equals("Braid4")) {rotrate=0; dt=0.0001; warp=200;n=4; N.disable();} else 
	  if (e.getItem().equals("LagrangeNa")) {rotrate=0; dt=0.001; warp=5; n=100; N.enable();} else 
	  if (e.getItem().equals("Saturn")) {rotrate=0; dt=0.00001; warp=5; n=100; N.enable();} else 
	  if (e.getItem().equals("Saturn2")) {rotrate=0; dt=0.00001; warp=5; n=100; N.enable();} else 
	  if (e.getItem().equals("Trefoil4")) {rotrate=0; dt=0.001; warp=5; n=4; N.disable();} else 
	  if (e.getItem().equals("FoldedTriLoop4")) {rotrate=0; dt=0.001; warp=5; n=4; N.disable();} else 
	  if (e.getItem().equals("Ducati3")) {rotrate=0; dt=0.001; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("OrthQuasiEllipse4")) {rotrate=0; dt=0.0001; warp=500; n=4; N.disable();} else 
	  if (e.getItem().equals("PlateSaucer4")) {rotrate=0; dt=0.0001; warp=500; n=4; N.disable();} else 
	  if (e.getItem().equals("Rosette4")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("Hill15")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("DoubleDouble5")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("DoubleDouble10")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("DoubleDouble20")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("BorderCollie4")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("Lagrange3a")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Test")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("Test1")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("Hill3_2")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Hill3_3")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Hill3_0.5")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("L5")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("Test2")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("Test3")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_2")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_0.5")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_0.1")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_10")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_1.2")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_1.3")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_alluneq")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("Ducati3_alluneq2")) {rotrate=0; dt=0.0005; warp=50; n=3; N.disable();} else 
	  if (e.getItem().equals("FigureEight4")) {rotrate=0; dt=0.0005; warp=50; n=4; N.disable();} else 
	  if (e.getItem().equals("L1-L5 M/m=40")) {rotrate=0; dt=1.0e-5; warp=5; n=7; N.enable();}else 
	  {}
	  ROTRATE.setText(""+rotrate); 
	  DT.setText(""+dt); WARP.setText(""+warp); N.setText(""+n);
	}

        public void actionPerformed(ActionEvent e)
        {
	    int j;
	    double mv;
	    double r=0, th=0, v=0;
	    double v_escape=0;
	    double G, r0;

	    G=1.177e-4;
	    r0 = 0.01;

	    n = SL.atoi(N.getText());
	    int n7 = (n<7)?7:n;

	    double m[];
	    double x[];
	    double y[];
	    double vx[];
	    double vy[];
	    double rad[];
	    Color  col[];
	    double pathx[][] = null;
	    double pathy[][] = null;
	    double[][] as = null;
	    double[][] ac = null;
	    double[][] bs = null;
	    double[][] bc = null;
	    int i, k, M=0, N=0, K=0;

	    if (INTEGRATOR.getSelectedItem().equals("Euler")) {
		integrator = EULER;
	    } else
	    if (INTEGRATOR.getSelectedItem().equals("Midpoint (Leapfrog)")) {
		integrator = MIDPOINT;
	    } else
	    if (INTEGRATOR.getSelectedItem().equals("Hermite")) {
		integrator = HERMITE;
	    } else
	    if (INTEGRATOR.getSelectedItem().equals("RungeKutta")) {
		integrator = RUNGEKUTTA;
	    } else
	    if (INTEGRATOR.getSelectedItem().equals("TrapNewt")) {
		integrator = TRAPNEWT;
	    } else
	    if (INTEGRATOR.getSelectedItem().equals("Animate Only")) {
		integrator = ANIMATE;
	    } 

	    if (e.getSource() == START) {
		if (SELECTION.getSelectedItem().equals(
			    "--Select One--")) {
		  return;
		} else 
		Data.init(SELECTION.getSelectedItem().toString(), n, G);
		M=Data.M;
		N=Data.N;
		K=Data.K;
		m=Data.m;
		rad=Data.rad;
		col=Data.col;
		x=Data.x;
		y=Data.y;
		vx=Data.vx;
		vy=Data.vy;
		as=Data.as;
		ac=Data.ac;
		bs=Data.bs;
		bc=Data.bc;

		pathx = new double[N][M]; pathy = new double[N][M]; 
		if (as != null && ac != null && bs != null && bc != null
			&& pathx != null && pathy != null) {
		    for (i=0; i<N; i++) {
		      for (j=0; j<M; j++) {
			for (k=1; k<K; k++) {
			  pathx[i][j] += as[i][k]*Math.sin(k*j*2*Math.PI/M);
			  pathx[i][j] += ac[i][k]*Math.cos(k*j*2*Math.PI/M);
			  pathy[i][j] += bs[i][k]*Math.sin(k*j*2*Math.PI/M);
			  pathy[i][j] += bc[i][k]*Math.cos(k*j*2*Math.PI/M);
			}
		      }
		    }
		}

		rotrate = SL.atof(ROTRATE.getText());
		dt = SL.atof(DT.getText());
		warp = SL.atoi(WARP.getText());

		mv = 0;
		for (j=1; j<n; j++) {
		    mv += m[j]*vx[j];
		}
		vx[0] = -mv/m[0];
		mv = 0;
		for (j=1; j<n; j++) {
		    mv += m[j]*vy[j];
		}
		vy[0] = -mv/m[0];

		gc.setParams( n,m,x,y,vx,vy,rad,col,
			      G,r0,rotrate,dt,warp,pathx,pathy,integrator);
		gc.timer.start();
	    } else if (e.getSource() == PAUSEPLAY) {
		if (PAUSEPLAY.getText().equals("Pause")) {
		    PAUSEPLAY.setText("Play");
		    PAUSEPLAY.setToolTipText("Restart the simulation");
		    gc.timer.stop();
		} else
		if (PAUSEPLAY.getText().equals("Play")) {
		    PAUSEPLAY.setText("Pause");
		    PAUSEPLAY.setToolTipText("Pause the simulation");
		    gc.timer.start();
		} 
	    } else if (e.getSource() == TRAIL) {
		if (TRAIL.getText().equals("Show Trails")) {
		    TRAIL.setText("Hide Trails");
		    TRAIL.setToolTipText("Hide historical trails");
		    gc.setTrails(true);
		    if (menuTrailsItem != null) menuTrailsItem.setState(true);
		} else
		if (TRAIL.getText().equals("Hide Trails")) {
		    TRAIL.setText("Show Trails");
		    TRAIL.setToolTipText("Show historical trails");
		    gc.setTrails(false);
		    if (menuTrailsItem != null) menuTrailsItem.setState(false);
		}
	    } else if (e.getSource() == APPLY) {
		rotrate = SL.atof(ROTRATE.getText());
		dt = SL.atof(DT.getText());
		warp = SL.atoi(WARP.getText());
		if (gc != null) {
		    gc.setAnimEffects(rotrate,dt,warp);
		    gc.timer.start();
		}
	    }
        }

	public void stop()
	{
	    if (gc != null) if (gc.timer != null) gc.timer.stop();
	}

	public void windowClosing(WindowEvent e)
	{
	    gc.timer.stop();
	    gc.setVisible(false);
	}
	public void windowClosed(WindowEvent e) { }
	public void windowOpened(WindowEvent e) { }
	public void windowIconified(WindowEvent e) { gc.timer.stop(); }
	public void windowDeiconified(WindowEvent e) { gc.timer.start(); }
	public void windowActivated(WindowEvent e) { }
	public void windowDeactivated(WindowEvent e) { }

	public void setupMenuBar(Frame frame) {
		MenuBar menuBar = new MenuBar();

		// Controls menu
		Menu controlsMenu = new Menu("Controls");
		MenuItem startItem = new MenuItem("Start", new MenuShortcut(KeyEvent.VK_R));
		MenuItem pauseItem = new MenuItem("Pause", new MenuShortcut(KeyEvent.VK_P));
		startItem.addActionListener(e -> START.doClick());
		pauseItem.addActionListener(e -> PAUSEPLAY.doClick());
		controlsMenu.add(startItem);
		controlsMenu.add(pauseItem);

		// View menu
		Menu viewMenu = new Menu("View");
		menuTrailsItem = new CheckboxMenuItem("Show Trails", false);
		menuTrailsItem.setShortcut(new MenuShortcut(KeyEvent.VK_T));
		menuTrailsItem.addItemListener(e -> {
			if (menuTrailsItem.getState()) {
				if (TRAIL.getText().equals("Show Trails")) {
					TRAIL.doClick();
				}
			} else {
				if (TRAIL.getText().equals("Hide Trails")) {
					TRAIL.doClick();
				}
			}
		});
		viewMenu.add(menuTrailsItem);

		// Help menu
		Menu helpMenu = new Menu("Help");
		MenuItem homePageItem = new MenuItem("Prof. Vanderbei's Home Page");
		homePageItem.addActionListener(e -> {
			try {
				Desktop.getDesktop().browse(new URI("https://vanderbei.princeton.edu"));
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		});
		helpMenu.add(homePageItem);

		menuBar.add(controlsMenu);
		menuBar.add(viewMenu);
		menuBar.add(helpMenu);

		frame.setMenuBar(menuBar);
	}
}
