package astro.galaxy;

import java.awt.*;
import java.awt.event.*;
import java.applet.*;
import java.awt.image.*;
import java.util.*;
import java.io.*;
import java.net.*;
import java.text.*;
import javax.swing.*;

import myutil.*;
import myutil.Format;

public class GalaxyCanvas extends Canvas implements ActionListener
{
	int n;
	Body[] p;
	double G, r0;
	double t, dt;
	int warp;
	double v_escape;
	double theta, rotrate;
	double pathx[][], pathy[][];
	double dx[], dy[], dvx[], dvy[], tmpx[], tmpy[];
	double k1x[], k1y[], k1vx[], k1vy[];
	double k2x[], k2y[], k2vx[], k2vy[];
	double k3x[], k3y[], k3vx[], k3vy[];
	double k4x[], k4y[], k4vx[], k4vy[];
	Body pi, pj;
	int integrator;
	final int ANIMATE=0;
	final int TRAPNEWT=1;
	final int MIDPOINT=2;
	final int RUNGEKUTTA=3;
	final int HERMITE=4;
	final int EULER=5;

	public javax.swing.Timer timer=null;

	double cmax0, cmax;

	double old_x[], old_y[], old_vx[], old_vy[],
	       old_ax[], old_ay[], old_jx[], old_jy[];

	public void setParams(
		int n,
		double m[],
		double x[],
		double y[],
		double vx[],
		double vy[],
		double rad[],
		Color  col[],
		double G,
		double r0,
		double rotrate,
		double dt,
		int warp,
		double pathx[][],
		double pathy[][],
		int integrator)
	{
	    setBackground(Color.black);
	    if (timer==null) {timer = new javax.swing.Timer (25, this);}

	    int i, j;

	    this.n = n;
	    p = new Body[n];
	    for (j=0; j<n; j++) p[j] = new Body();

	    for (j=0; j<n; j++) {
		p[j].m = m[j];
		p[j].x = x[j];
		p[j].y = y[j];
		p[j].vx = vx[j];
		p[j].vy = vy[j];
		p[j].rad = rad[j];
		p[j].col = col[j];
		p[j].escaped = false;
	    }

	    cmax = 0.1;
	    this.G = G;
	    this.r0 = r0;
	    this.rotrate = rotrate;
	    this.dt = dt;
	    this.warp = warp;
	    this.pathx = pathx;
	    this.pathy = pathy;
	    this.integrator = integrator;

	    dx = new double[n];
	    dy = new double[n];
	    dvx = new double[n];
	    dvy = new double[n];
	    tmpx = new double[n];
	    tmpy = new double[n];

	    // if (n<=7) {cmax = 2;} // for the Lagrange case

	    k1x = new double[n]; k1y = new double[n]; 
	    k2x = new double[n]; k2y = new double[n]; 
	    k3x = new double[n]; k3y = new double[n]; 
	    k4x = new double[n]; k4y = new double[n]; 
	    k1vx = new double[n]; k1vy = new double[n];
	    k2vx = new double[n]; k2vy = new double[n];
	    k3vx = new double[n]; k3vy = new double[n];
	    k4vx = new double[n]; k4vy = new double[n];
	    pi = new Body(); pj = new Body();

	    v_escape = Math.sqrt(3*G*(n-1)/r0); 
	    theta = 0;
	    t=0;
            if (integrator == MIDPOINT) {
		double r03 = r0*r0*r0;
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r3 = dist3(p[i], p[j]);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (p[j].x - p[i].x)/r3;
			    p[j].ay -= G * p[i].m * (p[j].y - p[i].y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    p[j].vx += p[j].ax * dt/2;
		    p[j].vy += p[j].ay * dt/2;
		}
	    } else
            if (integrator == HERMITE) {
		double r02 = r0*r0;
		for (j=0; j<n; j++) {
		    p[j].ax = p[j].ay = 0;
		    p[j].jx = p[j].jy = 0;
		}
		for (i=0; i<n; i++) {
		    for (j=i+1; j<n; j++) {
			double rjix = p[j].x-p[i].x, 
			       rjiy = p[j].y-p[i].y, 
			       vjix = p[j].vx-p[i].vx, 
			       vjiy = p[j].vy-p[i].vy;
			double r2 = rjix*rjix + rjiy*rjiy;
			if (r2<r02) r2=r02;
			double r3 = r2*Math.sqrt(r2);
			double rv = (rjix*vjix + rjiy*vjiy)/r2;
			p[i].ax += G*p[j].m * rjix / r3;
			p[i].ay += G*p[j].m * rjiy / r3;
			p[j].ax -= G*p[i].m * rjix / r3;
			p[j].ay -= G*p[i].m * rjiy / r3;
			p[i].jx += G*p[j].m * (vjix - 3*rv*rjix)/r3;
			p[i].jy += G*p[j].m * (vjiy - 3*rv*rjiy)/r3;
			p[j].jx -= G*p[i].m * (vjix - 3*rv*rjix)/r3;
			p[j].jy -= G*p[i].m * (vjiy - 3*rv*rjiy)/r3;
		    }
		}
		old_x  = new double[n];
		old_y  = new double[n];
		old_vx = new double[n];
		old_vy = new double[n];
		old_ax = new double[n];
		old_ay = new double[n];
		old_jx = new double[n];
		old_jy = new double[n];
	    }
	}

	public void destroy()
	{
	    timer.stop();
	    timer=null;
	}

	public void actionPerformed (ActionEvent event)
	{
	    int i, j, k;
	    double r03 = r0*r0*r0;
	    for (k=0; k<warp; k++) {
	      t += dt;
	      theta += dt*rotrate;
              if (integrator == EULER) {
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r3 = dist3(p[i], p[j]);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (p[j].x - p[i].x)/r3;
			    p[j].ay -= G * p[i].m * (p[j].y - p[i].y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    p[j].x += p[j].vx * dt;
		    p[j].y += p[j].vy * dt;
		}
		for (j=0; j<n; j++) {
		    p[j].vx += p[j].ax * dt;
		    p[j].vy += p[j].ay * dt;
		}
              } else
              if (integrator == MIDPOINT) {
		for (j=0; j<n; j++) {
		    p[j].x += p[j].vx * dt;
		    p[j].y += p[j].vy * dt;
		}
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r3 = dist3(p[i], p[j]);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (p[j].x - p[i].x)/r3;
			    p[j].ay -= G * p[i].m * (p[j].y - p[i].y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    p[j].vx += p[j].ax * dt;
		    p[j].vy += p[j].ay * dt;
		}
              } else
              if (integrator == HERMITE) {
		double r02 = r0*r0;
		double far;

		for (i=0; i<n; i++) {
		    old_x[i]  = p[i].x;  old_y[i]  = p[i].y;
		    old_vx[i] = p[i].vx; old_vy[i] = p[i].vy;
		    old_ax[i] = p[i].ax; old_ay[i] = p[i].ay;
		    old_jx[i] = p[i].jx; old_jy[i] = p[i].jy;
		    p[i].x += p[i].vx*dt + p[i].ax*dt*dt/2 + p[i].jx*dt*dt*dt/6;
		    p[i].y += p[i].vy*dt + p[i].ay*dt*dt/2 + p[i].jy*dt*dt*dt/6;
		    p[i].vx += p[i].ax*dt + p[i].jx*dt*dt/2;
		    p[i].vy += p[i].ay*dt + p[i].jy*dt*dt/2;
		}
		for (i=0; i<n; i++) {
		    p[i].ax = p[i].ay = 0;
		    p[i].jx = p[i].jy = 0;
		}
		for (i=0; i<n; i++) {
		    for (j=i+1; j<n; j++) {
			double rjix = p[j].x - p[i].x;
			double rjiy = p[j].y - p[i].y;
			double vjix = p[j].vx - p[i].vx;
			double vjiy = p[j].vy - p[i].vy;

			double r2 = rjix*rjix + rjiy*rjiy;
			if (r2<r02) {r2=r02; far=1;} else {far=0;}
			double r3 = r2*Math.sqrt(r2);
			double rv = (rjix*vjix + rjiy*vjiy)/r2;

			p[i].ax += G*p[j].m * rjix/r3;
			p[i].ay += G*p[j].m * rjiy/r3;
			p[j].ax -= G*p[i].m * rjix/r3;
			p[j].ay -= G*p[i].m * rjiy/r3;
			p[i].jx += G*p[j].m * (vjix - 3*far*rv*rjix)/r3;
			p[i].jy += G*p[j].m * (vjiy - 3*far*rv*rjiy)/r3;
			p[j].jx -= G*p[i].m * (vjix - 3*far*rv*rjix)/r3;
			p[j].jy -= G*p[i].m * (vjiy - 3*far*rv*rjiy)/r3;
		    }
		}
		for (i=0; i<n; i++) {
		    p[i].vx = old_vx[i] + (old_ax[i] + p[i].ax)*dt/2
			                + (old_jx[i] - p[i].jx)*dt*dt/12;
		    p[i].vy = old_vy[i] + (old_ay[i] + p[i].ay)*dt/2
			                + (old_jy[i] - p[i].jy)*dt*dt/12;
		    p[i].x = old_x[i] + (old_vx[i] + p[i].vx)*dt/2
			              + (old_ax[i] - p[i].ax)*dt*dt/12;
		    p[i].y = old_y[i] + (old_vy[i] + p[i].vy)*dt/2
			              + (old_ay[i] - p[i].ay)*dt*dt/12;
		}
              } else
              if (integrator == RUNGEKUTTA) {
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r3 = dist3(p[i], p[j]);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (p[j].x - p[i].x)/r3;
			    p[j].ay -= G * p[i].m * (p[j].y - p[i].y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    k1x[j] = dt*p[j].vx;
		    k1y[j] = dt*p[j].vy;
		    k1vx[j] = dt*p[j].ax;
		    k1vy[j] = dt*p[j].ay;
		}
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    pi.x = p[i].x + k1x[i]/2;
			    pi.y = p[i].y + k1y[i]/2;
			    pj.x = p[j].x + k1x[j]/2;
			    pj.y = p[j].y + k1y[j]/2;
			    double r3 = dist3(pi, pj);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (pj.x - pi.x)/r3;
			    p[j].ay -= G * p[i].m * (pj.y - pi.y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    k2x[j] = dt*(p[j].vx + k1vx[j]/2);
		    k2y[j] = dt*(p[j].vy + k1vy[j]/2);
		    k2vx[j] = dt*p[j].ax;
		    k2vy[j] = dt*p[j].ay;
		}
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    pi.x = p[i].x + k2x[i]/2;
			    pi.y = p[i].y + k2y[i]/2;
			    pj.x = p[j].x + k2x[j]/2;
			    pj.y = p[j].y + k2y[j]/2;
			    double r3 = dist3(pi, pj);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (pj.x - pi.x)/r3;
			    p[j].ay -= G * p[i].m * (pj.y - pi.y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    k3x[j] = dt*(p[j].vx + k2vx[j]/2);
		    k3y[j] = dt*(p[j].vy + k2vy[j]/2);
		    k3vx[j] = dt*p[j].ax;
		    k3vy[j] = dt*p[j].ay;
		}
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    pi.x = p[i].x + k3x[i];
			    pi.y = p[i].y + k3y[i];
			    pj.x = p[j].x + k3x[j];
			    pj.y = p[j].y + k3y[j];
			    double r3 = dist3(pi, pj);
			    if (r3<r03) r3=r03;
			    p[j].ax -= G * p[i].m * (pj.x - pi.x)/r3;
			    p[j].ay -= G * p[i].m * (pj.y - pi.y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    k4x[j] = dt*(p[j].vx + k3vx[j]);
		    k4y[j] = dt*(p[j].vy + k3vy[j]);
		    k4vx[j] = dt*p[j].ax;
		    k4vy[j] = dt*p[j].ay;
		}
		

		for (j=0; j<n; j++) {
		    p[j].x += k1x[j]/6 + k2x[j]/3 + k3x[j]/3 + k4x[j]/6;
		    p[j].y += k1y[j]/6 + k2y[j]/3 + k3y[j]/3 + k4y[j]/6;
		    p[j].vx += k1vx[j]/6 + k2vx[j]/3 + k3vx[j]/3 + k4vx[j]/6;
		    p[j].vy += k1vy[j]/6 + k2vy[j]/3 + k3vy[j]/3 + k4vy[j]/6;
		}
              } else
              if (integrator == TRAPNEWT) {
		for (j=0; j<n; j++) {
		    p[j].ax = 0; p[j].ay = 0;
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r3 = dist3(p[i], p[j]);
			    p[j].ax -= G * p[i].m * (p[j].x - p[i].x)/r3;
			    p[j].ay -= G * p[i].m * (p[j].y - p[i].y)/r3;
			}
		    }
		}
		for (j=0; j<n; j++) {
		    tmpx[j] = p[j].ax * dt;
		    tmpy[j] = p[j].ay * dt;
		}
		for (j=0; j<n; j++) {
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r = dist(p[i],p[j]);
			    double r2 = r*r;
			    double r5 = r*r*r*r*r;
			    tmpx[j] += (dt*dt/2)*
				      G*p[i].m*
				      (r2*p[i].vx-3*(p[i].x-p[j].x)*
				      ((p[i].x-p[j].x)*p[i].vx +
				       (p[i].y-p[j].y)*p[i].vy))/r5
				      -
				      (dt*dt/2)*
				      G*p[i].m*
			              (r2*p[j].vx-3*(p[i].x-p[j].x)*
				      ((p[i].x-p[j].x)*p[j].vx +
				       (p[i].y-p[j].y)*p[j].vy))/r5;

			    tmpy[j] += (dt*dt/2)*
				      G*p[i].m*
				      (r2*p[i].vy-3*(p[i].y-p[j].y)*
				      ((p[i].x-p[j].x)*p[i].vx +
				       (p[i].y-p[j].y)*p[i].vy))/r5
				      -
				      (dt*dt/2)*
				      G*p[i].m*
			              (r2*p[j].vy-3*(p[i].y-p[j].y)*
				      ((p[i].x-p[j].x)*p[j].vx +
				       (p[i].y-p[j].y)*p[j].vy))/r5;

			}
		    }
		}
		for (j=0; j<n; j++) {
		    dvx[j] = tmpx[j]; dvy[j] = tmpy[j];
		}
		for (j=0; j<n; j++) {
		    for (i=0; i<n; i++) {
			if (i != j) {
			    double r = dist(p[i],p[j]);
			    double r2 = r*r;
			    double r5 = r*r*r*r*r;
			    dvx[j] += (dt*dt/4)*
				      G*p[i].m*
				      (r2*tmpx[i]-3*(p[i].x-p[j].x)*
				      ((p[i].x-p[j].x)*tmpx[i] +
				       (p[i].y-p[j].y)*tmpy[i]))/r5
				      -
				      (dt*dt/4)*
				      G*p[i].m*
			              (r2*tmpx[j]-3*(p[i].x-p[j].x)*
				      ((p[i].x-p[j].x)*tmpx[j] +
				       (p[i].y-p[j].y)*tmpy[j]))/r5;

			    dvy[j] += (dt*dt/4)*
				      G*p[i].m*
				      (r2*tmpy[i]-3*(p[i].y-p[j].y)*
				      ((p[i].x-p[j].x)*tmpx[i] +
				       (p[i].y-p[j].y)*tmpy[i]))/r5
				      -
				      (dt*dt/4)*
				      G*p[i].m*
			              (r2*tmpy[j]-3*(p[i].y-p[j].y)*
				      ((p[i].x-p[j].x)*tmpx[j] +
				       (p[i].y-p[j].y)*tmpy[j]))/r5;

			}
		    }
		}
		for (j=0; j<n; j++) {
		    dx[j] = dt*(p[j].vx + dvx[j]/2);
		    dy[j] = dt*(p[j].vy + dvy[j]/2);
		}
		for (j=0; j<n; j++) {
		    p[j].x += dx[j];
		    p[j].y += dy[j];
		    p[j].vx += dvx[j];
		    p[j].vy += dvy[j];
		}
	      }
	    }
	    double kinetic=0, potential=0, omega=0;
	    for (j=0; j<n; j++) {
		kinetic += p[j].m*(p[j].vx*p[j].vx+p[j].vy*p[j].vy);
		for (i=0; i<n; i++) {
		    if (i != j) {
			double r1 = dist(p[i], p[j]);
			potential -= G*p[i].m*p[j].m/r1;
		    }
		}
		omega += p[j].x*p[j].vy - p[j].y*p[j].vx;
	    }
	    //System.out.print("energy = "+(kinetic+potential));
	    //System.out.println(",  omega = "+omega);
	    paint(getGraphics());
	}

	public void setAnimEffects(double rotrate, double dt, int warp)
	{
	    this.rotrate = rotrate;
	    this.dt = dt;
	    this.warp = warp;
	}

	public void setTrails(boolean trails)
	{
	    this.trails = trails;
	    if (trails) firstpass = true;
	}

	double dist( Body pi, Body pj )
	{
	    double r;
	    double xdiff = pi.x - pj.x;
	    double ydiff = pi.y - pj.y;
	    r = Math.sqrt(xdiff*xdiff + ydiff*ydiff);
	    return r;
	}

	double dist3( Body pi, Body pj )
	{
	    double r = dist(pi, pj);
	    return r*r*r;
	}

	/*
	public void repaint () { paint(); }
	public void update (Graphics g) { paint(); }
	public void paint (Graphics g) {paint();}
	*/

	boolean trails = false;
	boolean firstpass = true;
	public void paint (Graphics g) 
	{
	    int i, j;
	    double r;
	    double costh=Math.cos(theta), sinth=Math.sin(theta);
	    double kin=0, pot=0;
	    DecimalFormat fmt = new DecimalFormat("###,##0.###");

	  if (trails) {
	   if (firstpass) {
	    firstpass = false;
	    GL.ginit(getGraphics(), getSize().width, getSize().height, this,false);
	    cmax0 = 0;
	    for (j=0; j<n; j++) {
		double v = Math.sqrt(p[j].vx*p[j].vx + p[j].vy*p[j].vy);
		if (v > 10*v_escape) {p[j].escaped = true;}
		if (!p[j].escaped) {
		    if (p[j].x >  cmax0) cmax0 =  p[j].x;
		    if (p[j].x < -cmax0) cmax0 = -p[j].x;
		    if (p[j].y >  cmax0) cmax0 =  p[j].y;
		    if (p[j].y < -cmax0) cmax0 = -p[j].y;
		}
	    }
	    if (cmax0 > cmax) {
		cmax *= 2;
	    }
	    GL.ortho2( -cmax, cmax, -cmax, cmax);
	    GL.color(Color.black);
	    GL.clear();
	    // GL.swapbuffers();
	   } else {
	    // GL.ginit(g, getSize().width, getSize().height, this,true);
	    cmax0 = 0;
	    for (j=0; j<n; j++) {
		double v = Math.sqrt(p[j].vx*p[j].vx + p[j].vy*p[j].vy);
		if (v > 10*v_escape) {p[j].escaped = true;}
		if (!p[j].escaped) {
		    if (p[j].x >  cmax0) cmax0 =  p[j].x;
		    if (p[j].x < -cmax0) cmax0 = -p[j].x;
		    if (p[j].y >  cmax0) cmax0 =  p[j].y;
		    if (p[j].y < -cmax0) cmax0 = -p[j].y;
		}
	    }
	    if (cmax0 > cmax) {
		cmax *= 2;
	    }
	    GL.ortho2( -cmax, cmax, -cmax, cmax);
	    for (j=0; j<n; j++) {
		GL.color(p[j].col);
		// g.setXORMode(p[j].col);
		r = p[j].rad;
		if (r < cmax/200) r = cmax/200;
		GL.pnt2(costh*p[j].x+sinth*p[j].y, 
			-sinth*p[j].x+costh*p[j].y);
	    }
	    // GL.swapbuffers();
	   }
	  } else {
	    GL.ginit(g, getSize().width, getSize().height, 
			    this,true);
	    cmax0 = 0;
	    for (j=0; j<n; j++) {
		double v = Math.sqrt(p[j].vx*p[j].vx + p[j].vy*p[j].vy);
		if (v > 10*v_escape) {p[j].escaped = true;}
		if (!p[j].escaped) {
		    if (p[j].x >  cmax0) cmax0 =  p[j].x;
		    if (p[j].x < -cmax0) cmax0 = -p[j].x;
		    if (p[j].y >  cmax0) cmax0 =  p[j].y;
		    if (p[j].y < -cmax0) cmax0 = -p[j].y;
		}
	    }
	    if (cmax0 > cmax) {
		cmax *= 2;
	    }
	    GL.ortho2( -cmax, cmax, -cmax, cmax);

	    GL.color(Color.black);
	    GL.clear();
	    /*
	    GL.color(Color.gray);
	    for (j=0, r=0.5; j<7; j++, r*=2) {
		GL.circ(0,0,r);
		GL.drawString(r+"au", r/1.41, r/1.41, "CENTER", "CENTER");
	    }
	    */

	    GL.color(Color.white);
	    if (pathx != null) {
		for (i=0; i<pathx.length; i++) {
		    GL.move2(costh*pathx[i][0]+sinth*pathy[i][0],
			    -sinth*pathx[i][0]+costh*pathy[i][0]);
		    for (j=1; j<pathx[i].length; j++) {
			GL.draw2(costh*pathx[i][j]+sinth*pathy[i][j], 
				-sinth*pathx[i][j]+costh*pathy[i][j]);
		    }
		}
	    }

	    for (j=0; j<n; j++) {
		GL.color(p[j].col);
		// g.setXORMode(p[j].col);
		r = p[j].rad;
		if (r < cmax/200) r = cmax/200;
		GL.circf(costh*p[j].x+sinth*p[j].y, 
			-sinth*p[j].x+costh*p[j].y, r);
	    }

	    kin = pot = 0;
	    for (j=0; j<n; j++) {
		kin += p[j].m*(p[j].vx*p[j].vx + p[j].vy*p[j].vy)/2;
		for (i=j+1; i<n; i++) {
		    double r1 = dist(p[i], p[j]);
		    pot -= G*p[i].m*p[j].m/r1;
		}
	    }

	    GL.color(Color.white);
	    GL.drawString("Time   = "+fmt.format(t/(2*Math.PI)), 
		    		-0.98*cmax,  0.98*cmax, "LEFT", "TOP");
	    GL.drawString("Energy = "+(kin+pot), 
		    		-0.98*cmax, -0.98*cmax, "LEFT", "BOTTOM");
	    GL.swapbuffers();
	  }
	}
}
