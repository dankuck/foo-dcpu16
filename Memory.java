
import hexer.*;

public class Memory{

	private int pageCount;
	private int pageSize = 0x100;
	private int[][] pages;
	private int mask = 0xFFFFFFFF;

	public Memory(int size){
		pageCount = (size - 1) / pageSize + 1; // at least 1 page
		pages = new int[pageCount][];
	}

	public Memory(int pageSize, int pageCount){
		this.pageCount = pageCount;
		this.pageSize = pageSize;
		pages = new int[pageCount][];
	}

	public void setMask(int mask){
		this.mask = mask;
	}

	public int size(){
		return pageSize * pageCount;
	}

	public void write(int location, int value){
		int page = location / pageSize;
		if (page >= pageCount)
			throw new RuntimeException("Attempt to write past end of emulated memory " + location + ".");
		if (pages[page] == null)
			pages[page] = new int[pageSize];
		pages[page][location % pageSize] = value & mask;
	}

	public void write(int start, int[] values){
		for (int i = 0; i < values.length; i++)
			write(start + i, values[i]);
	}

	public int read(int location){
		int page = location / pageSize;
		if (page >= pageCount)
			throw new RuntimeException("Attempt to read past end of emulated memory " + location + ".");
		if (pages[page] == null)
			return 0;
		return pages[page][location % pageSize] & mask;
	}

	public int[] read(int start, int end){
		if (start >= size())
			start = size() - 1;
		end = start + end;
		if (end >= size())
			end = size() - 1;
		int[] bytes = new int[end - start];
		int b = 0;
		for (int i = start; i < end; i ++)
			bytes[b++] = read(i);
		return bytes;
	}

	public String dump(int start, int end){
		if (start >= size())
			start = size() - 1;
		end = start + end;
		if (end >= size())
			end = size() - 1;
		start = 8 * (start / 8);
		StringBuilder b = new StringBuilder();
		for (int i = start; i < end; i += 8){
			int[] v = new int[8];
			for (int j = 0; j < 8; j++)
				v[j] = read(i + j);
			b.append(Hexer.hex(i) + ": " + Hexer.hexArray(v) + "\n");
		}
		return b.toString();
	}

	public String dump(){
		return dump(0, size() - 1);
	}

	public class MemoryAccessor implements Accessor{

		private int location;

		public MemoryAccessor(int location){
			this.location = location;
		}

		public int read(){
			return Memory.this.read(location);
		}

		public void write(int v){
			Memory.this.write(location, v);
		}

		public String toString(){
			return "[" + Hexer.hex(location) + "]";
		}

	}

	public MemoryAccessor accessor(int location){
		return new MemoryAccessor(location);
	}

	public static void main(String[] args){
		Memory m = new Memory(0x10);
		System.out.println("Created memory " + m.size() + " words");
		testRead(m, 0);
		testWrite(m, 0, 100);
		testRead(m, 0);
		testWrite(m, 1, 10);
		testRead(m, 1);
		testRead(m, 0);
		testRead(m, 0x10);
		testWrite(m, 0x10, 1);
		testRead(m, 0x10);
		testRead(m, 0xFF);
		testRead(m, 0x100);
		testWrite(m, 0x100, 1);
		System.out.println(m.dump(0, 8));
		m = new Memory(0x100);
		System.out.println("Created memory " + m.size() + " words");
		testRead(m, 0x100);
		testWrite(m, 0x100, 1);
		testRead(m, 0x100);
		testRead(m, 0x101);
		testWrite(m, 0x101, 1);
		testRead(m, 0x101);
		System.out.println(m.dump(0, 8));
		m = new Memory(0x1, 0x100);
		System.out.println("Created memory " + m.size() + " words");
		testRead(m, 0x100);
		testWrite(m, 0x100, 1);
		testRead(m, 0x100);
		testRead(m, 0x101);
		testWrite(m, 0x101, 1);
		testRead(m, 0x101);
		System.out.println(m.dump(0, 8));
		m = new Memory(0x1, 0x101);
		System.out.println("Created memory " + m.size() + " words");
		testRead(m, 0x100);
		testWrite(m, 0x100, 1);
		testRead(m, 0x100);
		testRead(m, 0x101);
		testWrite(m, 0x101, 1);
		testRead(m, 0x101);
		System.out.println(m.dump(0, 8));
		m = new Memory(0x2, 0x81);
		System.out.println("Created memory " + m.size() + " words");
		testRead(m, 0x100);
		testWrite(m, 0x100, 1);
		testRead(m, 0x100);
		testRead(m, 0x101);
		testWrite(m, 0x101, 1);
		testRead(m, 0x101);
		m = new Memory(0x10000);
		testRead(m, 0x10000);
		System.out.println(m.dump(0, 8));
	}

	public static void testRead(Memory m, int location){
		System.out.print("Reading " + location + ": ");
		try{
			System.out.println(m.read(location));
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

	public static void testWrite(Memory m, int location, int value){
		System.out.print("Writing " + location + ", " + value + ": ");
		try{
			m.write(location, value);
			System.out.println("wrote");
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

}
