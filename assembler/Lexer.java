
package assembler;
import java.util.*;
import java.io.*;
import mathExpression.*;
import hexer.*;

public class Lexer{

	private Assembler assembler;
	private StringTokenizer tokens;
	private List<FlowFrame> flowstack = new ArrayList<FlowFrame>();
	private List<FlowFrame> lineSources = new ArrayList<FlowFrame>();
	private List<TextLine> nextLines = new ArrayList<TextLine>();
	private int org = 0;
	private String filename;
	private int currentLine = 0;

	public Lexer(Assembler assembler, String contents, String filename){
		this.assembler = assembler;
		contents += "\n"; // \n helps us make sure we get the last token
		tokens = new StringTokenizer(contents, ";, \t\n\r\f\"\\{}", true);
		this.filename = filename;
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
		try{
			FlowFrame bottom = new FlowFrame(FlowFrame.BOTTOM);
			bottom.active = true;
			pushFlow(bottom);
			int startSize = flowstack.size();
			while (true){
				FlowFrame flowtop = flowtop();
				if (flowtop == null)
					throw new Exception("Something went wrong with the flowstack.");
				assembler.currentGlobalLabel(flowtop.globalLabel);
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
					r.addLine(line);
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
					pushFlow(calledMacro);
					lineSources.add(calledMacro);
					continue;
				}
				if (line.get(0).charAt(0) == ':' || line.get(0).charAt(line.get(0).length() - 1) == ':'){
					assembler.currentGlobalLabel(flowtop.globalLabel);
					assembler.addLabel((line.scope() == null ? "" : line.scope()) + line.get(0).replaceAll("^:|:$", ""), org);
					flowtop.globalLabel = assembler.currentGlobalLabel();
					continue;
				}
				if (line.size() == 0)
					throw new Exception("Somehow ended up with a 0 length line");
				if (! line.get(0).equalsIgnoreCase("DAT") && line.size() > 3)
					throw new Exception("Too many tokens on line " + line.size() + ": " + line);
				assembler.addInstruction(line);
			}
			if (flowstack.size() < startSize)
				throw new Exception("There are too many ends");
			if (flowstack.size() > startSize)
				throw new Exception("There are not enough ends");
		}
		catch (Exception e){
			throw new Exception("Exception at " + filename + " " + currentLine + " in " + assembler.currentGlobalLabel() + ": " /*+ line*/ + " : " + e.getMessage(), e);
		}
	}

	private FlowFrame callMacro(TextLine line)
		throws Exception
	{
		List<String> params = assembler.interpretMacroParts(line);
		//System.out.println("Calling " + TextLine.joinLine(params));
		String name = params.remove(0);
		Macro macro = assembler.getMacro(name);
		if (macro == null)
			throw new Exception("Undefined macro");
		return macro.interpolate(params, line.scope());
	}

	private void pushFlow(FlowFrame frame){
		frame.globalLabel = assembler.currentGlobalLabel();
		flowstack.add(frame);
	}

	private FlowFrame popFlow(){
		return flowstack.remove(flowstack.size() - 1);
	}

	private void preprocess(String preprocessor, TextLine line)
		throws Exception
	{
		FlowFrame flowtop = flowtop();
		FlowFrame lineSource = lineSource();
		if (preprocessor.equals("include")){
			if (line.size() != 2)
				throw new Exception("Wrong token count on line " + line);
			String path = assembler.getPath(line.get(1));
			if (! new File(path).exists())
				System.out.println(path + " not exists");
			else
				new Lexer(assembler, assembler.contents(path), path).lex();
			return;
		}
		else if (preprocessor.equals("incbin")){
			if (line.size() != 2)
				throw new Exception("Wrong token count on line " + line);
			File file = new File(assembler.getPath(line.get(1)));
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
			assembler.addInstruction(line);
			return;
		}
		else if (preprocessor.equals("def") || preprocessor.equals("define") || preprocessor.equals("equ")){
			String[] parts = line.get(1).split("\\s+");
			if (parts.length < 2 || parts.length > 3)
				throw new Exception("Wrong token count on line " + line);
			String name = parts[0];
			int value = 1;
			if (parts.length > 2)
				throw new Exception("Wrong token count on line " + line);
			if (parts.length == 2){
				MathExpression exp = assembler.buildExpression(parts[1], true, false);
				if (exp.numericValue() == null)
					throw new Exception("Couldn't evaluate " + parts[1] + " => " + exp);
				value = exp.numericValue();
			}
			assembler.addDefinition(name, value);
			return;
		}
		else if (preprocessor.equals("undef")){
			if (line.size() != 2)
				throw new Exception("Wrong token count on line " + line);
			String name = line.get(1);
			assembler.dropDefinition(name);
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
				pushFlow(newtop);
				return;
			}
			if (line.size() != 2)
				throw new Exception("Wrong token count: " + line);
			String eval = line.get(1);
			if (preprocessor.equals("ifdef"))
				eval = "isdef(" + eval + ")";
			else if (preprocessor.equals("ifndef"))
				eval = "!isdef(" + eval + ")";
			MathExpression exp = assembler.buildExpression(eval, true, false);
			if (exp.numericValue() == null)
				throw new Exception("Couldn't evaluate " + eval + " => " + exp);
			boolean result = exp.numericValue() != 0;
			//System.out.println("Adding active if to stack");
			FlowFrame newtop = new FlowFrame(FlowFrame.IF);
			newtop.active = result;
			newtop.skiprest = result;
			pushFlow(newtop);
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
			MathExpression exp = assembler.buildExpression(eval, true, false);
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
				pushFlow(rep);
				return;
			}
			if (line.size() != 2)
				throw new Exception("Wrong token count: " + line);
			MathExpression exp = assembler.buildExpression(line.get(1), true, false);
			if (exp.numericValue() == null)
				throw new Exception("Expression doesn't literalize: " + line.get(1) + " => " + exp);
			//System.out.println("Adding active rep to stack");
			FlowFrame rep = new FlowFrame(FlowFrame.REP);
			rep.active = false;
			rep.countDown = exp.numericValue();
			pushFlow(rep);
			return;
		}
		else if (preprocessor.equals("macro")){
			if (! flowtop.active){
				//System.out.println("Adding inactive rep to stack");
				FlowFrame mac = new FlowFrame(FlowFrame.MACRO);
				mac.active = false;
				pushFlow(mac);
				return;
			}
			line.remove(0);
			List<String> params = assembler.interpretMacroParts(line);
			String name = params.remove(0);
			FlowFrame mac = new FlowFrame(FlowFrame.MACRO);
			mac.active = false;
			Macro m = new Macro(name, params, mac);
			assembler.addMacro(name, m);
			pushFlow(mac);
			return;
		}
		else if (preprocessor.equals("end")){
			if (flowtop.isBottom())
				throw new Exception(preprocessor + " in wrong context");
			if (! flowtop.isRep() || flowtop.countDown <= 0){
				//System.out.println("Removing " + (flowtop.isRep() ? "rep" : "if") + " from stack");
				if (lineSource == flowtop)
					lineSources.remove(lineSources.size() - 1);
				popFlow();
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
			MathExpression exp = assembler.buildExpression(eval, true, false);
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
			assembler.addInstruction(line);
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

	private void queueLine(TextLine line){
		line.setLocation(filename, currentLine);
		nextLines.add(line);
	}

	private void queueLine()
		throws Exception
	{
		if (nextLines.size() != 0)
			return;
		boolean inComment = false;
		TextLine line = new TextLine();
		line.globalLabel(assembler.currentGlobalLabel());
		String expression = "";
		boolean inQuotes = false;
		while (tokens.hasMoreTokens()){
			String token = tokens.nextToken();
			if (token.equals("\n"))
				currentLine ++;
			if (token.charAt(0) == '{' && ! inComment)
				token = "\n";
			boolean isEndCurly = token.charAt(0) == '}' && ! inComment;
			boolean isLabel = (token.charAt(0) == ':' || token.charAt(token.length() - 1) == ':') && ! inComment;
			if (token.equals("\n") || isEndCurly || isLabel){
				inComment = false;
				if (expression.trim().length() > 0){
					line.add(expression.trim());
					expression = "";
				}
				if (isLabel){
					if (line.size() > 0)
						throw new Exception("Label is not at beginning of line: " + token);
					line.add(token);
				}
				if (line.size() > 0)
					queueLine(line);
				if (isEndCurly){
					TextLine curly = new TextLine();
					curly.add("}");
					queueLine(curly);
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
			expression += token;
		}
	}

	public boolean isPreprocessor(String word){
		return word.matches("#.*|\\..*");
	}

	public String cleanPreprocessor(String word){
		return word.replaceAll("^#|^\\.", "").toLowerCase();
	}
}
