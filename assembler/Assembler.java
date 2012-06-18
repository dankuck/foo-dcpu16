
package assembler;

import java.util.*;
import java.io.*;
import mathExpression.*;
import hexer.*;

public class Assembler{

	private static boolean optimize = true;
	private static HashMap<String, Integer> instructionBytes = new HashMap<String, Integer>();

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

	private String filename;
	private String contents = null;
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

	public Assembler(String contentsOrFilename, boolean contents){
		init();
		if (contents){
			this.contents = contentsOrFilename;
			this.filename = "eval";
		}
		else
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

	public String contents()
		throws Exception
	{
		if (contents != null)
			return contents;
		return contents(filename);
	}

	public String contents(String filename)
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

	public void debug(String debugfile)
		throws Exception
	{
		FileWriter writer = new FileWriter(debugfile);
		writer.write(debug());
		writer.close();
	}

	public String debug()
		throws Exception
	{
		ArrayList<String> column1 = new ArrayList<String>();
		ArrayList<String> column2 = new ArrayList<String>();
		ArrayList<String> column3 = new ArrayList<String>();
		for (int i = 0; i < lines.size(); i++){
			column1.add(lines.get(i).toString());
			column2.add(structures.get(i).toLastString());
			column3.add(Hexer.hexArray(programBytes.get(i)));
		}
		int length1 = columnMaxLength(column1);
		int length2 = columnMaxLength(column2);
		//int length3 = columnMaxLength(column3);
		String debug = "";
		for (int i = 0; i < column1.size(); i++){
			int position = lineToPosition(i);
			debug += " ";
			debug += column1.get(i);
			debug += spaces(length1 - column1.get(i).length());
			debug += " ; ";
			debug += column2.get(i);
			debug += spaces(length2 - column2.get(i).length());
			debug += " ; ";
			debug += spaces(5 - (position + "").length());
			debug += position;
			debug += " ";
			debug += Hexer.hex(position);
			debug += ": ";
			debug += column3.get(i);
			debug += "\n";
		}
		return debug;
	}

	private int columnMaxLength(List<String> column){
		int max = 0;
		for (String s : column)
			if (max < s.length())
				max = s.length();
		return max;
	}

	private String spaces(int length){
		String spaces = "";
		for (int i = 0; i < length; i++)
			spaces += " ";
		return spaces;
	}

	public void setIncluder(Assembler.Includer includer){
		this.bracketIncluder = includer;
	}

	private void programize()
		throws Exception
	{
		TextLine line = null;
		try{
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
				line = structures.get(i).getLine();
				structures.get(i).programize(i, false);
			}
			showLabelAlignment();
			boolean ready = false;
			for (int k = 0; k < 8 && ! ready; k++){ // eight passes is really ridiculously high, so we'll stop there and give an error.
				ready = true;
				for (int i = 0; i < lines.size(); i++){
					line = structures.get(i).getLine();
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
		catch (Exception e){
			throw new Exception("Exception at " + line.lineFile() + " " + line.lineNumber() + " in " + line.globalLabel() + ": " + line + " : " + e.getMessage(), e);
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
		simplifyExpression(exp, checkDefinitions, requireLabels, null);
		return exp;
	}

	public void simplifyExpression(MathExpression exp, String globalLabel, final boolean checkDefinitions, final boolean requireLabels, final Scope scope){
		currentGlobalLabel = globalLabel;
		simplifyExpression(exp, checkDefinitions, requireLabels, scope);
	}

	public void simplifyExpression(MathExpression exp, final boolean checkDefinitions, final boolean requireLabels, final Scope scope){
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
				String labelToFind = label;
				for (Scope scopeFind = new Scope(scope); scopeFind.size() > 0; scopeFind.pop()){
					if (labelByteIsDefined(scopeFind + label)){
						labelToFind = scopeFind + label;
						break;
					}
				}
				//System.out.println("Searching for label " + label);
				if (! requireLabels && ! labelByteIsDefined(labelToFind)){
					//System.out.println("Won't find it.");
					return null;
				}
				try{
					//System.out.println("Found : " + labelToByte(label));
					return labelToByte(labelToFind);
				}
				catch(RuntimeException e){
					throw e;
				}
				catch(Exception e){
					throw new RuntimeException(e + " " + scope + labelToFind);
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

	public void addLabel(String label, int labelOffset)
		throws Exception
	{
		int line = lines.size();
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

	public void addDefinition(String name, int value)
		throws Exception
	{
		if (definitions.get(name.toUpperCase()) != null)
			throw new Exception("Cannot redefine " + name + " unless you undefine it first.");
		definitions.put(name.toUpperCase(), value);
	}

	public void dropDefinition(String name){
		definitions.remove(name.toUpperCase());
	}

	public void addMacro(String name, Macro macro){
		macros.put(name.toUpperCase(), macro);
	}

	public Macro getMacro(String name){
		return macros.get(name.toUpperCase());
	}

	private void lexize()
		throws Exception
	{
		lexize(contents());
	}

	public void addInstruction(TextLine line)
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

	public String currentGlobalLabel(){
		return currentGlobalLabel;
	}

	public void currentGlobalLabel(String set){
		currentGlobalLabel = set;
	}

	public List<String> interpretMacroParts(List<String> line)
		throws Exception
	{
		if (line.size() == 0)
			throw new Exception("Not enough tokens on line " + TextLine.joinLine(line));
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
			throw new Exception("Macro definition is malformed: " + line);
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
		new Lexer(this, contents, filename).lex();
	}

	public String getPath(String token)
		throws Exception
	{
		if (token.charAt(0) == '"')
			return stringIncluder.pathTo(token.replaceAll("^\"|\"$", ""));
		else if (token.charAt(0) == '<')
			return bracketIncluder.pathTo(token.replaceAll("^\\<|\\>$", ""));
		else
			throw new Exception("Path not understood: " + token);
	}

	private interface AssembleStructure{

		public int length(int currentPosition, boolean finalize)
			throws Exception;

		public String toHexParts(boolean finalize)
			throws Exception;

		public boolean programize(int linePosition, boolean finalize)
			throws Exception;

		public String toLastString();

		public TextLine getLine();
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
			lengthExp = buildExpression(line.get(1), line.globalLabel(), true, false);
			if (line.size() == 3)
				valueExp = buildExpression(line.get(2), line.globalLabel(), true, false);
			originalLengthExp = lengthExp;
			originalValueExp = valueExp;
		}

		public int length(int currentPosition, boolean finalize)
			throws Exception
		{
			lengthExp = originalLengthExp.clone();
			if (lengthExp.numericValue() == null)
				simplifyExpression(lengthExp, line.globalLabel(), false, finalize, line.scope());
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
				simplifyExpression(valueExp, line.globalLabel(), false, finalize, line.scope());
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

		public String toLastString(){
			TextLine l = new TextLine(line);
			l.set(1, lengthExp.toString());
			if (l.size() == 3)
				l.set(2, valueExp.toString());
			return l.toString();
		}


		public TextLine getLine(){
			return new TextLine(line);
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
			boundaryExp = buildExpression(line.get(1), line.globalLabel(), true, false);
			original = boundaryExp;
		}

		private int boundary(boolean finalize)
			throws Exception
		{
			boundaryExp = original.clone();
			if (boundaryExp.numericValue() == null)
				simplifyExpression(boundaryExp, line.globalLabel(), false, finalize, line.scope());
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

		public String toLastString(){
			TextLine l = new TextLine(line);
			l.set(1, boundaryExp.toString());
			return l.toString();
		}

		public TextLine getLine(){
			return new TextLine(line);
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
			line = new TextLine(line);
			this.pack = line.remove(0).equals(".DP");
			this.line = line;
			data = new int[line.size()][];
			for (int i = 0; i < line.size(); i++){
				String word = line.get(i);
				literals.add(null);
				if (word.indexOf('"') > -1)
					data[i] = getString(word);
				else
					literals.set(i, buildExpression(word, line.globalLabel(), true, false));
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
				simplifyExpression(exp, line.globalLabel(), false, finalize, line.scope());
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


		public String toLastString(){
			return "[DATA]";
		}

		public TextLine getLine(){
			return new TextLine(line);
		}
	}

	public boolean isInstruction(String instruction){
		return instructionBytes.get(instruction.toUpperCase()) != null;
	}

	private class AssembleInstruction implements AssembleStructure{

		public String instruction;
		public AssembleInstructionData a;
		public AssembleInstructionData b;
		public TextLine line;
		private AssembleInstruction subbingFor;
		private AssembleInstruction substitution;

		public AssembleInstruction(TextLine line)
			throws Exception
		{
			init();
			this.line = line;
			instruction = line.get(0);
			if (line.size() > 1)
				a = new AssembleInstructionData(line.get(1), line.globalLabel());
			if (line.size() > 2)
				b = new AssembleInstructionData(line.get(2), line.globalLabel());
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
				substitution = sub;
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

			public String toLastString(){
				return exp.toString();
			}

			public void literalize(boolean finalize)
				throws Exception
			{
				//System.out.println("finalize " + (finalize ? "yes" : "no"));
				exp = original.clone(); // re-calculate from the original every time to allow for moving labels
				simplifyExpression(exp, globalLabel, false, finalize, line.scope());
			}

			public int toByte(boolean finalize)
				throws Exception
			{
				Integer easy = staticTransforms.get(data.toUpperCase());
				if (easy != null)
					return easy;
				literalize(finalize);
				String register = findRegister(exp);
				if (exp.isParenthesized() && (register != null || data.charAt(0) == '[')){
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
						if (plusRegisters.get(label.toUpperCase()) != null)
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

		public String toLastString(){
			if (substitution != null)
				return substitution.toLastString();
			TextLine l = new TextLine(line);
			l.set(1, a.toLastString());
			if (l.size() == 3)
				l.set(2, b.toLastString());
			return l.toString();
		}

		public TextLine getLine(){
			return new TextLine(line);
		}
	}

}


