
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
	private HashMap<String, Integer> labelOffsets = new HashMap<String, Integer>();
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
	private HashMap<String, Macro> macros = new HashMap<String, Macro>();

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
			System.arraycopy(s.toBytes(position), 0, program, position, s.length());
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
		return alignment + labelOffsets.get(label.toUpperCase());
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
				if (label.matches("'\\.'"))
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
			String instruction = lines.get(i).get(0);
			if (instruction.equalsIgnoreCase("DAT") || instruction.equalsIgnoreCase(".DW") || instruction.equalsIgnoreCase(".DP") || instruction.equalsIgnoreCase(".ASCII"))
				structures.put(i, new AssembleData(lines.get(i), instruction.equalsIgnoreCase(".DP")));
			else if (instruction.equalsIgnoreCase(".FILL"))
				structures.put(i, new AssembleFill(lines.get(i)));
			else{
				structures.put(i, new AssembleInstruction(t));
			}
		}
	}

	private void addLabel(String label, int line, int labelOffset)
		throws Exception
	{
		if (definitions.get(label.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + label + " unless you undefine it first.");
		if (labelsToLines.get(label.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + label);
		labelsToLines.put(label.toUpperCase(), line);
		labelOffsets.put(label.toUpperCase(), labelOffset);
	}

	private void addDefinition(String name, int value)
		throws Exception
	{
		if (definitions.get(name.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + name + " unless you undefine it first.");
		definitions.put(name.toUpperCase(), value);
	}

	private void dropDefinition(String name){
		definitions.remove(name.toUpperCase());
	}

	private void addMacro(String name, Macro macro){
		macros.put(name.toUpperCase(), macro);
	}

	private void lexize()
		throws Exception
	{
		lines = new ArrayList<List<String>>();
		lexize(contents());
	}

	private class FlowFrame{
		final static public int BOTTOM = 1;
		final static public int REP = 2;
		final static public int MACRO = 3;
		final static public int IF = 4;
		private int type;
		public boolean active;
		public boolean skiprest;
		public int countDown;
		public int currentLine;
		public List<List<String>> lines;
		public boolean runningFromAddedLines;
		public FlowFrame(int type){
			this.type = type;
			if (isRep() || isMacro())
				lines = new ArrayList<List<String>>();
		}
		public boolean isIf(){
			return type == IF;
		}
		public boolean isRep(){
			return type == REP;
		}
		public boolean isMacro(){
			return type == MACRO;
		}
		public boolean isBottom(){
			return type == BOTTOM;
		}
		public boolean active(){
			return active;
		}
		public void addLine(List<String> line){
			if (isRep() || isMacro())
				lines.add(new ArrayList<String>(line));
		}
		public List<String> nextLine(){
			if (lines.size() == 0)
				throw new RuntimeException("No lines captured... not even .end");
			if (isRep() && currentLine >= lines.size()){
				currentLine = 0;
				countDown--;
			}
			return new ArrayList<String>(lines.get(currentLine++));
		}
		public boolean hasMoreLines(){
			if (isRep())
				return countDown > 0 || currentLine < lines.size();
			return currentLine < lines.size();
		}
		public void resetLine(){
			currentLine = 0;
		}
	}

	private class Lexer{

		StringTokenizer tokens;
		List<FlowFrame> flowstack = new ArrayList<FlowFrame>();
		List<FlowFrame> lineSources = new ArrayList<FlowFrame>();
		List<String> nextLine;
		int org = 0;

		public Lexer(String contents){
			contents += "\n"; // \n helps us make sure we get the last token
			tokens = new StringTokenizer(contents, ";, \t\n\r\f\"\\", true);
		}

		private FlowFrame flowtop(){
			return flowstack.size() > 0 ? flowstack.get(flowstack.size() - 1) : null;
		}

		private FlowFrame lineSource(){
			return lineSources.size() > 0 ? lineSources.get(lineSources.size() - 1) : null;
		}

		public void lex()
			throws Exception
		{
			FlowFrame bottom = new FlowFrame(FlowFrame.BOTTOM);
			bottom.active = true;
			flowstack.add(bottom);
			int startSize = flowstack.size();
			while (true){
				FlowFrame flowtop = flowtop();
				if (flowtop == null)
					throw new Exception("Something went wrong with the flowstack.");
				FlowFrame lineSource = lineSource();
				List<String> line;
				if (lineSource != null){
					line = lineSource.nextLine();
				}
				else if (! hasMoreLines())
					break;
				else
					line = nextLine();
				System.out.println("- " + joinLine(line));
				for (int i = flowstack.size() - 1; i >= 0; i--){
					FlowFrame r = flowstack.get(i);
					if (r.runningFromAddedLines)
						break;
					r.addLine(line);
				}
				boolean isPreprocessor = line.get(0).matches("#.*|\\..*");
				String preprocessor = line.get(0).replaceAll("^#|^\\.", "").toLowerCase();
				boolean skippingLines = ! flowtop.active();
				boolean isFlowRelated = isPreprocessor && (preprocessor.equals("if") || preprocessor.equals("elif") || preprocessor.equals("elseif") || preprocessor.equals("else") || preprocessor.equals("ifdef")|| preprocessor.equals("ifndef") || preprocessor.equals("rep") || preprocessor.equals("macro") || preprocessor.equals("end"));
				if (skippingLines && ! isFlowRelated)
					continue;
				if (isPreprocessor){
					preprocess(preprocessor, line);
					continue;
				}
				if (line.get(0).indexOf('(') >= 0){
					FlowFrame calledMacro = callMacro(line);
					calledMacro.active = true;
					calledMacro.runningFromAddedLines = true;
					flowstack.add(calledMacro);
					lineSources.add(calledMacro);
					continue;
				}
				if (line.size() == 0)
					throw new Exception("Somehow ended up with a 0 length line");
				if (! line.get(0).equalsIgnoreCase("DAT") && line.size() > 3)
					throw new Exception("Too many tokens on line " + lines.size() + ": " + joinLine(line) + ", " + line);
				lines.add(line);
			}
			if (flowstack.size() < startSize)
				throw new Exception("There are too many ends");
			if (flowstack.size() > startSize)
				throw new Exception("There are not enough ends");
		}

		private FlowFrame callMacro(List<String> line)
			throws Exception
		{
			List<String> params = interpretMacroParts(line);
			//System.out.println("Calling " + joinLine(params));
			String name = params.remove(0);
			Macro macro = macros.get(name.toUpperCase());
			if (macro == null)
				throw new Exception("Undefined macro");
			return macro.interpolate(params);
		}

		private void preprocess(String preprocessor, List<String> line)
			throws Exception
		{
			FlowFrame flowtop = flowtop();
			FlowFrame lineSource = lineSource();
			if (preprocessor.equals("include")){
				if (line.size() != 2)
					throw new Exception("Wrong token count on line " + joinLine(line));
				String path = getPath(line.get(1));
				if (! new File(path).exists())
					System.out.println(path + " not exists");
				else
					lexize(contents(path));
				return;
			}
			else if (preprocessor.equals("incbin")){
				if (line.size() != 2)
					throw new Exception("Wrong token count on line " + joinLine(line));
				File file = new File(getPath(line.get(1)));
				FileInputStream reader = new FileInputStream(file);
				int length = (int)file.length();
				if (length % 2 == 1)
					length++; // without this we'd leave out the last byte
				byte[] octets = new byte[length];
				reader.read(octets);
				line.add("DAT");
				for (int i = 0; i < octets.length / 2; i++){
					int word = ((int)(octets[i * 2] & 0xFF) << 8) | (int)(octets[i * 2 + 1] & 0xFF);
					line.add("0x" + Hexer.hex(word));
				}
				lines.add(line);
				return;
			}
			else if (preprocessor.equals("def") || preprocessor.equals("define") || preprocessor.equals("equ")){
				if (line.size() < 2 || line.size() > 3)
					throw new Exception("Wrong token count on line " + joinLine(line));
				String[] parts = line.get(1).split("\\s");
				String name = parts[0];
				int value = 1;
				if (parts.length > 2)
					throw new Exception("Wrong token count on line " + joinLine(line));
				if (parts.length == 2){
					MathExpression exp = buildExpression(parts[1]);
					if (exp.numericValue() == null)
						throw new Exception("Couldn't evaluate " + parts[1] + " => " + exp);
					value = exp.numericValue();
				}
				addDefinition(name, value);
				return;
			}
			else if (preprocessor.equals("undef")){
				if (line.size() != 2)
					throw new Exception("Wrong token count on line " + joinLine(line));
				String name = line.get(1);
				dropDefinition(name);
				return;
			}
			else if (preprocessor.equals("echo")){
				line.remove(0);
				System.out.println(joinLine(line));
				return;
			}
			else if (preprocessor.equals("error")){
				line.remove(0);
				throw new Exception(joinLine(line));
			}
			else if (preprocessor.equals("if") || preprocessor.equals("ifdef") || preprocessor.equals("ifndef")){
				if (! flowtop.active()){
					FlowFrame newtop = new FlowFrame(FlowFrame.IF);
					newtop.active = false;
					newtop.skiprest = true;
					//System.out.println("Adding inactive if to stack");
					flowstack.add(newtop);
					return;
				}
				if (line.size() != 2)
					throw new Exception("Wrong token count: " + joinLine(line));
				String eval = line.get(1);
				if (preprocessor.equals("ifdef"))
					eval = "isdef(" + eval + ")";
				else if (preprocessor.equals("ifndef"))
					eval = "!isdef(" + eval + ")";
				MathExpression exp = buildExpression(eval);
				if (exp.numericValue() == null)
					throw new Exception("Couldn't evaluate " + eval + " => " + exp);
				boolean result = exp.numericValue() != 0;
				//System.out.println("Adding active if to stack");
				FlowFrame newtop = new FlowFrame(FlowFrame.IF);
				newtop.active = result;
				newtop.skiprest = result;
				flowstack.add(newtop);
				return;
			}
			else if (preprocessor.equals("elif") || preprocessor.equals("elsif")){
				if (! flowtop.isIf())
					throw new Exception(preprocessor + " in wrong context");
				if (flowtop.skiprest){
					flowtop.active = false;
					return;
				}
				if (line.size() != 2)
					throw new Exception("Wrong token count: " + joinLine(line));
				String eval = line.get(1);
				MathExpression exp = buildExpression(eval);
				if (exp.numericValue() == null)
					throw new Exception("Couldn't evaluate " + eval + " => " + exp);
				boolean result = exp.numericValue() != 0;
				flowtop.active = result;
				flowtop.skiprest = result;
				return;
			}
			else if (preprocessor.equals("else")){
				if (! flowtop.isIf())
					throw new Exception(preprocessor + " in wrong context");
				if (flowtop.skiprest){
					flowtop.active = false;
					return;
				}
				flowtop.active = true;
				flowtop.skiprest = true;
				return;
			}
			else if (preprocessor.equals("rep")){
				if (! flowtop.active()){
					//System.out.println("Adding inactive rep to stack");
					FlowFrame rep = new FlowFrame(FlowFrame.REP);
					rep.active = false;
					flowstack.add(rep);
					return;
				}
				if (line.size() != 2)
					throw new Exception("Wrong token count: " + joinLine(line));
				MathExpression exp = buildExpression(line.get(1));
				if (exp.numericValue() == null)
					throw new Exception("Expression doesn't literalize: " + line.get(1) + " => " + exp);
				//System.out.println("Adding active rep to stack");
				FlowFrame rep = new FlowFrame(FlowFrame.REP);
				rep.active = false;
				rep.countDown = exp.numericValue();
				flowstack.add(rep);
				return;
			}
			else if (preprocessor.equals("macro")){
				if (! flowtop.active()){
					//System.out.println("Adding inactive rep to stack");
					FlowFrame mac = new FlowFrame(FlowFrame.MACRO);
					mac.active = false;
					flowstack.add(mac);
					return;
				}
				line.remove(0);
				List<String> params = interpretMacroParts(line);
				String name = params.remove(0);
				FlowFrame mac = new FlowFrame(FlowFrame.MACRO);
				mac.active = false;
				Macro m = new Macro(name, params, mac);
				addMacro(name, m);
				flowstack.add(mac);
				return;
			}
			else if (preprocessor.equals("end")){
				if (flowtop.isBottom())
					throw new Exception(preprocessor + " in wrong context");
				if (! flowtop.isRep() || flowtop.countDown <= 0){
					//System.out.println("Removing " + (flowtop.isRep() ? "rep" : "if") + " from stack");
					if (lineSource == flowtop)
						lineSources.remove(lineSources.size() - 1);
					flowstack.remove(flowstack.size() - 1);
				}
				else if (flowtop.isRep() && ! flowtop.runningFromAddedLines){
					//System.out.println("First repeat (second run)");
					flowtop.runningFromAddedLines = true; // start repeating.
					flowtop.countDown--;
					flowtop.active = true;
					lineSources.add(flowtop);
				}
				if (flowtop.countDown > 0){
					//System.out.println("Looping: " + flowtop.countDown);
				}
				return;
			}
			else if (preprocessor.equals("org")){
				if (line.size() != 2)
					throw new Exception("Not enough tokens: " + joinLine(line));
				String eval = line.get(1);
				MathExpression exp = buildExpression(eval);
				if (exp.numericValue() == null)
					throw new Exception("Couldn't evaluate " + eval + " => " + exp);
				org = exp.numericValue();
				return;
			}
			else if (
					preprocessor.equals("dw")
					|| preprocessor.equals("dp")
					|| preprocessor.equals("fill")
					|| preprocessor.equals("ascii")
					// || preprocessor.equals("align") ... hmm ... this one's gonna require some rework of the program
			){
				line.set(0, "." + preprocessor.toUpperCase()); // normalize, but otherwise let the structurizer deal with it.
				lines.add(line);
				return;
			}
			else
				throw new Exception("Preprocessor directive not handled " + preprocessor);
		}

		private boolean hasMoreLines()
			throws Exception
		{
			queueLine();
			return nextLine != null;
		}

		private List<String> nextLine()
			throws Exception
		{
			queueLine();
			List<String> line = nextLine;
			nextLine = null;
			return line;
		}

		private void queueLine()
			throws Exception
		{
			if (nextLine != null)
				return;
			boolean inComment = false;
			List<String> line = new ArrayList<String>();
			String expression = "";
			boolean inQuotes = false;
			while (tokens.hasMoreTokens()){
				String token = tokens.nextToken();
				if (token.equals("\n")){
					inComment = false;
					if (expression.trim().length() > 0){
						line.add(expression.trim());
						expression = "";
					}
					if (line.size() == 0)
						continue;
					nextLine = line;
					return;
				}
				if (inComment)
					continue;
				if (token.equals(",") || (token.equals(" ") && line.size() == 0 && expression.trim().length() > 0)){ // first token ends at space, other tokens end at ,
					expression = expression.trim();
					line.add(expression.trim());
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
					addLabel(token.replaceAll("^:|:$", ""), lines.size(), org);
					continue;
				}
				expression += token;
			}
		}

	}

	public List<String> interpretMacroParts(List<String> line)
		throws Exception
	{
		if (line.size() == 0)
			throw new Exception("Not enough tokens on line " + joinLine(line));
		String name = "";
		String firstToken = line.get(0);
		for (int i = 0; i < firstToken.length(); i++){
			char c = firstToken.charAt(i);
			if (c == '('){
				firstToken = firstToken.substring(i);
				break;
			}
			else
				name += c;
		}
		if (firstToken.length() == 0 || firstToken.charAt(0) != '(' || name.length() == 0)
			throw new Exception("Macro definition is malformed");
		List<String> params = new ArrayList<String>();
		if (line.size() == 1){
			String param = firstToken.substring(1, firstToken.length() - 1);
			if (param.length() > 0)
				params.add(param);
		}
		else{
			String param = firstToken.substring(1);
			if (param.length() > 0)
				params.add(param);
			for (int i = 1; i < line.size() - 1; i++)
				params.add(line.get(i));
			String lastToken = line.get(line.size() - 1);
			params.add(lastToken.substring(0, lastToken.length() - 1));
		}
		List<String> parts = new ArrayList<String>();
		parts.add(name);
		parts.addAll(params);
		return parts;
	}

	private void lexize(String contents)
		throws Exception
	{
		new Lexer(contents).lex();
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

	private class Macro{

		private String name;
		private List<String> params;
		private FlowFrame lines;

		public Macro(String name, List<String> params, FlowFrame lines){
			this.name = name;
			this.params = params;
			this.lines = lines;
		}

		public FlowFrame interpolate(List<String> substitutions)
			throws Exception
		{
			if (params.size() != substitutions.size())
				throw new Exception("Substitutions size doesn't match parameter size");
			HashMap<String, String> regexes = new HashMap<String, String>();
			for (int i = 0; i < params.size(); i++)
				regexes.put("\\b" + params.get(i).replace("\\", "\\\\").replaceAll("([^a-zA-Z0-9])", "\\\\$1") + "\\b", substitutions.get(i));
			FlowFrame frame = new FlowFrame(FlowFrame.MACRO);
			while (lines.hasMoreLines()){
				List<String> line = lines.nextLine();
				for (int i = 0; i < line.size(); i++){
					System.out.println("Replacing: " + line.get(i));
					for (Map.Entry<String, String> regex : regexes.entrySet()){
						System.out.println(" sub " + regex.getKey() + " with " + regex.getValue());
						line.set(i, line.get(i).replaceAll(regex.getKey(), regex.getValue()));
					}
					System.out.println("Replaced: " + line.get(i));
				}
				frame.addLine(line);
			}
			lines.resetLine();
			return frame;
		}
	}

	private interface AssembleStructure{

		public int length()
			throws Exception;

		public String toHexParts()
			throws Exception;

		public int[] toBytes()
			throws Exception;

		public int[] toBytes(int position)
			throws Exception;

	}

	private class AssembleFill implements AssembleStructure{

		private int length;
		private int value = 0;

		public AssembleFill(List<String> line)
			throws Exception
		{
			if (line.size() == 1 || line.size() > 3)
				throw new Exception("Wrong token count: " + joinLine(line));
			MathExpression lengthExp = buildExpression(line.get(1));
			if (lengthExp.numericValue() == null)
				throw new Exception("First argument not understood: " + line.get(1) + " => " + lengthExp);
			length = lengthExp.numericValue();
			if (line.size() == 3){
				MathExpression valueExp = buildExpression(line.get(2));
				if (valueExp.numericValue() == null)
					throw new Exception("First argument not understood: " + line.get(2) + " => " + valueExp);
				value = valueExp.numericValue() & 0xFFFF;
			}
		}

		public int length()
			throws Exception
		{
			return length;
		}

		public String toHexParts()
			throws Exception
		{
			return Hexer.hexArray(toBytes());
		}

		public int[] toBytes(int position)
			throws Exception
		{
			return toBytes();
		}

		public int[] toBytes()
			throws Exception
		{
			int[] bytes = new int[length];
			if (value != 0)
				for (int i = 0; i < bytes.length; i++)
					bytes[i] = value;
			return bytes;
		}

	}

	private class AssembleData implements AssembleStructure{

		private int[][] data;
		private boolean pack = false;

		public AssembleData(List<String> line, boolean pack)
			throws Exception
		{
			this.pack = pack;
			line.remove(0);
			data = new int[line.size()][];
			int i = 0;
			for (String word : line){
				if (word.indexOf('"') > -1){
					data[i++] = getString(word);
				}
				else if (isNumericExpression(word)){
					int[] number = new int[1];
					MathExpression exp = buildExpression(word);
					if (exp.numericValue() == null)
						throw new Exception("Expression does not simplify to literal: " + word + " => " + exp);
					number[0] = exp.numericValue() & 0xFFFF;
					data[i++] = number;
				}
				else
					throw new Exception("Data not understood: " + word);
			}
		}

		private int[] getString(String code)
			throws Exception
		{
			boolean pack = false;
			boolean swap = false;
			boolean zeroTerminate = false;
			boolean wordZeroTerminate = false;
			boolean bytePascalLength = false;
			boolean wordPascalLength = false;
			int OR = 0;
			int quoteStart = 0;
			for (int i = 0; i < code.length(); i++){
				char c = code.charAt(i);
				if (c == 'k'){
					if (pack && swap)
						throw new Exception("k and s are incompatible");
					pack = true;
					swap = false;
				}
				else if (c == 's'){
					if (pack && ! swap)
						throw new Exception("k and s are incompatible");
					pack = true;
					swap = true;
				}
				else if (c == 'z'){
					if (wordZeroTerminate)
						throw new Exception("z and x are incompatible");
					zeroTerminate = true;
				}
				else if (c == 'x'){
					if (zeroTerminate)
						throw new Exception("z and x are incompatible");
					wordZeroTerminate = true;
				}
				else if (c == 'a'){
					if (wordPascalLength)
						throw new Exception("a and p are incompatible");
					bytePascalLength = true;
				}
				else if (c == 'p'){
					if (bytePascalLength)
						throw new Exception("a and p are incompatible");
					wordPascalLength = true;
				}
				else if (c == '<'){
					String ORString = "";
					for (i++; i < code.length(); i++)
						if (code.charAt(i) == '>')
							break;
						else
							ORString += code.charAt(i);
					MathExpression exp = buildExpression(ORString);
					if (exp.numericValue() == null)
						throw new Exception("OR value doesn't simplify to literal: " + ORString + " => " + exp);
					OR = exp.numericValue();
				}
				else if (c == '"'){
					quoteStart = i;
					break;
				}
			}
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
			for (int i = quoteStart + 1; i < code.length() - 1; i++){ // skips the outer quotes
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
			int[] trimString = new int[currentChar];
			System.arraycopy(string, 0, trimString, 0, currentChar);
			string = trimString;
			if (OR > 0)
				for (int i = 0; i < string.length; i++)
					string[i] |= OR;
			int stringLength = string.length;
			int bytesLength;
			if (pack){
				boolean byteLengthMarker = zeroTerminate || bytePascalLength;
				boolean wordLengthMarker = ! byteLengthMarker && (wordZeroTerminate || wordPascalLength);
				int contentLength = stringLength + (byteLengthMarker ? 1 : 0);
				bytesLength = contentLength / 2 + contentLength % 2 + (wordLengthMarker ? 1 : 0);
			}
			else
				bytesLength = stringLength + (zeroTerminate || wordZeroTerminate || bytePascalLength || wordPascalLength ? 1 : 0);
			int[] bytes = new int[bytesLength];
			if (pack){
				int bytesStart = 0;
				int stringStart = 0;
				if (wordPascalLength){
					bytes[0] = stringLength;
					bytesStart = 1;
				}
				else if (bytePascalLength){
					if (swap)
						bytes[0] = (string[0] << 8) | (stringLength & 0xFF);
					else
						bytes[0] = (stringLength << 8) | (string[0] & 0xFF);
					bytesStart = 1;
					stringStart = 1;
				}
				for (int i = stringStart; i < string.length; i++){
					int bytesI = (i - stringStart) / 2 + bytesStart;
					int high = (i - stringStart) % 2;
					if (high == (swap ? 1 : 0))
						bytes[bytesI] |= string[i] << 8;
					else
						bytes[bytesI] |= string[i] & 0xFF;
				}
				return bytes;
			}
			else{
				int start = 0;
				if (wordPascalLength || bytePascalLength){
					bytes[0] = stringLength;
					start = 1;
				}
				for (int i = 0; i < string.length; i++)
					bytes[i + start] = string[i];
				return bytes;
			}
		}

		public int length()
			throws Exception
		{
			int length = 0;
			for (int i = 0; i < data.length; i++)
				length += data[i].length;
			if (pack)
				return length / 2 + length % 2;
			return length;
		}

		public String toHexParts()
			throws Exception
		{
			return Hexer.hexArray(toBytes());
		}

		public int[] toBytes(int position)
			throws Exception
		{
			return toBytes();
		}

		public int[] toBytes()
			throws Exception
		{
			int[] bytes = new int[pack ? length() * 2 : length()];
			int position = 0;
			for (int i = 0; i < data.length; i++){
				System.arraycopy(data[i], 0, bytes, position, data[i].length);
				position += data[i].length;
			}
			if (pack)
				return packed(bytes);
			return bytes;
		}

		private int[] packed(int[] bytes){
			int[] packed = new int[bytes.length / 2];
			for (int i = 0; i < packed.length; i++)
				packed[i] = (bytes[i * 2] << 8) | (bytes[i * 2 + 1] & 0xFF);
			return packed;
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

		public int[] toBytes(int position)
			throws Exception
		{
			return toBytes();
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
