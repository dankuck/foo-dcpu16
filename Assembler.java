
import java.util.*;
import java.io.*;

class Assembler{

	static{
		String[][] precedence = {
									{ "@!", "@+", "@~", "@-" },
									{ "*", "/", "%" },
									{ "+", "-" },
									{ "<<", ">>" },
									{ "==", "!=", "<>" },	// per 0xSCA standards, == and the like are evaluated before <= and the like
									{ "<=", "<", ">=", ">" },
									{ "&" },
									{ "^" },
									{ "|" },
									{ "&&" },
									{ "||" },
									{ "^^" }, // per 0xSCA standards, it's &, ^, |, but &&, ||, ^^
									{ "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=" }
								};
		MathExpression.setPrecedence(precedence);
	}

	public static void main(String[] args)
		throws Exception
	{
		String infile = args.length > 0 ? args[0] : getFilename("Input file");
		Assembler as = new Assembler(infile);
		String outfile = args.length > 1 ? args[1] : getFilename("Output file (blank to print to screen)");
		if (outfile.length() == 0)
			System.out.println(as.assembleToHex());
		else
			as.assemble(outfile);
	}

	public static String getFilename(String ask){
		System.out.print(ask + ": ");
		return System.console().readLine();
	}

	private String filename;
	private List<List <String>> lines;
	private HashMap<String, Integer> labelsToLines = new HashMap<String, Integer>();
	private HashMap<Integer, AssembleStructure> structures = new HashMap<Integer, AssembleStructure>();
	private HashMap<Integer, Integer> linesToBytes = new HashMap<Integer, Integer>();
	private boolean labelsAligned = false;
	private int[] program;
	private HashMap<String, Integer> staticTransforms;
	private HashMap<String, Integer> plusRegisters;
	private ArrayList<String> registers;
	private Assembler.Includer bracketIncluder = new Assembler.StringIncluder();
	private Assembler.Includer stringIncluder = new Assembler.StringIncluder();
	private HashMap<String, Integer> definitions = new HashMap<String, Integer>();

	private void init(){
		if (registers == null){
			String[] rs = { "A", "B", "C", "X", "Y", "Z", "I", "J", "SP", "PC", "O" };
			registers = new ArrayList<String>();
			for (int i = 0; i < rs.length; i++)
				registers.add(rs[i]);
		}
		if (staticTransforms == null){
			staticTransforms = new HashMap<String, Integer>();
			staticTransforms.put("A", 0x0);
			staticTransforms.put("B", 0x1);
			staticTransforms.put("C", 0x2);
			staticTransforms.put("X", 0x3);
			staticTransforms.put("Y", 0x4);
			staticTransforms.put("Z", 0x5);
			staticTransforms.put("I", 0x6);
			staticTransforms.put("J", 0x7);
			staticTransforms.put("[A]", 0x8);
			staticTransforms.put("[B]", 0x9);
			staticTransforms.put("[C]", 0xA);
			staticTransforms.put("[X]", 0xB);
			staticTransforms.put("[Y]", 0xC);
			staticTransforms.put("[Z]", 0xD);
			staticTransforms.put("[I]", 0xE);
			staticTransforms.put("[J]", 0xF);
			staticTransforms.put("POP", 0x18);
			staticTransforms.put("PEEK", 0x19);
			staticTransforms.put("PUSH", 0x1A);
			// aliases to the above
				staticTransforms.put("[SP++]", 0x18);
				staticTransforms.put("[SP]", 0x19);
				staticTransforms.put("[--SP]", 0x1A);
			staticTransforms.put("SP", 0x1B);
			staticTransforms.put("PC", 0x1C);
			staticTransforms.put("O", 0x1D);
		}
		if (plusRegisters == null){
			plusRegisters = new HashMap<String, Integer>();
			plusRegisters.put("A", 0x10);
			plusRegisters.put("B", 0x11);
			plusRegisters.put("C", 0x12);
			plusRegisters.put("X", 0x13);
			plusRegisters.put("Y", 0x14);
			plusRegisters.put("Z", 0x15);
			plusRegisters.put("I", 0x16);
			plusRegisters.put("J", 0x17);
		}
	}

	public Assembler(String filename){
		init();
		this.filename = filename;
	}

	private interface Includer{

		public String pathTo(String filename);

	}

	private class StringIncluder implements Includer{

		public String pathTo(String filename){
			return filename;
		}
	}

	private String contents()
		throws Exception
	{
		File file = new File(filename);
		FileReader reader = new FileReader(file);
		char[] stringChars = new char[(int)file.length()];
		reader.read(stringChars, 0, stringChars.length);
		return new String(stringChars);
	}

	private String contents(String filename)
		throws Exception
	{
		File file = new File(filename);
		FileReader reader = new FileReader(file);
		char[] stringChars = new char[(int)file.length()];
		reader.read(stringChars, 0, stringChars.length);
		return new String(stringChars);
	}

	public String assembleToHex()
		throws Exception
	{
		return Hexer.hexArray(assemble());
	}

	public void assemble(String outfile)
		throws Exception
	{
		int[] program = assemble();
		FileOutputStream writer = new FileOutputStream(outfile);
		for (int i = 0; i < program.length; i++){
			writer.write((byte)(program[i] >> 8));
			writer.write((byte)(program[i] & 0xFF));
		}
		writer.close();
	}

	public int[] assemble()
		throws Exception
	{
		if (program != null)
			return program;
		lexize();
		structurize();
		alignBytes();
		showLabelAlignment();
		showStructure();
		programize();
		return program;
	}

	public void setIncluder(Assembler.Includer includer){
		this.bracketIncluder = includer;
	}

	private void programize()
		throws Exception
	{
		program = new int[length()];
		int position = 0;
		for (int i = 0; i < lines.size(); i++){
			AssembleStructure s = structures.get(i);
			System.arraycopy(s.toBytes(), 0, program, position, s.length());
			position += s.length();
		}
	}


	private int length()
		throws Exception
	{
		int length = 0;
		for (int i = 0; i < lines.size(); i++)
			length += structures.get(i).length();
		return length;
	}

	private void showStructure()
		throws Exception
	{
		for (int i = 0; i < lines.size(); i++){
			AssembleStructure s = structures.get(i);
			System.out.println(s + " ; " + s.length() + " byte(s) ; " + (labelsAligned ? Hexer.hexArray(s.toBytes()) + " ; " + s.toHexParts() : ""));
		}
	}

	private void showLabelAlignment()
		throws Exception
	{
		for (String label : labelsToLines.keySet())
			System.out.println(label + " " + Hexer.hex(labelToByte(label)));
	}

	private int labelToByte(String label)
		throws Exception
	{
		Integer line = labelsToLines.get(label.toUpperCase());
		if (line == null)
			throw new Exception("Undefined label " + label);
		Integer alignment = linesToBytes.get(line);
		if (alignment == null)
			throw new Exception("Label refers to non-existant line: " + label);
		return alignment;
	}

	public boolean isNumericExpression(String expression){
		return expression.matches("0x[0-9A-Fa-f]+")
		 		|| expression.matches("[0-9]+");
	}

	public int interpretNumber(String expression)
		throws Exception
	{
		if (expression.matches("0x[0-9A-Fa-f]+"))
			return Hexer.unhex(expression.substring(2));
		if (expression.matches("[0-9]+"))
			return Integer.parseInt(expression);
		throw new Exception("Not a numeric expression: " + expression);
	}

	public int interpretExpression(String expression)
		throws Exception
	{
		if (isNumericExpression(expression))
			return interpretNumber(expression);
		return labelToByte(expression);
	}

	public MathExpression buildExpression(String string)
		throws Exception
	{
		MathExpression exp = MathExpression.parse(string);
		exp.simplify(new MathExpression.LabelInterpretter(){
			public Integer interpret(String label){
				if (registers.contains(label.toUpperCase()))
					return null;
				if (label.matches("'.'"))
					return (int)label.charAt(1);
				Integer defined = definitions.get(label.toUpperCase());
				if (defined != null)
					return defined;
				if (! labelsAligned)
					return null;
				try{
					return labelToByte(label);
				}
				catch(RuntimeException e){
					throw e;
				}
				catch(Exception e){
					throw new RuntimeException(e);
				}
			}
			public Integer call(String label, MathExpression input){
				if (label.equalsIgnoreCase("isdef"))
					return definitions.get(input.value().toUpperCase()) != null || labelsToLines.get(input.value().toUpperCase()) != null ? 1 : 0;
				else
					return null;
			}
		});
		return exp;
	}

	private void alignBytes()
		throws Exception
	{
		int alignment = 0;
		for (int i = 0; i < structures.size(); i++){
			linesToBytes.put(i, alignment);
			alignment += structures.get(i).length();
		}
		labelsAligned = true;
	}

	private void structurize()
		throws Exception
	{
		for (int i = 0; i < lines.size(); i++){
			String[] t = new String[lines.get(i).size()];
			lines.get(i).toArray(t);
			if (lines.get(i).get(0).equalsIgnoreCase("DAT"))
				structures.put(i, new AssembleData(t));
			else{
				structures.put(i, new AssembleInstruction(t));
			}
		}
	}

	private void addLabel(String label, int line)
		throws Exception
	{
		if (definitions.get(label.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + label + ". Undefine it first.");
		labelsToLines.put(label.toUpperCase(), line);
	}

	private void addDefinition(String name, int value){
		definitions.put(name.toUpperCase(), value);
	}

	private void dropDefinition(String name){
		definitions.remove(name.toUpperCase());
	}

	private void lexize()
		throws Exception
	{
		lines = new ArrayList<List<String>>();
		lexize(contents());
	}

	private void lexize(String contents)
		throws Exception
	{
		contents += "\n"; // \n helps us make sure we get the last token
		StringTokenizer tokens = new StringTokenizer(contents, ";, \t\n\r\f\"\\", true);
		boolean inComment = false;
		List<String> line = new ArrayList<String>();
		String expression = "";
		boolean inQuotes = false;
		List<Boolean> ifstack = new ArrayList<Boolean>();
		List<Boolean> ifskiprest = new ArrayList<Boolean>();
		while (tokens.hasMoreTokens()){
			String token = tokens.nextToken();
			if (token.equals("\n")){
				inComment = false;
				expression = expression.trim();
				if (expression.length() > 0){
					line.add(expression);
					expression = "";
				}
				if (line.size() == 0)
					continue;
				boolean isPreprocessor = line.get(0).matches("#.*|\\..*");
				String preprocessor = line.get(0).replaceAll("^#|^\\.", "");
				if (ifstack.size() > 0){
					boolean skippingLines = ! ifstack.get(ifstack.size() - 1);
					boolean isIfRelated = isPreprocessor && (preprocessor.equals("if") || preprocessor.equals("elif") || preprocessor.equals("elseif") || preprocessor.equals("else") || preprocessor.equals("end") || preprocessor.equals("ifdef")|| preprocessor.equals("ifndef"));
					if (skippingLines && ! isIfRelated){
						line = new ArrayList<String>();
						continue;
					}
				}
				if (isPreprocessor){
					if (preprocessor.equalsIgnoreCase("include")){
						if (line.size() != 2)
							throw new Exception("Wrong token count on line " + joinLine(line));
						String path = getPath(line.get(1));
						if (! new File(path).exists())
							System.out.println(path + " not exists");
						else
							lexize(contents(path));
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("incbin")){
						if (line.size() != 2)
							throw new Exception("Wrong token count on line " + joinLine(line));
						File file = new File(getPath(line.get(1)));
						FileInputStream reader = new FileInputStream(file);
						int length = (int)file.length();
						if (length % 2 == 1)
							length++; // without this we'll leave out the last byte
						byte[] octets = new byte[length];
						reader.read(octets);
						line = new ArrayList<String>();
						line.add("DAT");
						for (int i = 0; i < octets.length / 2; i++){
							int word = ((int)(octets[i * 2] & 0xFF) << 8) | (int)(octets[i * 2 + 1] & 0xFF);
							line.add("0x" + Hexer.hex(word));
						}
					}
					else if (preprocessor.equalsIgnoreCase("def") || preprocessor.equalsIgnoreCase("define") || preprocessor.equalsIgnoreCase("equ")){
						if (line.size() < 2 || line.size() > 3)
							throw new Exception("Wrong token count on line " + joinLine(line));
						String name = line.get(1);
						int value = 1;
						if (line.size() == 3){
							MathExpression exp = buildExpression(line.get(2));
							if (exp.numericValue() == null)
								throw new Exception("Couldn't evaluate " + line.get(2) + " => " + exp);
							value = exp.numericValue();
						}
						addDefinition(name, value);
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("undef")){
						if (line.size() != 2)
							throw new Exception("Wrong token count on line " + joinLine(line));
						String name = line.get(1);
						dropDefinition(name);
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("echo")){
						line.remove(0);
						System.out.println(joinLine(line));
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("error")){
						line.remove(0);
						throw new Exception(joinLine(line));
					}
					else if (preprocessor.equalsIgnoreCase("if") || preprocessor.equalsIgnoreCase("ifdef") || preprocessor.equalsIgnoreCase("ifndef")){
						if (ifstack.size() > 0 && ! ifstack.get(ifstack.size() - 1)){
							ifstack.add(false);
							ifskiprest.add(true);
							line = new ArrayList<String>();
							continue;
						}
						if (line.size() != 2)
							throw new Exception("Wrong token count: " + joinLine(line));
						String eval = line.get(1);
						if (preprocessor.equalsIgnoreCase("ifdef"))
							eval = "isdef(" + eval + ")";
						else if (preprocessor.equalsIgnoreCase("ifndef"))
							eval = "!isdef(" + eval + ")";
						MathExpression exp = buildExpression(eval);
						if (exp.numericValue() == null)
							throw new Exception("Couldn't evaluate " + eval + " => " + exp);
						boolean result = exp.numericValue() != 0;
						ifstack.add(result);
						ifskiprest.add(result);
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("elif") || preprocessor.equalsIgnoreCase("elsif")){
						if (ifstack.size() == 0)
							throw new Exception(preprocessor + " in wrong context");
						if (ifskiprest.get(ifskiprest.size() - 1)){
							ifstack.set(ifstack.size() - 1, false);
							line = new ArrayList<String>();
							continue;
						}
						if (line.size() != 2)
							throw new Exception("Wrong token count: " + joinLine(line));
						String eval = line.get(1);
						MathExpression exp = buildExpression(eval);
						if (exp.numericValue() == null)
							throw new Exception("Couldn't evaluate " + eval + " => " + exp);
						boolean result = exp.numericValue() != 0;
						ifstack.set(ifstack.size() - 1, result);
						ifskiprest.set(ifstack.size() - 1, result);
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("else")){
						if (ifstack.size() == 0)
							throw new Exception(preprocessor + " in wrong context");
						if (ifskiprest.get(ifskiprest.size() - 1)){
							ifstack.set(ifstack.size() - 1, false);
							line = new ArrayList<String>();
							continue;
						}
						ifstack.set(ifstack.size() - 1, true);
						ifskiprest.set(ifstack.size() - 1, true);
						line = new ArrayList<String>();
						continue;
					}
					else if (preprocessor.equalsIgnoreCase("end")){
						if (ifstack.size() == 0)
							throw new Exception(preprocessor + " in wrong context");
						ifskiprest.remove(ifstack.size() - 1);
						ifstack.remove(ifstack.size() - 1);
						line = new ArrayList<String>();
						continue;
					}
					else
						throw new Exception("Preprocessor directive not handled " + preprocessor);

				}
				if (line.size() == 0)
					continue;
				if (line.get(0).equalsIgnoreCase("DAT")){
					// any size is fine
				}
				else if (line.size() > 3){
					throw new Exception("Too many tokens on line " + lines.size() + ": " + joinLine(line) + ", " + line);
				}
				lines.add(line);
				line = new ArrayList<String>();
				continue;
			}
			if (inComment)
				continue;
			if (token.equals(" ") && line.size() == 0 && expression.trim().length() > 0){ // first token ends at space, other tokens end at ,
				expression = expression.trim();
				line.add(expression);
				expression = "";
				continue;
			}
			if (token.equals(",")){
				expression = expression.trim();
				line.add(expression);
				expression = "";
				continue;
			}
			if (token.equals(";")){
				inComment = true;
				continue;
			}
			if (token.charAt(0) == '"'){
				inQuotes = true;
				boolean escaping = false;
				while (tokens.hasMoreTokens()){
					String subtoken = tokens.nextToken();
					if (! escaping && subtoken.charAt(0) == '\\'){
						escaping = true;
					}
					else if (! escaping && subtoken.charAt(0) == '"'){
						token += subtoken;
						break;
					}
					else
						escaping = false;
					token += subtoken;
				}
			}
			if (token.charAt(0) == ':' || token.charAt(token.length() - 1) == ':'){
				if (line.size() > 0)
					throw new Exception("Label not at beginning of line " + token + " in " + joinLine(line));
				addLabel(token.replaceAll("^:|:$", ""), lines.size());
				continue;
			}
			if (line.size() == 0 && token.equalsIgnoreCase("DAT")){
				line = new ArrayList<String>();
			}
			expression += token;
		}
		if (ifstack.size() > 0)
			throw new Exception("Reached end of program without finding .end");
	}

	private String getPath(String token)
		throws Exception
	{
		if (token.charAt(0) == '"')
			return stringIncluder.pathTo(token.replaceAll("^\"|\"$", ""));
		else if (token.charAt(0) == '<')
			return bracketIncluder.pathTo(token.replaceAll("^\\<|\\>$", ""));
		else
			throw new Exception("Path not understood: " + token);
	}

	public String joinLine(List<String> line){
		String str = "";
		for (String token : line)
			str += token + " ";
		return str;
	}

	private interface AssembleStructure{

		public int length()
			throws Exception;

		public String toHexParts()
			throws Exception;

		public int[] toBytes()
			throws Exception;

	}

	private class AssembleData implements AssembleStructure{

		private int[][] data;

		public AssembleData(String[] line)
			throws Exception
		{
			data = new int[line.length - 1][];
			for (int i = 1; i < line.length; i++){
				if (line[i].charAt(0) == '"'){
					data[i - 1] = getString(line[i]);
				}
				else if (isNumericExpression(line[i])){
					int[] number = new int[1];
					number[0] = interpretNumber(line[i]);
					data[i - 1] = number;
				}
				else
					throw new Exception("Data not understood: " + line[i]);
			}
		}

		private int[] getString(String code){
			HashMap<Character, Character> escapes = new HashMap<Character, Character>();
			escapes.put('n', '\n');
			escapes.put('r', '\r');
			escapes.put('t', '\t');
			escapes.put('f', '\f');
			escapes.put('0', '\0');
			escapes.put('b', '\b');
			boolean escaping = false;
			int[] string = new int[code.length()];
			int currentChar = 0;
			for (int i = 1; i < code.length() - 1; i++){ // skips the outer quotes
				char c = code.charAt(i);
				if (escaping){
					escaping = false;
					Character escaped = escapes.get(c);
					if (escaped != null)
						c = escaped;
				}
				else if (c == '\\'){
					escaping = true;
					continue;
				}
				string[currentChar++] = c;
			}
			// Looks like you have to null-char delimit your own strings if you want them null-char delimited
			// string[currentChar++] = 0; // null-char delimited I assume.
			int[] trimString = new int[currentChar];
			System.arraycopy(string, 0, trimString, 0, currentChar);
			return trimString;
		}

		public int length()
			throws Exception
		{
			int length = 0;
			for (int i = 0; i < data.length; i++)
				length += data[i].length;
			return length;
		}

		public String toHexParts()
			throws Exception
		{
			return Hexer.hexArray(toBytes());
		}

		public int[] toBytes()
			throws Exception
		{
			int[] bytes = new int[length()];
			int position = 0;
			for (int i = 0; i < data.length; i++){
				System.arraycopy(data[i], 0, bytes, position, data[i].length);
				position += data[i].length;
			}
			return bytes;
		}

	}

	private class AssembleInstruction implements AssembleStructure{

		public String instruction;
		public AssembleInstructionData a;
		public AssembleInstructionData b;

		private HashMap<String, Integer> instructionBytes;

		private void init(){
			if (instructionBytes == null){
				instructionBytes = new HashMap<String, Integer>();
				instructionBytes.put("JSR", 0x0); // other non-basic instructions will also have 0x0
				instructionBytes.put("SET", 0x1);
				instructionBytes.put("ADD", 0x2);
				instructionBytes.put("SUB", 0x3);
				instructionBytes.put("MUL", 0x4);
				instructionBytes.put("DIV", 0x5);
				instructionBytes.put("MOD", 0x6);
				instructionBytes.put("SHL", 0x7);
				instructionBytes.put("SHR", 0x8);
				instructionBytes.put("AND", 0x9);
				instructionBytes.put("BOR", 0xA);
				instructionBytes.put("XOR", 0xB);
				instructionBytes.put("IFE", 0xC);
				instructionBytes.put("IFN", 0xD);
				instructionBytes.put("IFG", 0xE);
				instructionBytes.put("IFB", 0xF);
			}
		}

		public AssembleInstruction(String[] line){
			init();
			instruction = line[0];
			if (line.length > 1)
				a = new AssembleInstructionData(line[1]);
			if (line.length > 2)
				b = new AssembleInstructionData(line[2]);
		}

		public int length()
			throws Exception
		{
			int length = 1;
			if (a != null && a.hasExtraByte())
				length++;
			if (b != null && b.hasExtraByte())
				length++;
			return length;
		}

		public String toHexParts()
			throws Exception
		{
			String hex = Hexer.hex(instructionByte());
			if (instructionByte() == 0x0)
				hex += " " + Hexer.hex(nonbasicInstructionByte());
			if (a != null)
				hex += " " + Hexer.hex(a.toByte());
			if (b != null)
				hex += " " + Hexer.hex(b.toByte());
			if (a != null && a.hasExtraByte())
				hex += " " + Hexer.hex(a.extraByte());
			if (b != null && b.hasExtraByte())
				hex += " " + Hexer.hex(b.extraByte());
			return hex;
		}

		public int[] toBytes()
			throws Exception
		{
			int[] bytes = new int[length()];
			int instruction = instructionByte();
			if (instruction == 0x0){
				instruction |= nonbasicInstructionByte() << 4;
				if (a != null)
					instruction |= a.toByte() << 10;
				// nonbasics shouldn't have a b.
			}
			else{
				if (a != null)
					instruction |= a.toByte() << 4;
				if (b != null)
					instruction |= b.toByte() << 10;
			}
			bytes[0] = instruction;
			if (a != null && a.hasExtraByte())
				bytes[1] = a.extraByte();
			if (b != null && b.hasExtraByte())
				bytes[bytes.length - 1] = b.extraByte();
			return bytes;
		}

		private int nonbasicInstructionByte(){
			return 0x01; // JSR is the only one defined
		}

		private int instructionByte()
			throws Exception
		{
			Integer inst = instructionBytes.get(instruction.toUpperCase());
			if (inst == null)
				throw new Exception("Instruction not recognized " + instruction);
			return inst;
		}

		public String toString(){
			String str = instruction;
			if (a != null)
				str += " " + a;
			if (b != null)
				str += " " + b;
			return str;
		}

		public class AssembleInstructionData{

			private String data;
			private Integer toByte;

			public AssembleInstructionData(String data){
				this.data = data.trim();
			}

			public String toString(){
				return data;
			}

			public int toByte()
				throws Exception
			{
				if (toByte != null)
					return toByte;
				Integer easy = staticTransforms.get(data.toUpperCase());
				if (easy != null)
					return toByte = easy;
				MathExpression exp = buildExpression(data);
				String register = findRegister(exp);
				if (exp.isParenthesized() && data.charAt(0) == '['){
					if (register != null){
						Integer code = plusRegisters.get(register.toUpperCase());
						if (code == null)
							throw new Exception("Cannot use " + register + " in [register+literal] in: " + data + " => " + exp);
						return toByte = code;
					}
					return toByte = 0x1E;
				}
				else{
					if (register != null){
						if (exp.value() != null && exp.value().equalsIgnoreCase(register))
							return toByte = staticTransforms.get(register.toUpperCase());
						else
							throw new Exception("Cannot use a register this way: " + data + " => " + exp);
					}
					/* the program jumps around a bit making this occassionally produce errors. just use extraByte literals for now
					if (exp.numericValue() != null && (exp.numericValue() & 0xFFFF) < 0x1F)
						return toByte = exp.numericValue() + 0x20;
					*/
					return toByte = 0x1F;
				}
			}

			private String findRegister(MathExpression exp){
				if (exp.value() != null && registers.contains(exp.value().toUpperCase())){
					return exp.value().toUpperCase();
				}
				if (exp.left() != null){
					String register = findRegister(exp.left());
					if (register != null)
						return register;
				}
				if (exp.right() != null){
					String register = findRegister(exp.right());
					if (register != null)
						return register;
				}
				return null;
			}

			public int extraByte()
				throws Exception
			{
				int toByte = toByte();
				if (! hasExtraByte())
					throw new Exception("extraByte() requested but none is appropriate");
				if (toByte >= 0x10 && toByte <= 0x17){
					MathExpression exp = buildExpression(data);
					List<String> registers = exp.labels();
					if (registers.size() != 1)
						throw new Exception(registers.size() + " registers in expression: " + data + " => " + exp);
					String register = registers.get(0);
					exp.massageIntoCommutable(); // should turn x-y into x+-y
					if (! exp.bringToTop(register))
						throw new Exception("Couldn't re-arrange expression into [literal+register] format: " + data + " => " + exp);
					if (exp.left() == null || exp.right() == null || ! "+".equals(exp.operator()))
						throw new Exception("Bad expression in [literal+register]: " + data + " yields " + exp);
					if (exp.left().value() != null && plusRegisters.get(exp.left().value().toUpperCase()) != null){
						if (exp.right().numericValue() == null)
							throw new Exception("Couldn't literalize " + exp.right() + " in " + data + " " + exp);
						return exp.right().numericValue();
					}
					if (exp.right().value() != null && plusRegisters.get(exp.right().value().toUpperCase()) != null){
						if (exp.left().numericValue() == null)
							throw new Exception("Couldn't literalize " + exp.left() + " in " + data + " " + exp);
						return exp.left().numericValue();
					}
					throw new Exception("Expression doesn't simplify to [literal+register] format: " + data + " => " + exp);
				}
				else if (toByte == 0x1E || toByte == 0x1F){
					MathExpression exp = buildExpression(data);
					if (exp.numericValue() == null)
						throw new Exception("Expression doesn't simplify to literal: " + data + " => " + exp);
					return exp.numericValue();
				}
				else
					throw new Exception("I'm broken... I don't know what to do with my own toByte==" + Hexer.hex(toByte));
			}

			public boolean hasExtraByte() // When this is called, the tokens might not be aligned yet, so we cannot call extraByte(). Besides, extraByte() calls this method.
				throws Exception
			{
				int toByte = toByte();
				return ! ((toByte >= 0x0 && toByte <= 0xF) || (toByte >= 0x18 && toByte <= 0x1D) || (toByte >= 0x20 && toByte <= 0x3F));
			}
		}
	}

}
