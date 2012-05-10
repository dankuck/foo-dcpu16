
import java.util.*;
import java.io.*;

class Assembler{

	private static boolean optimize = true;

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
	private List<TextLine> lines = new ArrayList<TextLine>();
	private String currentGlobalLabel = "";
	private HashMap<String, Integer> labelsToLines = new HashMap<String, Integer>();
	private HashMap<String, Integer> labelOffsets = new HashMap<String, Integer>();
	private HashMap<Integer, AssembleStructure> structures = new HashMap<Integer, AssembleStructure>();
	private int[] program;
	private HashMap<String, Integer> staticTransforms;
	private HashMap<String, Integer> plusRegisters;
	private ArrayList<String> registers;
	private Assembler.Includer bracketIncluder = new Assembler.StringIncluder();
	private Assembler.Includer stringIncluder = new Assembler.StringIncluder();
	private HashMap<String, Integer> definitions = new HashMap<String, Integer>();
	private HashMap<String, Macro> macros = new HashMap<String, Macro>();
	private HashMap<Integer, int[]> programBytes = new HashMap<Integer, int[]>();

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
		reader.close();
		return new String(stringChars);
	}

	private String contents(String filename)
		throws Exception
	{
		File file = new File(filename);
		FileReader reader = new FileReader(file);
		char[] stringChars = new char[(int)file.length()];
		reader.read(stringChars, 0, stringChars.length);
		reader.close();
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
		programize();
		System.out.println("Program:");
		System.out.println(Hexer.hexArray(program));
		showStructure(false);
		showLabelAlignment();
		return program;
	}

	public void setIncluder(Assembler.Includer includer){
		this.bracketIncluder = includer;
	}

	private void programize()
		throws Exception
	{
		/*
		The finalize parameter (the second parameter passed to AssemblerStructure.programize)
		says two things when true:
		(1) that all labels should have some alignment by now
		(2) that the instruction is allowed to make adjustments to size, but it should try not to

		This means that most instructions that can make adjustments will make one last size adjustment
		on the second pass, and no size adjustments on the third pass (but maybe still value adjustments),
		and no adjustments at all on the fourth pass.
		 */
		for (int i = 0; i < lines.size(); i++){
			//System.out.println("Adding line " + i);
			structures.get(i).programize(i, false);
		}
		boolean ready = false;
		for (int k = 0; k < 8 && ! ready; k++){ // eight passes is really ridiculously high, so we'll stop there and give an error.
			ready = true;
			for (int i = 0; i < lines.size(); i++){
				//System.out.println("Checking line " + i);
				if (structures.get(i).programize(i, true))
					ready = false;
			}
		}
		if (! ready)
			throw new Exception("The code won't stay put. Maybe it would behave if you check for instructions that change size based on a label later in the program.");
		program = new int[length()];
		int position = 0;
		for (int i = 0; i < lines.size(); i++){
			//System.out.println("Programizing line " + i + " position: " + position);
			System.arraycopy(programBytes.get(i), 0, program, position, programBytes.get(i).length);
			position += programBytes.get(i).length;
		}
	}

	private Integer lineToPosition(int line){
		if (line == 0)
			return 0;
		if (programBytes.get(line - 1) == null)
			return null;
		int length = 0;
		for (int i = 0; i < line; i++)
			length += programBytes.get(i).length;
		return length;
	}

	private int length()
		throws Exception
	{
		return lineToPosition(lines.size());
	}

	private void showStructure(boolean finalize)
		throws Exception
	{
		for (int i = 0; i < lines.size(); i++){
			AssembleStructure s = structures.get(i);
			System.out.println(s + " ; " + s.length(0, finalize) + " byte(s) ; " + s.toHexParts(finalize));
		}
	}

	private void showLabelAlignment()
		throws Exception
	{
		for (String label : labelsToLines.keySet())
			System.out.println(label + " " + Hexer.hex(labelToByte(label)));
	}

	private boolean labelByteIsDefined(String label){
		String globalLabel = label.charAt(0) == '_' ? currentGlobalLabel + label.toUpperCase() : label.toUpperCase();
		Integer line = labelsToLines.get(globalLabel);
		if (line == null)
			return false;
		Integer alignment = lineToPosition(line);
		if (alignment == null)
			return false;
		return true;
	}

	private int labelToByte(String label)
		throws Exception
	{
		String globalLabel = label.charAt(0) == '_' ? currentGlobalLabel + label.toUpperCase() : label.toUpperCase();
		Integer line = labelsToLines.get(globalLabel);
		if (line == null)
			throw new Exception("Undefined label " + label);
		Integer alignment = lineToPosition(line);
		if (alignment == null)
			throw new Exception("Label refers to non-existant line: " + label + " " + line);
		return alignment + labelOffsets.get(globalLabel);
	}

	public MathExpression buildExpression(String string, String globalLabel, final boolean checkDefinitions, final boolean requireLabels)
		throws Exception
	{
		currentGlobalLabel = globalLabel;
		return buildExpression(string, checkDefinitions, requireLabels);
	}

	public MathExpression buildExpression(String string, final boolean checkDefinitions, final boolean requireLabels)
		throws Exception
	{
		MathExpression exp = MathExpression.parse(string);
		simplifyExpression(exp, checkDefinitions, requireLabels);
		return exp;
	}

	public void simplifyExpression(MathExpression exp, String globalLabel, final boolean checkDefinitions, final boolean requireLabels){
		currentGlobalLabel = globalLabel;
		simplifyExpression(exp, checkDefinitions, requireLabels);
	}

	public void simplifyExpression(MathExpression exp, final boolean checkDefinitions, final boolean requireLabels){
		exp.simplify(new MathExpression.LabelInterpretter(){
			public Integer interpret(String label){
				if (registers.contains(label.toUpperCase()))
					return null;
				if (label.matches("'\\.'"))
					return (int)label.charAt(1);
				if (checkDefinitions){
					Integer defined = definitions.get(label.toUpperCase());
					if (defined != null)
						return defined;
				}
				//System.out.println("Searching for label " + label);
				if (! requireLabels && ! labelByteIsDefined(label)){
					//System.out.println("Won't find it.");
					return null;
				}
				try{
					//System.out.println("Found : " + labelToByte(label));
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
	}

	private void addLabel(String label, int line, int labelOffset)
		throws Exception
	{
		boolean isLocal = false;
		if (label.charAt(0) == '_'){
			label = currentGlobalLabel + label;
			isLocal = true;
		}
		if (definitions.get(label.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + label + " unless you undefine it first.");
		if (labelsToLines.get(label.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + label);
		if (! isLocal)
			currentGlobalLabel = label.toUpperCase() + "::";
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
		public List<TextLine> lines;
		public boolean runningFromAddedLines;
		public FlowFrame(int type){
			this.type = type;
			if (isRep() || isMacro())
				lines = new ArrayList<TextLine>();
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
		public void sub(List<String> line){
			if (isRep() || isMacro())
				lines.add(new TextLine(line));
		}
		public TextLine nextLine(){
			if (lines.size() == 0)
				throw new RuntimeException("No lines captured... not even .end");
			if (isRep() && currentLine >= lines.size()){
				currentLine = 0;
				countDown--;
			}
			return new TextLine(lines.get(currentLine++));
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

	private void subInstruction(TextLine line)
		throws Exception
	{
		int i = lines.size();
		lines.add(line);
		String instruction = line.get(0);
		if (instruction.equalsIgnoreCase(".ALIGN"))
			structures.put(i, new AssembleAlign(line));
		else if (instruction.equalsIgnoreCase("DAT") || instruction.equalsIgnoreCase(".DW") || instruction.equalsIgnoreCase(".DP") || instruction.equalsIgnoreCase(".ASCII"))
			structures.put(i, new AssembleData(line));
		else if (instruction.equalsIgnoreCase(".FILL"))
			structures.put(i, new AssembleFill(line));
		else
			structures.put(i, new AssembleInstruction(line));
	}

	private class Lexer{

		StringTokenizer tokens;
		List<FlowFrame> flowstack = new ArrayList<FlowFrame>();
		List<FlowFrame> lineSources = new ArrayList<FlowFrame>();
		List<TextLine> nextLines = new ArrayList<TextLine>();
		int org = 0;

		public Lexer(String contents){
			contents += "\n"; // \n helps us make sure we get the last token
			tokens = new StringTokenizer(contents, ";, \t\n\r\f\"\\{}", true);
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
				TextLine line;
				if (lineSource != null){
					line = lineSource.nextLine();
				}
				else if (! hasMoreLines())
					break;
				else
					line = nextLine();
				//System.out.println("- " + line);
				for (int i = flowstack.size() - 1; i >= 0; i--){
					FlowFrame r = flowstack.get(i);
					if (r.runningFromAddedLines)
						break;
					r.sub(line);
				}
				boolean isPreprocessor = isPreprocessor(line.get(0));
				String preprocessor = cleanPreprocessor(line.get(0));
				boolean skippingLines = ! flowtop.active;
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
					throw new Exception("Too many tokens on line " + lines.size() + ": " + line);
				subInstruction(line);
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

		private void preprocess(String preprocessor, TextLine line)
			throws Exception
		{
			FlowFrame flowtop = flowtop();
			FlowFrame lineSource = lineSource();
			if (preprocessor.equals("include")){
				if (line.size() != 2)
					throw new Exception("Wrong token count on line " + line);
				String path = getPath(line.get(1));
				if (! new File(path).exists())
					System.out.println(path + " not exists");
				else
					lexize(contents(path));
				return;
			}
			else if (preprocessor.equals("incbin")){
				if (line.size() != 2)
					throw new Exception("Wrong token count on line " + line);
				File file = new File(getPath(line.get(1)));
				FileInputStream reader = new FileInputStream(file);
				int length = (int)file.length();
				length += length % 2; // without this we'd leave out the last byte
				byte[] octets = new byte[length];
				reader.read(octets);
				reader.close();
				line.add("DAT");
				for (int i = 0; i < octets.length / 2; i++){
					int word = ((int)(octets[i * 2] & 0xFF) << 8) | (int)(octets[i * 2 + 1] & 0xFF);
					line.add("0x" + Hexer.hex(word));
				}
				subInstruction(line);
				return;
			}
			else if (preprocessor.equals("def") || preprocessor.equals("define") || preprocessor.equals("equ")){
				if (line.size() < 2 || line.size() > 3)
					throw new Exception("Wrong token count on line " + line);
				String[] parts = line.get(1).split("\\s");
				String name = parts[0];
				int value = 1;
				if (parts.length > 2)
					throw new Exception("Wrong token count on line " + line);
				if (parts.length == 2){
					MathExpression exp = buildExpression(parts[1], true, false);
					if (exp.numericValue() == null)
						throw new Exception("Couldn't evaluate " + parts[1] + " => " + exp);
					value = exp.numericValue();
				}
				addDefinition(name, value);
				return;
			}
			else if (preprocessor.equals("undef")){
				if (line.size() != 2)
					throw new Exception("Wrong token count on line " + line);
				String name = line.get(1);
				dropDefinition(name);
				return;
			}
			else if (preprocessor.equals("echo")){
				line.remove(0);
				System.out.println(line);
				return;
			}
			else if (preprocessor.equals("error")){
				line.remove(0);
				throw new Exception(line.toString());
			}
			else if (preprocessor.equals("if") || preprocessor.equals("ifdef") || preprocessor.equals("ifndef")){
				if (! flowtop.active){
					FlowFrame newtop = new FlowFrame(FlowFrame.IF);
					newtop.active = false;
					newtop.skiprest = true;
					//System.out.println("Adding inactive if to stack");
					flowstack.add(newtop);
					return;
				}
				if (line.size() != 2)
					throw new Exception("Wrong token count: " + line);
				String eval = line.get(1);
				if (preprocessor.equals("ifdef"))
					eval = "isdef(" + eval + ")";
				else if (preprocessor.equals("ifndef"))
					eval = "!isdef(" + eval + ")";
				MathExpression exp = buildExpression(eval, true, false);
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
					throw new Exception("Wrong token count: " + line);
				String eval = line.get(1);
				MathExpression exp = buildExpression(eval, true, false);
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
				if (! flowtop.active){
					//System.out.println("Adding inactive rep to stack");
					FlowFrame rep = new FlowFrame(FlowFrame.REP);
					rep.active = false;
					flowstack.add(rep);
					return;
				}
				if (line.size() != 2)
					throw new Exception("Wrong token count: " + line);
				MathExpression exp = buildExpression(line.get(1), true, false);
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
				if (! flowtop.active){
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
					throw new Exception("Not enough tokens: " + line);
				String eval = line.get(1);
				MathExpression exp = buildExpression(eval, true, false);
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
					|| preprocessor.equals("align")
			){
				line.set(0, "." + preprocessor.toUpperCase()); // normalize, but otherwise let the structurizer deal with it.
				subInstruction(line);
				return;
			}
			else
				throw new Exception("Preprocessor directive not handled " + preprocessor);
		}

		private boolean hasMoreLines()
			throws Exception
		{
			queueLine();
			return nextLines.size() != 0;
		}

		private TextLine nextLine()
			throws Exception
		{
			queueLine();
			if (nextLines.size() == 0)
				return null;
			TextLine line = nextLines.remove(0);
			if (line.get(0).equals("}")){
				queueLine();
				TextLine nextLine = nextLines.size() == 0 ? null : nextLines.get(0);
				if (nextLine != null && isPreprocessor(nextLine.get(0))){
					String pp = cleanPreprocessor(nextLine.get(0));
					if (pp.equals("elif") || pp.equals("elsif") || pp.equals("else"))
						return nextLine(); // in these cases the } is just syntactic sugar, in all other cases it should be interpretted as a .end
				}
				line.set(0, ".end");
			}
			return line;
		}

		private void queueLine()
			throws Exception
		{
			if (nextLines.size() != 0)
				return;
			boolean inComment = false;
			TextLine line = new TextLine();
			line.globalLabel(currentGlobalLabel);
			String expression = "";
			boolean inQuotes = false;
			while (tokens.hasMoreTokens()){
				String token = tokens.nextToken();
				if (token.charAt(0) == '{' && ! inComment){
					token = "\n";
				}
				boolean isEndCurly = token.charAt(0) == '}' && ! inComment;
				if (token.equals("\n") || isEndCurly){
					inComment = false;
					if (expression.trim().length() > 0){
						line.add(expression.trim());
						expression = "";
					}
					if (line.size() > 0)
						nextLines.add(line);
					if (isEndCurly){
						TextLine curly = new TextLine();
						curly.add("}");
						nextLines.add(curly);
					}
					if (nextLines.size() > 0)
						return;
					continue;
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
						throw new Exception("Label not at beginning of line " + token + " in " + line);
					addLabel(token.replaceAll("^:|:$", ""), lines.size(), org);
					line.globalLabel(currentGlobalLabel);
					continue;
				}
				expression += token;
			}
		}
	}

	public boolean isPreprocessor(String word){
		return word.matches("#.*|\\..*");
	}

	public String cleanPreprocessor(String word){
		return word.replaceAll("^#|^\\.", "").toLowerCase();
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
		if (line.size() == 0)
			return "";
		line = new ArrayList<String>(line);
		String last = line.remove(line.size() - 1);
		String str = "";
		for (String token : line)
			str += token + " ";
		str += last;
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
				regexes.put("\\b" + params.get(i).replace("\\", "\\\\").replaceAll("([^a-zA-Z0-9])", "\\\\$1") + "\\b", "(" + substitutions.get(i) + ")");
			FlowFrame frame = new FlowFrame(FlowFrame.MACRO);
			while (lines.hasMoreLines()){
				List<String> line = lines.nextLine();
				for (int i = 0; i < line.size(); i++){
					//System.out.println("Replacing: " + line.get(i));
					for (int j = 0; j < params.size(); j++)
						if (line.get(i).equalsIgnoreCase(params.get(j)))
							line.set(i, substitutions.get(j));
					for (Map.Entry<String, String> regex : regexes.entrySet()){
						//System.out.println(" sub " + regex.getKey() + " with " + regex.getValue());
						line.set(i, line.get(i).replaceAll(regex.getKey(), regex.getValue()));
					}
					//System.out.println("Replaced: " + line.get(i));
				}
				frame.sub(line);
			}
			lines.resetLine();
			return frame;
		}
	}

	private interface AssembleStructure{

		public int length(int currentPosition, boolean finalize)
			throws Exception;

		public String toHexParts(boolean finalize)
			throws Exception;

		public boolean programize(int linePosition, boolean finalize)
			throws Exception;

	}

	private class AssembleFill implements AssembleStructure{

		private MathExpression lengthExp;
		private MathExpression valueExp;
		private MathExpression originalLengthExp;
		private MathExpression originalValueExp;
		private TextLine line;
		private boolean lockItDown = false;

		public AssembleFill(TextLine line)
			throws Exception
		{
			this.line = line;
			if (line.size() == 1 || line.size() > 3)
				throw new Exception("Wrong token count: " + line);
			lengthExp = buildExpression(line.get(1), line.globalLabel, true, false);
			if (line.size() == 3)
				valueExp = buildExpression(line.get(2), line.globalLabel, true, false);
			originalLengthExp = lengthExp;
			originalValueExp = valueExp;
		}

		public int length(int currentPosition, boolean finalize)
			throws Exception
		{
			lengthExp = originalLengthExp.clone();
			if (lengthExp.numericValue() == null)
				simplifyExpression(lengthExp, line.globalLabel, false, finalize);
			if (lengthExp.numericValue() == null){
				if (! finalize)
					return 0;
				throw new Exception("First argument not understood: " + line.get(1) + " => " + lengthExp);
			}
			return lengthExp.numericValue();
		}

		private int value(boolean finalize)
			throws Exception
		{
			if (valueExp == null)
				return 0;
			valueExp = originalValueExp.clone();
			if (valueExp.numericValue() == null)
				simplifyExpression(valueExp, line.globalLabel, false, finalize);
			if (valueExp.numericValue() == null){
				if (! finalize)
					return 0;
				throw new Exception("First argument not understood: " + line.get(2) + " => " + valueExp);
			}
			return valueExp.numericValue() & 0xFFFF;
		}

		public String toHexParts(boolean finalize)
			throws Exception
		{
			return Hexer.hexArray(toBytes(0, false, null));
		}

		public int[] toBytes(int currentPosition, boolean finalize, int[] old)
			throws Exception
		{
			if (lockItDown && old != null)
				return old;
			int length = length(currentPosition, finalize);
			if (finalize && old.length < length)
				length += length - old.length;
			int[] bytes = new int[length];
			int value = value(finalize);
			if (value != 0)
				for (int i = 0; i < bytes.length; i++)
					bytes[i] = value;
			if (finalize)
				lockItDown = true;
			return bytes;
		}

		public boolean programize(int linePosition, boolean finalize)
			throws Exception
		{
			int[] old = programBytes.get(linePosition);
			int[] bytes = toBytes(lineToPosition(linePosition), finalize, old);
			programBytes.put(linePosition, bytes);
			return ! Arrays.equals(old, bytes);
		}
	}

	private class AssembleAlign implements AssembleStructure{

		private MathExpression boundaryExp;
		private MathExpression original;
		private TextLine line;

		public AssembleAlign(TextLine line)
			throws Exception
		{
			this.line = line;
			if (line.size() != 2)
				throw new Exception("Not enough tokens " + line);
			boundaryExp = buildExpression(line.get(1), line.globalLabel, true, false);
			original = boundaryExp;
		}

		private int boundary(boolean finalize)
			throws Exception
		{
			boundaryExp = original.clone();
			if (boundaryExp.numericValue() == null)
				simplifyExpression(boundaryExp, line.globalLabel, false, finalize);
			if (boundaryExp.numericValue() == null){
				if (! finalize)
					return 1;
				throw new Exception("Couldn't literalize " + line.get(1) + " => " + boundaryExp);
			}
			return boundaryExp.numericValue();
		}

		public int length(int position, boolean finalize)
			throws Exception
		{
			return boundary(finalize) - (position % boundary(finalize));
		}

		public String toHexParts(boolean finalize)
			throws Exception
		{
			return "";
		}

		public int[] toBytes(int currentPosition, boolean finalize, int[] old)
			throws Exception
		{
			int length = length(currentPosition, finalize);
			if (finalize && old.length > length)
				length += boundary(finalize);
			return new int[length];
		}

		public boolean programize(int linePosition, boolean finalize)
			throws Exception
		{
			int[] old = programBytes.get(linePosition);
			int[] bytes = toBytes(lineToPosition(linePosition), finalize, old);
			programBytes.put(linePosition, bytes);
			return ! Arrays.equals(old, bytes);
		}
	}

	private class AssembleData implements AssembleStructure{

		private int[][] data;
		private boolean pack = false;
		private List<MathExpression> literals = new ArrayList<MathExpression>();
		private List<MathExpression> originals;
		private TextLine line;

		public AssembleData(TextLine line)
			throws Exception
		{
			this.pack = line.remove(0).equals(".DP");
			this.line = line;
			data = new int[line.size()][];
			for (int i = 0; i < line.size(); i++){
				String word = line.get(i);
				literals.add(null);
				if (word.indexOf('"') > -1)
					data[i] = getString(word);
				else
					literals.set(i, buildExpression(word, line.globalLabel, true, false));
			}
			originals = literals;
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
					MathExpression exp = buildExpression(ORString, true, false);
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

		private void literalize(boolean finalize)
			throws Exception
		{
			for (int i = 0; i < data.length; i++){
				MathExpression exp = originals.get(i);
				if (exp == null)
					continue;
				exp = exp.clone();
				simplifyExpression(exp, line.globalLabel, false, finalize);
				if (exp.numericValue() == null){
					if (! finalize){
						data[i] = null;
						continue;
					}
					throw new Exception("Expression does not simplify to literal: " + line.get(i) + " => " + exp);
				}
				int[] number = new int[1];
				number[0] = exp.numericValue() & 0xFFFF;
				data[i] = number;
			}
		}

		public int length(int currentPosition, boolean finalize)
			throws Exception
		{
			literalize(finalize);
			int length = 0;
			for (int i = 0; i < data.length; i++)
				length += data[i].length;
			if (pack)
				return length / 2 + length % 2;
			return length;
		}

		public String toHexParts(boolean finalize)
			throws Exception
		{
			return Hexer.hexArray(toBytes(0, finalize));
		}

		public int[] toBytes(int currentPosition, boolean finalize)
			throws Exception
		{
			literalize(finalize);
			int[] bytes = new int[pack ? length(0, finalize) * 2 : length(0, finalize)];
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

		public boolean programize(int linePosition, boolean finalize)
			throws Exception
		{
			int[] old = programBytes.get(linePosition);
			int[] bytes = toBytes(lineToPosition(linePosition), finalize);
			programBytes.put(linePosition, bytes);
			return ! Arrays.equals(old, bytes);
		}

	}

	private class AssembleInstruction implements AssembleStructure{

		public String instruction;
		public AssembleInstructionData a;
		public AssembleInstructionData b;
		public TextLine line;
		private AssembleInstruction subbingFor;

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

		public AssembleInstruction(TextLine line)
			throws Exception
		{
			init();
			this.line = line;
			instruction = line.get(0);
			if (line.size() > 1)
				a = new AssembleInstructionData(line.get(1), line.globalLabel);
			if (line.size() > 2)
				b = new AssembleInstructionData(line.get(2), line.globalLabel);
		}

		public int length(int currentPosition, boolean finalize)
			throws Exception
		{
			int length = 1;
			if (a != null && a.hasExtraByte(finalize))
				length++;
			if (b != null && b.hasExtraByte(finalize))
				length++;
			return length;
		}

		public String toHexParts(boolean finalize)
			throws Exception
		{
			String hex = Hexer.hex(instructionByte());
			if (instructionByte() == 0x0)
				hex += " " + Hexer.hex(nonbasicInstructionByte());
			if (a != null)
				hex += " " + Hexer.hex(a.toByte(finalize));
			if (b != null)
				hex += " " + Hexer.hex(b.toByte(finalize));
			if (a != null && a.hasExtraByte(finalize))
				hex += " " + Hexer.hex(a.extraByte(finalize));
			if (b != null && b.hasExtraByte(finalize))
				hex += " " + Hexer.hex(b.extraByte(finalize));
			return hex;
		}

		public int[] toBytes(int currentPosition, boolean finalize)
			throws Exception
		{
			AssembleInstruction sub = substitution(currentPosition, finalize);
			if (sub != null){
				int[] subBytes = sub.toBytes(currentPosition, finalize);
				if (subbingFor == null)
					System.out.println("Optimization: Using " + sub + " in place of " + this + " : " + sub.toHexParts(finalize) + "; " + Hexer.hexArray(subBytes));
				return subBytes;
			}
			int[] bytes = new int[length(currentPosition, finalize)];
			bytes[0] = completeInstruction(finalize);
			if (a != null && a.hasExtraByte(finalize))
				bytes[1] = a.extraByte(finalize);
			if (b != null && b.hasExtraByte(finalize))
				bytes[bytes.length - 1] = b.extraByte(finalize);
			return bytes;
		}

		private int completeInstruction(boolean finalize)
			throws Exception
		{
			int instruction = instructionByte();
			if (instruction == 0x0){
				instruction |= nonbasicInstructionByte() << 4;
				if (a != null)
					instruction |= a.toByte(finalize) << 10;
				// nonbasics shouldn't have a b.
			}
			else{
				if (a != null)
					instruction |= a.toByte(finalize) << 4;
				if (b != null)
					instruction |= b.toByte(finalize) << 10;
			}
			return instruction;
		}

		private AssembleInstruction substitution(int currentPosition, boolean finalize)
			throws Exception
		{
			if (! optimize)
				return null;
			TextLine sub = null;
			int inst = completeInstruction(finalize);
			if (inst == 0x7DC1){ // SET PC, [next_word]
				sub = new TextLine(line);
				sub.set(0, "ADD");
				sub.set(2, "(" + sub.get(2) + ")-" + (currentPosition + 1));
			}
			else if ((inst & 0xFC0F) == 0x7C02 && (subbingFor == null || ! subbingFor.instruction.equalsIgnoreCase("SUB"))){ // ADD x, [next_word]
				sub = new TextLine(line);
				sub.set(0, "SUB");
				sub.set(2, "-(" + sub.get(2) + ")");
			}
			else if ((inst & 0xFC0F) == 0x7C03 && (subbingFor == null || ! subbingFor.instruction.equalsIgnoreCase("ADD"))){ // SUB x, [next_word]
				sub = new TextLine(line);
				sub.set(0, "ADD");
				sub.set(2, "-(" + sub.get(2) + ")");
			}
			else if (((inst & 0x000F) == 0x0004 || (inst & 0x000F) == 0x0005) && b != null && b.isConstant(finalize)){
				/*
				If multiplying or dividing by a constant that is a power of 2, we can turn it into a shift instead.
				In many cases, this is faster because shift is faster than DIV and we can avoid a [next_word] lookup
				*/
				int bValue = b.constantValue(finalize);
				int shift = -1;
				for (int i = 0; i <= 0xF; i++)
					if (1<<i == bValue){
						shift = i;
						break;
					}
					else if (1<<i > bValue)
						break; // passed our target
				if (shift == -1)
					return null;
				sub = new TextLine(line);
				sub.set(0, (inst & 0x000F) == 0x0004 ? "SHL" : "SHR");
				sub.set(2, shift + "");
			}
			else if ((inst & 0x000F) == 0x0006 && b != null && b.isConstant(finalize)){
				/*
				If moduloing by a constant that is a power of 2, we can turn it into an AND instead.
				This is faster because AND is faster than MOD. In one case, it will also remove a [next_word] lookup
				*/
				int bValue = b.constantValue(finalize);
				int mod = -1;
				for (int i = 0; i <= 0xF; i++)
					if (1<<i == bValue){
						mod = i;
						break;
					}
					else if (1<<i > bValue)
						break; // passed our target
				if (mod == -1)
					return null;
				sub = new TextLine(line);
				sub.set(0, "AND");
				sub.set(2, (bValue - 1) + "");
			}
			else
				return null;
			AssembleInstruction subIns = new AssembleInstruction(sub);
			subIns.subbingFor = this;
			AssembleInstruction find = subIns;
			while (find != null){
				subIns = find;
				find = find.substitution(currentPosition, finalize);
			}
			if (subIns.toBytes(currentPosition, finalize).length == 1)
				return subIns;
			return null;
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
				str += ", " + b;
			return str;
		}

		public class AssembleInstructionData{

			private String data;
			private String globalLabel;
			private MathExpression exp;
			private MathExpression original;
			private boolean forceExtraByte = false;

			public AssembleInstructionData(String data, String globalLabel)
				throws Exception
			{
				this.data = data.trim();
				exp = buildExpression(this.data, globalLabel, true, false);
				original = exp;
				//System.out.println(this.data + " => " + exp);
				this.globalLabel = globalLabel;
			}

			public String toString(){
				return data;
			}

			public void literalize(boolean finalize)
				throws Exception
			{
				//System.out.println("finalize " + (finalize ? "yes" : "no"));
				exp = original.clone(); // re-calculate from the original every time to allow for moving labels
				simplifyExpression(exp, globalLabel, false, finalize);
			}

			public int toByte(boolean finalize)
				throws Exception
			{
				Integer easy = staticTransforms.get(data.toUpperCase());
				if (easy != null)
					return easy;
				literalize(finalize);
				String register = findRegister(exp);
				if (exp.isParenthesized() && data.charAt(0) == '['){
					if (register != null){
						Integer code = plusRegisters.get(register.toUpperCase());
						if (code == null)
							throw new Exception("Cannot use " + register + " in [register+literal] in: " + data + " => " + exp);
						return code;
					}
					return 0x1E;
				}
				else{
					if (register != null){
						if (exp.value() != null && exp.value().equalsIgnoreCase(register))
							return staticTransforms.get(register.toUpperCase());
						else
							throw new Exception("Cannot use a register this way: " + data + " => " + exp);
					}
					if (forceExtraByte)
						return 0x1F;
					if (exp.numericValue() != null && (exp.numericValue() & 0xFFFF) < 0x1F)
						return (exp.numericValue() & 0xFFFF) + 0x20;
					if (finalize)
						forceExtraByte = true; // finalize means "stop changing size", so don't let this change size next time.
					return 0x1F;
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

			public int extraByte(boolean finalize)
				throws Exception
			{
				literalize(finalize);
				int toByte = toByte(finalize);
				if (! hasExtraByte(finalize))
					throw new Exception("extraByte() requested but none is appropriate");
				if (toByte >= 0x10 && toByte <= 0x17){
					List<String> rs = new ArrayList<String>();
					for (String label : exp.labels())
						if (plusRegisters.get(label) != null)
							rs.add(label);
					if (rs.size() != 1)
						throw new Exception(rs.size() + " registers in expression: " + data + " => " + exp);
					String register = rs.get(0);
					exp.massageIntoCommutable(); // should turn x-y into x+-y
					if (! exp.bringToTop(register))
						throw new Exception("Couldn't re-arrange expression into [literal+register] format: " + data + " => " + exp);
					if (exp.left() == null || exp.right() == null || ! "+".equals(exp.operator()))
						throw new Exception("Bad expression in [literal+register]: " + data + " yields " + exp);
					if (exp.left().value() != null && plusRegisters.get(exp.left().value().toUpperCase()) != null){
						if (exp.right().numericValue() == null){
							if (! finalize)
								return 0xFFFF;
							throw new Exception("Couldn't literalize " + exp.right() + " in " + data + " " + exp);
						}
						return exp.right().numericValue();
					}
					if (exp.right().value() != null && plusRegisters.get(exp.right().value().toUpperCase()) != null){
						if (exp.left().numericValue() == null){
							if (! finalize)
								return 0xFFFF;
							throw new Exception("Couldn't literalize " + exp.left() + " in " + data + " " + exp);
						}
						return exp.left().numericValue();
					}
					throw new Exception("Expression doesn't simplify to [literal+register] format: " + data + " => " + exp);
				}
				else if (toByte == 0x1E || toByte == 0x1F){
					if (exp.numericValue() == null){
						if (finalize)
							throw new Exception("Expression doesn't simplify to literal: " + data + " => " + exp);
						return 0;
					}
					return exp.numericValue();
				}
				else if (finalize)
					throw new Exception("I'm broken... I don't know what to do with my own toByte==" + Hexer.hex(toByte) + " " + (hasExtraByte(finalize) ? " (I now know that I have no extra byte, " + Hexer.hex(toByte(finalize)) + ") " + this + " of " + AssembleInstruction.this : ""));
				else
					return 0;
			}

			public boolean hasExtraByte(boolean finalize) // When this is called, the tokens might not be aligned yet, so we cannot call extraByte(). Besides, extraByte() calls this method.
				throws Exception
			{
				int toByte = toByte(finalize);
				return ! ((toByte >= 0x0 && toByte <= 0xF) || (toByte >= 0x18 && toByte <= 0x1D) || (toByte >= 0x20 && toByte <= 0x3F));
			}

			public boolean isConstant(boolean finalize)
				throws Exception
			{
				return hasExtraByte(finalize) || (toByte(finalize) >= 0x20 && toByte(finalize) <= 0x3F);
			}

			public int constantValue(boolean finalize)
				throws Exception
			{
				if (! isConstant(finalize))
					throw new Exception("Not a constant, check isConstant first");
				if (hasExtraByte(finalize))
					return extraByte(finalize);
				return toByte(finalize) - 0x20;
			}
		}


		public boolean programize(int linePosition, boolean finalize)
			throws Exception
		{
			int[] old = programBytes.get(linePosition);
			int[] bytes = toBytes(lineToPosition(linePosition), finalize);
			programBytes.put(linePosition, bytes);
			return ! Arrays.equals(old, bytes);
		}
	}

	private class TextLine extends ArrayList<String>{

		private String globalLabel;

		public TextLine(){
			super();
		}

		public TextLine(List<String> copy){
			super(copy);
		}

		public TextLine(TextLine copy){
			super(copy);
			globalLabel = copy.globalLabel;
		}

		public void globalLabel(String globalLabel){
			this.globalLabel = globalLabel;
		}

		public String globalLabel(){
			return globalLabel;
		}

		public String toString(){
			return joinLine(this);
		}
	}

}
