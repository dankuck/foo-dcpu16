
// Holds multiple DCPU16's and lets us fiddle with them

import java.util.*;

public class Harness{

	private ArrayList<DCPU16> dcpu16s = new ArrayList<DCPU16>();
	private HashMap<DCPU16, Thread> threads = new HashMap<DCPU16, Thread>();
	private HashMap<DCPU16, Exception> exceptions = new HashMap<DCPU16, Exception>();

	private void say(String s){
		System.out.println(s);
	}

	private void ask(String s){
		System.out.print(s + " : ");
	}

	public void run(){
		while (true){
			showMenu();
			ask("Choose");
			switch (getChoice().charAt(0)){
				case '0':
					say("Ciao");
					stopCpus();
					return;
				case '1':
					DCPU16 d = new DCPU16();
					dcpu16s.add(d);
					runDcpuMenu(d);
					break;
				case '2':
					if (dcpu16s.size() == 0){
						say("There are no DCPU16's to work with. Create one instead.");
						break;
					}
					if (dcpu16s.size() == 1){
						say("There's only one, so I suppose you want to work with it.");
						runDcpuMenu(dcpu16s.get(0));
						break;
					}
					ask("Which one? 0 to " + (dcpu16s.size() - 1));
					int c = getInteger();
					if (c >= dcpu16s.size()){
						say("Bad choice");
						return;
					}
					runDcpuMenu(dcpu16s.get(c));
					break;
				default:
					say("That's not a choice");
			}
		}
	}

	private void showMenu(){
		say("0: Exit");
		say("1: Create new DCPU16");
		say("2: Work with a DCPU16");
	}

	private void stopCpus(){
		for (DCPU16 d : dcpu16s)
			d.stop();
	}

	private int getInteger(){
		try{
			return Integer.parseInt(getChoice());
		}
		catch (Exception e){
			say("" + e);
			return 0;
		}
	}

	private void runDcpuMenu(DCPU16 d){
		DCPU16Screen screen = null;
		boolean watching = false;
		while (true){
			if (watching && isRunning(d)){
				getChoice();
				if (screen != null){
					screen.stop();
					screen = null;
				}
				else
					d.setDebug(false);
				watching = false;
			}
			showDcpu(d);
			showDcpuMenu();
			ask("Choose");
			switch (getChoice().charAt(0)){
				case '0':
					if (screen != null){
						screen.stop();
						screen = null;
					}
					return;
				case '1':
					toggleRunDcpu(d);
					break;
				case '2':
					read(d);
					break;
				case '3':
					write(d);
					break;
				case '4':
					setRegister(d);
					break;
				case '5':
					resetRegisters(d);
					break;
				case '6':
					d.step();
					break;
				case '7':
					new Thread(screen = new DCPU16Screen(d.memory())).start();
					watching = true;
					if (isRunning(d))
						say("Watching screen...");
					else
						say("Will watch screen when the DCPU starts...");
					break;
				case '8':
					d.setDebug(true);
					watching = true;
					if (isRunning(d))
						say("Watching debug...");
					else
						say("Will watch debug when the DCPU starts...");
					break;
				case '9':
					break;
				default:
					say("That's not a choice");
			}
		}
	}

	private void showDcpuMenu(){
		say("0: Back to main menu");
		say("1: Start / Stop");
		say("2: Read memory");
		say("3: Write memory");
		say("4: Set register");
		say("5: Reset registers");
		say("6: Step");
		say("7: Watch VRAM");
		say("8: Watch Debug");
		say("9: Show again");
	}

	private void setRegister(DCPU16 d){
		say(Registers.A + ": A");
		say(Registers.B + ": B");
		say(Registers.C + ": C");
		say(Registers.X + ": X");
		say(Registers.Y + ": Y");
		say(Registers.Z + ": Z");
		say(Registers.PC + ": PC");
		say(Registers.SP + ": SP");
		say(Registers.O + ": O");
		ask("Choose a register");
		Accessor r = d.registers().accessor(getInteger());
		ask("Value (hex)");
		r.write(Hexer.unhex(getChoice()));
	}

	private void resetRegisters(DCPU16 d){
		d.registers().accessor(Registers.A).write(0);
		d.registers().accessor(Registers.B).write(0);
		d.registers().accessor(Registers.C).write(0);
		d.registers().accessor(Registers.X).write(0);
		d.registers().accessor(Registers.Y).write(0);
		d.registers().accessor(Registers.Z).write(0);
		d.registers().accessor(Registers.PC).write(0);
		d.registers().accessor(Registers.SP).write(0);
		d.registers().accessor(Registers.O).write(0);
	}

	private void write(DCPU16 d){
		ask("What location?");
		int location = Hexer.unhex(getChoice());
		ask("Enter bytes (in space-separated hex)");
		int[] bytes = Hexer.unhexArray(getChoice());
		d.memory().write(location, bytes);
	}

	private void read(DCPU16 d){
		int location = 0;
		while (location != 99) {
			say(d.memory().dump(location, 80));
			ask("Location (99 to quit) ");
			location = Hexer.unhex(getChoice());
		}
	}

	private void showDcpu(DCPU16 d){
		if (isRunning(d))
			say("[ RUNNING ]");
		Exception e = exceptions.get(d);
		if (e != null)
			say("Exception: " + e);
		say(d.dump());
	}

	private boolean isRunning(DCPU16 d){
		return threads.get(d) != null;
	}

	private void toggleRunDcpu(final DCPU16 d){
		if (! isRunning(d)){
			exceptions.remove(d);
			Thread t = new Thread(new Runnable(){
				public void run(){
					try{
						d.run();
					}
					catch (Exception e){
						exceptions.put(d, e);
					}
					threads.remove(d);
				}
			});
			threads.put(d, t);
			t.start();
		}
		else
			d.stop();
	}

	private String getChoice(){
		String choice = System.console().readLine();
		if (choice.length() == 0)
			return " ";
		return choice;
	}

	public static void main(String args[]){
		new Harness().run();
	}

}
