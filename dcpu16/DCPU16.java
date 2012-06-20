package dcpu16;

// Specification: http://0x10c.com/doc/dcpu-16.txt

import hexer.*;

public class DCPU16{

	private Memory m;
	private Registers r;
	private boolean skipInstruction = false;
	private boolean running = false;
	private boolean stop = false;
	private long startTime = -1;
	private long startCycles = -1;
	private long cycles = 0;
	private long hertzLimit = 0;

	private boolean debugging = false;


	public DCPU16(){
		init(new Memory(0x10000));
	}

	public DCPU16(Memory m){
		init(m);
	}

	private void init(Memory m){
		this.m = m;
		m.setMask(0xFFFF);
		r = new Registers();
	}

	public Memory memory(){
		return m;
	}

	public Registers registers(){
		return r;
	}

	public void setDebug(boolean on){
		debugging = on;
	}

	private void debug(String s){
		if (debugging)
			System.out.println(s);
	}

	public void setSpeed(long hertzLimit){
		this.hertzLimit = hertzLimit;
		startTime = new java.util.Date().getTime();
		startCycles = cycles;
	}

	/**
	 * Runs step() in a loop until a crash or until some other thread calls stop().
	 */
	public void run(){
		doStart();
		synchronized(this){
			while (true){
				if (stop){
					doStop();
					return;
				}
				try{
					step();
					if (hertzLimit > 0 && hertz() > hertzLimit)
						try{
							wait(10);
						}
						catch (InterruptedException e){}
				}
				catch (RuntimeException e){
					doStop();
					debug("" + e);
					throw e;
				}
			}
		}
	}

	private void doStart(){
		stop = false;
		running = true;
		startTime = new java.util.Date().getTime();
		startCycles = cycles;
	}

	private void doStop(){
		stop = false;
		running = false;
		startTime = -1;
		startCycles = -1;
	}

	public void stop(){
		if (running)
			stop = true;
	}

	public double hertz(){
		if (startTime < 0 || startCycles < 0)
			return 0.0;
		long cyclesPassed = cycles - startCycles;
		long timePassed = new java.util.Date().getTime() - startTime;
		if (timePassed == 0)
			return cyclesPassed > 0 ? Double.POSITIVE_INFINITY : 0.0;
		else
			return 1000 * cyclesPassed / timePassed; // cycles / milliseconds => kHz, kHz * 1000 => hertz
	}

	public void step(){
		int word = nextWord().read();
		int instruction = word & 0xF;
		int a = (word >> 4) & 0x3F;
		int b = (word >> 10) & 0x3F;
		debug(Hexer.hex(word) + " => " + Hexer.hex(instruction) + " a: " + Hexer.hex(a) + " b: " + Hexer.hex(b));
		if (instruction == 0){
			nonbasic(a, b);
		}
		else{
			long priorCycles = cycles; // Some of the cycles stuff in the spec is actually just magic
			Accessor aa = accessor(a);
			Accessor ba = accessor(b);
			if (skipInstruction){ // has to happen after we lookup the accessors, in case one needs to eat the next word
				skipInstruction = false;
				cycles = priorCycles + 1;
				debug("Skipped instruction");
				return;
			}
			switch (instruction){
				case 0x1:
					set(aa, ba); break;
				case 0x2:
					add(aa, ba); break;
				case 0x3:
					sub(aa, ba); break;
				case 0x4:
					mul(aa, ba); break;
				case 0x5:
					div(aa, ba); break;
				case 0x6:
					mod(aa, ba); break;
				case 0x7:
					shl(aa, ba); break;
				case 0x8:
					shr(aa, ba); break;
				case 0x9:
					band(aa, ba); break;
				case 0xA:
					bor(aa, ba); break;
				case 0xB:
					bxor(aa, ba); break;
				case 0xC:
					ife(aa, ba); break;
				case 0xD:
					ifn(aa, ba); break;
				case 0xE:
					ifg(aa, ba); break;
				case 0xF:
					ifb(aa, ba); break;
				default: // should be impossible because of word & 0xF above
					throw new RuntimeException("Instruction " + instruction + " not yet available");
			}
		}
	}

	private void set(Accessor aa, Accessor ba){
		debug("SET " + aa + " " + ba);
		debug("b=" + Hexer.hex(ba.read()));
		aa.write(ba.read());
		cycles++;
	}

	private void add(Accessor aa, Accessor ba){
		debug("ADD " + aa + " " + ba);
		debug("a=" + Hexer.hex(aa.read()));
		debug("b=" + Hexer.hex(ba.read()));
		int v = aa.read() + ba.read();
		aa.write(v & 0xFFFF);
		r.o().write(v >> 16);
		cycles+=2;
	}

	private void sub(Accessor aa, Accessor ba){
		debug("SUB " + aa + " " + ba);
		debug("a=" + Hexer.hex(aa.read()));
		debug("b=" + Hexer.hex(ba.read()));
		int v = aa.read() - ba.read();
		aa.write(v & 0xFFFF);
		r.o().write(v >> 16);
		cycles+=2;
	}

	private void nonbasic(int instruction, int a){
		long priorCycles = cycles; // magic is happening
		Accessor aa = accessor(a);
		if (skipInstruction){
			skipInstruction = false;
			cycles = priorCycles + 1;
			debug("Skipped instruction");
			return;
		}
		switch (instruction){
			case 0x01:
				jsr(aa); break;
			default:
				throw new RuntimeException("Non-basic instruction is reserved: " + Hexer.hex(instruction));
		}
	}

	private void jsr(Accessor aa){
		debug("JSR " + aa);
		m.write(pushStack(), r.pc().read());
		r.pc().write(aa.read());
		cycles+=2;
	}

	public void mul(Accessor aa, Accessor ba){

		debug("MUL " + aa + " " + ba);
		int v = aa.read() * ba.read();
		r.o().write(v >> 16);
		aa.write(v & 0xFFFF);
		cycles+=2;
	}

	public void div(Accessor aa, Accessor ba){

		debug("DIV " + aa + " " + ba);
		if (ba.read() == 0){
			aa.write(0);
			r.o().write(0);
			return;
		}
		r.o().write(((aa.read() << 16) / ba.read()) & 0xFFFF);
		aa.write((aa.read() / ba.read()) & 0xFFFF);
		cycles+=3;
	}

	public void mod(Accessor aa, Accessor ba){

		debug("MOD " + aa + " " + ba);
		if (ba.read() == 0){
			aa.write(0);		return;
		}
		aa.write(aa.read() % ba.read());
		cycles+=3;
	}

	public void shl(Accessor aa, Accessor ba){

		debug("SHL " + aa + " " + ba);
		int v = aa.read() << ba.read();
		r.o().write((v >> 16) & 0xFFFF);
		aa.write(v & 0xFFFF);
		cycles+=2;
	}

	public void shr(Accessor aa, Accessor ba){

		debug("SHR " + aa + " " + ba);
		r.o().write(((aa.read() << 16) >> ba.read()) & 0xFFFF);
		aa.write((aa.read() >> ba.read()) & 0xFFFF);
		cycles+=2;
	}

	public void band(Accessor aa, Accessor ba){

		debug("AND " + aa + " " + ba);
		aa.write(aa.read() & ba.read());
		cycles++;
	}

	public void bor(Accessor aa, Accessor ba){

		debug("BOR " + aa + " " + ba);
		aa.write(aa.read() | ba.read());
		cycles++;
	}

	public void bxor(Accessor aa, Accessor ba){

		debug("XOR " + aa + " " + ba);
		aa.write(aa.read() ^ ba.read());
		cycles++;
	}

	public void ife(Accessor aa, Accessor ba){

		debug("IFE " + aa + " " + ba);
		if (aa.read() != ba.read())
			skipInstruction = true;
		cycles+=2;
	}

	public void ifn(Accessor aa, Accessor ba){

		debug("IFN " + aa + " " + ba);
		if (aa.read() == ba.read())
			skipInstruction = true;
		cycles+=2;
	}

	public void ifg(Accessor aa, Accessor ba){

		debug("IFG " + aa + " " + ba);
		if (aa.read() <= ba.read())
			skipInstruction = true;
		cycles+=2;
	}

	public void ifb(Accessor aa, Accessor ba){

		debug("IFB " + aa + " " + ba);
		if ((aa.read() & ba.read()) == 0)
			skipInstruction = true;
		cycles+=2;
	}


	private boolean isGenericRegister(int v){
		return v >= 0x00 && v <= 0x07;
	}

	private boolean isGenericRegisterMemory(int v){
		return (v & 0xF8) == 0x08 && isGenericRegister(v & 0x07);
	}

	private boolean isRegister(int v){
		return isGenericRegister(v) || v == 0x1b || v == 0x1c || v == 0x1d;
	}

	private boolean isRegisterMemory(int v){
		return isGenericRegisterMemory(v) || v == 0x19;
	}

	private boolean isLiteral(int v){
		return v >= 0x20 && v <= 0x3F;
	}

	private boolean isNextWordMemory(int v){
		return v == 0x1E;
	}

	private boolean isNextWord(int v){
		return v == 0x1F;
	}

	private boolean isNextWordPlusRegisterMemory(int v){
		return v >= 0x10 && v <= 0x17;
	}

	private boolean isPop(int v){
		return v == 0x18;
	}

	private boolean isPush(int v){
		return v == 0x1A;
	}

	public String dump(){
		double speed = hertz();
		return "Memory:\n" + m.dump(0, 10) + "...\n"
				+ "Stack: " + Hexer.hex(r.sp().read()) + "\n" + m.dump(r.sp().read(), 9) + "\n"
				+ "Registers:\n" + r.dump() + "\n"
				+ "Others: \nIF: " + (skipInstruction ? "0" : "1") + " Cycles: " + cycles + " Speed: " + (speed > 0 ? "kHz: " + (speed / 1000) : "") + "\n";
	}

	public Accessor accessor(int resource){
		if (isRegister(resource)){
			switch (resource){
				case 0x00:
					return r.accessor(r.A);
				case 0x01:
					return r.accessor(r.B);
				case 0x02:
					return r.accessor(r.C);
				case 0x03:
					return r.accessor(r.X);
				case 0x04:
					return r.accessor(r.Y);
				case 0x05:
					return r.accessor(r.Z);
				case 0x06:
					return r.accessor(r.I);
				case 0x07:
					return r.accessor(r.J);
				case 0x1B:
					return r.accessor(r.SP);
				case 0x1C:
					return r.accessor(r.PC);
				case 0x1D:
					return r.accessor(r.O);
				default:
					throw new RuntimeException("isRegister is broke: instruction isn't isRegister " + resource);
			}
		}
		else if (isRegisterMemory(resource)){
			switch (resource){
				case 0x08:
					return m.accessor(r.accessor(r.A).read());
				case 0x09:
					return m.accessor(r.accessor(r.B).read());
				case 0x0A:
					return m.accessor(r.accessor(r.C).read());
				case 0x0B:
					return m.accessor(r.accessor(r.X).read());
				case 0x0C:
					return m.accessor(r.accessor(r.Y).read());
				case 0x0D:
					return m.accessor(r.accessor(r.Z).read());
				case 0x0E:
					return m.accessor(r.accessor(r.I).read());
				case 0x0F:
					return m.accessor(r.accessor(r.J).read());
				case 0x19:
					return m.accessor(r.accessor(r.SP).read());
				default:
					throw new RuntimeException("isRegisterMemory is broke");
			}
		}
		else if (isLiteral(resource)){
			return new LiteralAccessor(resource & 0x1F);
		}
		else if (isNextWordMemory(resource)){
			cycles++;
			return m.accessor(nextWord().read());
		}
		else if (isNextWord(resource)){
			cycles++;
			return nextWord();
		}
		else if (isNextWordPlusRegisterMemory(resource)){
			cycles++;
			int next = nextWord().read();
			switch (resource){
				case 0x10:
					return m.accessor(r.accessor(r.A).read() + next);
				case 0x11:
					return m.accessor(r.accessor(r.B).read() + next);
				case 0x12:
					return m.accessor(r.accessor(r.C).read() + next);
				case 0x13:
					return m.accessor(r.accessor(r.X).read() + next);
				case 0x14:
					return m.accessor(r.accessor(r.Y).read() + next);
				case 0x15:
					return m.accessor(r.accessor(r.Z).read() + next);
				case 0x16:
					return m.accessor(r.accessor(r.I).read() + next);
				case 0x17:
					return m.accessor(r.accessor(r.J).read() + next);
				default:
					throw new RuntimeException("isNextWordPlusRegisterMemory is broke");
			}
		}
		else if (isPop(resource)){
			return new StackPopAccessor();
		}
		else if (isPush(resource)){
			return new StackPushAccessor();
		}
		throw new RuntimeException("We don't handle that kind of resource yet " + resource);
	}

	private int popStack(){
		int pop = r.sp().read();
		r.sp().inc(1);
		return pop;
	}

	private int pushStack(){
		r.sp().inc(-1);
		return r.sp().read();
	}

	private Accessor nextWord(){
		int next = r.pc().read();
		r.pc().inc();
		return m.accessor(next);
	}

	public class StackPopAccessor implements Accessor{

		Accessor a;

		private Accessor a(){
			if (a == null)
				a = m.accessor(popStack());
			return a;
		}

		public int read(){
			return a().read();
		}

		public void write(int v){
			a().write(v);
		}

	}

	public class StackPushAccessor implements Accessor{

		Accessor a;

		private Accessor a(){
			if (a == null)
				a = m.accessor(pushStack());
			return a;
		}

		public int read(){
			return a().read();
		}

		public void write(int v){
			a().write(v);
		}

	}

	public class LiteralAccessor implements Accessor{

		private int value;

		public LiteralAccessor(int value){
			this.value = value;
		}

		public int read(){
			return value;
		}

		public void write(int value){
			// do nothing as per spec
		}

		public String toString(){
			return value + "";
		}
	}

	public static void main(String[] args){
		/*
		0x7801;	// SET A, 0xBEEF
		0xBEEF;
		0x0011;	// SET B, A
		0x7021;	// SET C, PC
		*/
		//runProgram("7801 BEEF 0011 7021");
		//runProgram("7801 BEE 0011 7021");
		//runProgram("7801 DEADBEEF 0011 7021");
		runProgram("7c01 0030 7de1 1000 0020 7803 1000 c00d\n"
					+ "7dc1 001a a861 7c01 2000 2161 2000 8463\n"
					+ "806d 7dc1 000d 9031 7c10 0018 7dc1 001a\n"
					+ "9037 61c1 0000 " // I want it to actually crash, so I put an invalid instruction at the location where "crash" would be. here's what it was supposed to be: 7dc1 001a 0000 0000 0000 0000
					);

	}

	public static void runProgram(String program){
		runProgram(Hexer.unhexArray(program));
	}

	public static void runProgram(int[] program){
		DCPU16 d = new DCPU16();
		d.setDebug(true);
		d.memory().write(0, program);
		try{
			d.run();
		}
		catch (Exception e){
			e.printStackTrace();
		}
		System.out.println(d.dump());
	}

}
