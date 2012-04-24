
class DCPU16Screen implements Runnable{

	private Memory m;
	private int beginning;
	private boolean stop;
	private boolean running;
	private int[] lastBytes;

	public DCPU16Screen(Memory memory, int beginning){
		this.m = memory;
		this.beginning = beginning;
	}

	public void run(){
		running = true;
		while (true){
			if (stop)
				break;
			refresh();
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

	public void refresh(){
		int[] bytes = m.read(beginning, 32 * 12);
		if (lastBytes == null || ! sameBytes(bytes, lastBytes))
			lastBytes = bytes;
		else
			return;
		System.out.println("\n");
		String horizontal = "+";
		for (int i = 0; i < 32; i++)
			horizontal += "=";
		horizontal += "+";
		System.out.println(horizontal);
		for (int i = 0; i < bytes.length; i++){
			if (i % 32 == 0)
				System.out.print("|");
			System.out.print((char)(bytes[i] & 0xFF));
			if (i % 32 == 31)
				System.out.println("|");
		}
		System.out.println(horizontal);
	}

}
