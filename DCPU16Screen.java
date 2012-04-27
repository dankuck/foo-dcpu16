
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.GroupLayout;
import javax.swing.JComponent;
import javax.swing.JTextArea;
import java.awt.Font;
import javax.swing.WindowConstants;
import java.awt.event.*;

class DCPU16Screen
	extends JFrame
	implements Runnable
{

	static final long serialVersionUID = 1;

	private Memory m;
	private int beginning;
	private boolean stop;
	private boolean running;
	private int[] lastBytes;

	private JTextArea screenTextArea;

	public DCPU16Screen(Memory memory, int beginning){
		this.m = memory;
		this.beginning = beginning;
	}

	public DCPU16Screen(Memory memory){
		this.m = memory;
		this.beginning = 0x8000;
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

		{
			screenTextArea = new JTextArea();
			screenTextArea.setFont(new Font("Monospaced", Font.PLAIN, 10));
			screenTextArea.setEditable(false);
		}
		thisLayout.setVerticalGroup(thisLayout.createSequentialGroup()
			.addComponent(screenTextArea, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
			);
		thisLayout.setHorizontalGroup(thisLayout.createSequentialGroup()
			    .addComponent(screenTextArea, GroupLayout.PREFERRED_SIZE, 250, GroupLayout.PREFERRED_SIZE)
			    );

		pack();
		setSize(250, 250);
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
		int[] bytes = m.read(beginning, 32 * 12);
		if (lastBytes == null || ! sameBytes(bytes, lastBytes))
			lastBytes = bytes;
		else
			return false;
		String horizontal = "+";
		for (int i = 0; i < 32; i++)
			horizontal += "=";
		horizontal += "+";
		String screen = /*horizontal + */"\n";
		for (int i = 0; i < bytes.length; i++){
			//if (i % 32 == 0)
			//	screen += "|";
			char c = (char)(bytes[i] & 0xFF);
			screen += c == 0 ? 0x20 : c;
			if (i % 32 == 31)
				screen += /*"|" + */"\n";
		}
		screen += /*horizontal + */"\n";
		screenTextArea.setText(screen);
		return true;
	}

}
