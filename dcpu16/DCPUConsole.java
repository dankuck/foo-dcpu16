package dcpu16;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.awt.Font;
import javax.swing.WindowConstants;
import java.awt.event.*;

import java.util.*;
import java.io.*;

import hexer.*;

public class DCPUConsole
	extends JFrame
	implements Runnable
{

	static final long serialVersionUID = 1;

	private Memory m;
	private int screenStart;
	private boolean stop;
	private boolean running;
	private int[] lastBytes;
	private int keyboardStart;
	private int keyboardPosition = 0;

	private JTextArea screenTextArea;

	public DCPUConsole(Memory memory, int screenStart, int keyboardStart){
		this.m = memory;
		this.screenStart = screenStart;
		this.keyboardStart = keyboardStart;
	}

	public DCPUConsole(Memory memory){
		this.m = memory;
		this.screenStart = 0x8000;
		this.keyboardStart = 0x9000;
	}

	private void doShow(){
		SwingUtilities.invokeLater(new Runnable(){
			public void run(){
				showMe();
			}
		});
	}

	private void showMe(){
		GroupLayout thisLayout = new GroupLayout((JComponent)getContentPane());
		getContentPane().setLayout(thisLayout);
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		KeyListener keyboard = new KeyListener(){
			public void keyPressed(KeyEvent e){}
			public void keyReleased(KeyEvent e){}
			public void keyTyped(KeyEvent e){
				queueKey(e.getKeyChar());
			}
		};
		addKeyListener(keyboard);

		{
			screenTextArea = new JTextArea();
			screenTextArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
			screenTextArea.setEditable(false);
			screenTextArea.addKeyListener(keyboard);
		}
		thisLayout.setVerticalGroup(thisLayout.createSequentialGroup()
			.addComponent(screenTextArea, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
			);
		thisLayout.setHorizontalGroup(thisLayout.createSequentialGroup()
			    .addComponent(screenTextArea, GroupLayout.PREFERRED_SIZE, 300, GroupLayout.PREFERRED_SIZE)
			    );

		pack();
		setSize(300, 300);
		setLocationRelativeTo(null);
		setVisible(true);

		addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				stop();
			}
		});
	}

	public void run(){
		doShow();
		running = true;
		synchronized(this){
			while (true){
				if (stop)
					break;
				if (! refresh()) // if refresh does something, don't wait, go around again immediately
					try{
						wait(100);
					}
					catch (InterruptedException e){}
			}
		}
		running = false;
	}

	public void stop(){
		if (running)
			stop = true;
	}

	private boolean sameBytes(int[] a, int[] b){
		if (a.length != b.length)
			return false;
		for (int i = 0; i < a.length; i++)
			if (a[i] != b[i])
				return false;
		return true;
	}

	public boolean refresh(){
		if (screenTextArea == null)
			return false;
		int[] bytes = m.read(screenStart, 32 * 12);
		if (lastBytes == null || ! sameBytes(bytes, lastBytes))
			lastBytes = bytes;
		else
			return false;
		String screen = "";
		for (int i = 0; i < bytes.length; i++){
			if (i % 32 == 0)
				screen += Hexer.hex(i / 32) + ": ";
			char c = (char)(bytes[i] & 0xFF);
			screen += c == 0 ? 0x20 : c;
			if (i % 32 == 31)
				screen += "\n";
		}
		screen += "\n";
		screenTextArea.setText(screen);
		return true;
	}

	private void queueKey(char k){
		int location = keyboardStart + keyboardPosition;
		if (m.read(location) != 0){
			java.awt.Toolkit.getDefaultToolkit().beep();
			return;
		}
		m.write(location, k);
		keyboardPosition = (keyboardPosition + 1) & 0xF;
	}

}
