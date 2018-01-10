package de.mpicbg.scf.InteractiveWatershed_dev.imgTools;

/*
Author: Robert Haase, Scientific Computing Facility, MPI-CBG, Dresden  

Copyright 2017 Max Planck Institute of Molecular Cell Biology and Genetics, Dresden, Germany

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following 
conditions are met:

1 - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.

2 - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer 
in the documentation and/or other materials provided with the distribution.

3 - Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived 
from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

import ij.IJ;

import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;

//import de.mpicbg.scf.imgtools.ui.DebugHelper;


public class ProgressDialog extends JDialog {
	/**
	 * 
	 */
	private static final long serialVersionUID = 2180397247413784822L;
	private boolean cancelled = false;

	private final JProgressBar progressBar = new JProgressBar();
	private final JLabel lblText = new JLabel("");
	private final JButton cancelButton = new JButton("Cancel");
	private static ProgressDialog instance;
	
	private static double minimumTimeToDecideIfShowDialog = 0.08; //minutes
	private static double minimumTimeToShowDialog = 1; //minutes
	
	private long startTimeStamp;
	private final JLabel lblEstimatedRemainingTime = new JLabel("");
	
	/**
	 * Launch the application.
	 *
	 * @param args arguments
	 */
	public static void main(String[] args) {
		try {
			ProgressDialog dialog = new ProgressDialog();
			dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
			dialog.setVisible(true);
		} catch (Exception e) {
			e.printStackTrace();
		}
	} 

	/**
	 * Create the dialog.
	 */
	private ProgressDialog() {
		setTitle("This may take a while...");
		setBounds(100, 100, 450, 110);
		getContentPane().setLayout(new BorderLayout());
		JPanel contentPanel = new JPanel();
		contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
		getContentPane().add(contentPanel, BorderLayout.CENTER);
		contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.X_AXIS));
		{
			JPanel buttonPane = new JPanel();
			contentPanel.add(buttonPane);
			buttonPane.setLayout(null);
			{
				lblText.setBounds(6, 0, 428, 20);
				
				buttonPane.add(lblText);
			}
			{
				progressBar.setBounds(6, 20, 428, 20);
				
				buttonPane.add(progressBar);
			}
			{
				
				cancelButton.setBounds(288, 43, 146, 29);
				cancelButton.addMouseListener(new MouseAdapter() {
					@Override
					public void mouseReleased(MouseEvent e) {
						cancelled = true;
					}
				});
				cancelButton.setActionCommand("Cancel");
				buttonPane.add(cancelButton);
			}
			{
				lblEstimatedRemainingTime.setBounds(6, 48, 281, 16);
				buttonPane.add(lblEstimatedRemainingTime);
			}
		}
	}
	
	public static void setStatusText(String text)
	{
		IJ.showStatus(text);
		if (isHeadless())
		{
			return;
		}
		getInstance().lblText.setText(text);
	}
	
	public static void setProgress(double value)
	{
		if (isHeadless())
		{
			return;
		}
		getInstance().setStatusValue(value);
	}

	private static long lastTimeStamp = 0;
	private static double lastValue;
	private void setStatusValue(double value)
	{
		if (isHeadless())
		{
			return;
		}
		if (System.currentTimeMillis() - lastTimeStamp < 1000 && Math.abs(lastValue - value) < 0.1 && value < 0.99)
		{
			return;
		}
		lastTimeStamp = System.currentTimeMillis();
		
		if (value < 0)
		{
			value = 0;
		}
		if (value > 1)
		{
			value = 1;
		}
		progressBar.setValue((int)(value  * 100));
		IJ.showProgress(value);

		long currentTimeStamp = System.currentTimeMillis();
		long timeDiff  = currentTimeStamp - startTimeStamp;
		if (value > 0 && timeDiff > 0)
		{
			long futureTime = (long)( timeDiff/value * (1 - value));
			lblEstimatedRemainingTime.setText("Remaining: " + humanReadableTimeNumber(futureTime));
			//DebugHelper.print(this, "" + progressBar.getValue() + "% " + lblEstimatedRemainingTime.getText());
			if ((!this.isVisible()) && timeDiff > minimumTimeToDecideIfShowDialog * 60000 && futureTime > minimumTimeToShowDialog * 60000) 
			{
				this.setVisible(true);
			}
		}
	}

	public static ProgressDialog getInstance()
	{
		if (isHeadless())
		{
			return null;
		}
		if (instance == null)
		{
			instance = new ProgressDialog();
		}
		return instance;
	}
	
	public static void reset()
	{
		if (isHeadless())
		{
			return;
		}
		getInstance().startTimeStamp = System.currentTimeMillis();
		setCancelAllowed(true);
		getInstance().setStatusValue(0);
		setStatusText("");
		getInstance().lblEstimatedRemainingTime.setText("");
		getInstance().cancelled = false;
	}
	
	public static void setCancelAllowed(boolean value)
	{
		if (isHeadless())
		{
			return;
		}
		getInstance().cancelButton.setEnabled(value);
	}
	
	public static boolean wasCancelled()
	{
		if (isHeadless())
		{
			return false;
		}
		return getInstance().cancelled;
	}

	public static void setMinimumTimeToDecideIfShowDialog( double minimumTimeToDecideIfShowDialog) 
	{
		if (minimumTimeToDecideIfShowDialog < 0)
		{
			minimumTimeToDecideIfShowDialog = 0;
		}
		ProgressDialog.minimumTimeToDecideIfShowDialog = minimumTimeToDecideIfShowDialog;
	}

	public static void setMinimumTimeToShowDialog(double minimumTimeToShowDialog) 
	{
		if (minimumTimeToShowDialog < 0)
		{
			minimumTimeToShowDialog = 0;
		}
		if (minimumTimeToShowDialog > 1)
		{
			minimumTimeToShowDialog = 1;
		}
		ProgressDialog.minimumTimeToShowDialog = minimumTimeToShowDialog;
	}
	
	public static void finish()
	{
		if (isHeadless())
		{
			return;
		}
		getInstance().setVisible(false);
		IJ.showProgress(1);
	}
	
	
	/**
	 * Returns a number of milliseconds to readable string such as "1h, 5min"
	 * @param numberOfMilliSeconds number of milliseconds
	 * @return String naming the time
	 */
	public static String humanReadableTimeNumber(long numberOfMilliSeconds)
	{
		double num = (double)numberOfMilliSeconds;
		double formerValue = 0;
		String formerUnit = "";
		String unit = "ms";
			
		if (num > 1000)
		{
			formerValue = num % 1000;
			num /= 1000;
			formerUnit = unit;
			unit = "s";
	
			if (num > 60)
			{
				formerValue = num % 60;
				num /= 60;
				formerUnit = unit;
				unit = "min";
				
				if (num > 60)
				{
					formerValue = num % 60;
					num /= 60;
					formerUnit = unit;
					unit = "h";
				}
			}
		}
			
		String res = (long)num + " " + unit;
		if (formerValue > 0)
		{
			res = res + " " + (long)formerValue + " " + formerUnit;
		}
		return res;
	}
	
	
	/**
	 * 
	 * Determine, if ImageJ is running in headless mode
	 * @return True, if headless
	 */
	public static boolean isHeadless()
	{
		try 
		{
			if (GraphicsEnvironment.isHeadless())
			{
				return true;
			}
		}
		catch (java.awt.HeadlessException e)
		{
			return true;
		}
		return false;
	}
	
}
