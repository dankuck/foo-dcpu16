package dcpu16;

import hexer.*;

public class Registers{

	private int[] registers = new int[11];

	final public static int A = 0;
	final public static int B = 1;
	final public static int C = 2;
	final public static int X = 3;
	final public static int Y = 4;
	final public static int Z = 5;
	final public static int I = 6;
	final public static int J = 7;
	final public static int PC = 8;
	final public static int SP = 9;
	final public static int O = 10;

	public int read(int r){
		return registers[r] & 0xFFFF;
	}

	public void write(int r, int value){
		registers[r] = value & 0xFFFF;
	}

	public void inc(int r){
		inc(r, 1);
	}

	public void inc(int r, int inc){
		write(r, read(r) + inc);
	}

	public class RegisterAccessor implements Accessor{

		private int register;

		public RegisterAccessor(int register){
			this.register = register;
		}

		public int read(){
			return Registers.this.read(register);
		}

		public void write(int v){
			Registers.this.write(register, v);
		}

		public void inc(){
			inc(1);
		}

		public void inc(int v){
			write(read() + v);
		}

		public String toString(){
			switch (register){
			case 0: return "A";
			case 1: return "B";
			case 2: return "C";
			case 3: return "X";
			case 4: return "Y";
			case 5: return "Z";
			case 6: return "I";
			case 7: return "J";
			case 8: return "PC";
			case 9: return "SP";
			case 10: return "O";
			}
			return "?";
		}

	}

	public RegisterAccessor accessor(int r){
		return new RegisterAccessor(r);
	}

	public RegisterAccessor pc(){
		return accessor(PC);
	}

	public RegisterAccessor sp(){
		return accessor(SP);
	}

	public RegisterAccessor o(){
		return accessor(O);
	}

	public String dump(){
		return " A: " + Hexer.hex(read(A)) + " "
				+ " B: " + Hexer.hex(read(B)) + " "
				+ " C: " + Hexer.hex(read(C)) + " "
				+ " X: " + Hexer.hex(read(X)) + " "
				+ " Y: " + Hexer.hex(read(Y)) + " "
				+ " Z: " + Hexer.hex(read(Z)) + "\n"
				+ " I: " + Hexer.hex(read(I)) + " "
				+ " J: " + Hexer.hex(read(J)) + "\n"
				+ "PC: " + Hexer.hex(read(PC)) + " "
				+ "SP: " + Hexer.hex(read(SP)) + " "
				+ " O: " + Hexer.hex(read(O)) + "\n";
	}

	public static void main(String[] args){
		Registers r = new Registers();
		r.sp().inc(1);
		r.sp().inc(-1);
		System.out.println(r.sp().read());
	}

}
