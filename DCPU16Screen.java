/*
 * This file runs a program with screen and keyboard support.
 * Usage: java DCPU16Screen program_file
 * If the program_file ends in .asm, this will attempt to compile the file first.
 * Otherwise, it will assume that the file already contains compiled code.
 */

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
import dcpu16.*;

public class DCPU16Screen
{

	public static void main(String[] args){
		if (args.length < 1){
			System.out.println("Call me with a filename to run");
			return;
		}
		final DCPU16 d = new DCPU16();
		d.setSpeed(100000);
		loadFromFile(args[0], d.memory());
		new Thread(new DCPUConsole(d.memory())).start();
		final boolean[] stop = new boolean[1];
		new Thread(new Runnable(){
			public void run(){
				synchronized(this){
					while (! stop[0]){
						System.out.println(d.dump());
						try{
							wait(1000);
						}
						catch(InterruptedException e){}
					}
				}
			}
		}).start();
		try{
			d.run();
		}
		catch (Exception e){
			e.printStackTrace();
		}
		stop[0] = true;
		System.out.println(d.dump()); // take one last dump
	}

	private static void loadFromFile(String filename, Memory memory){
		if (filename.matches(".*\\.asm")){
			System.out.println("doing asm....");
			assembler.Assembler a = new assembler.Assembler(filename);
			try{
				memory.write(0, a.assemble());
			}
			catch (Exception e){
				e.printStackTrace();
			}
			return;
		}
		try{
			File file = new File(filename);
			FileInputStream reader = new FileInputStream(file);
			byte[] octets = new byte[(int)file.length()];
			reader.read(octets);
			int[] sextets = new int[octets.length / 2];
			for (int i = 0; i < sextets.length; i++)
				sextets[i] = ((int)(octets[i * 2] & 0xFF) << 8) | (int)(octets[i * 2 + 1] & 0xFF);
			memory.write(0, sextets);
		}
		catch (FileNotFoundException e){
			e.printStackTrace();
		}
		catch (IOException e){
			e.printStackTrace();
		}
	}
}
