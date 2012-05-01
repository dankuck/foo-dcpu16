/*
* Turns a mathematical expression into a tree of MathExpression nodes.
* When () and [] follow an identifier (not an operator), then we treat it as a + like in Assembler, not as a method like in C
* But if you're doing regular math problems, you don't care about that, because you'll never come across such a thing.
*/

import java.util.*;

class MathExpression{

	private final static String UNARY = "unary";
	private static String[][] precedence =
										{
											{ "@!", "@+", "@~", "@-" },
											{ "*", "/", "%" },
											{ "+", "-" },
											{ "<<", ">>" },
											{ "<=", "<", ">=", ">" },
											{ "==", "!=", "<>" },
											{ "&" },
											{ "^" },
											{ "|" },
											{ "&&" },
											{ "^^" },
											{ "||" },
											{ "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=" }
										};
	private static String[] operatorChars = null;
	private static List<String> commutableOperators;
	static {
		commutableOperators = new ArrayList<String>();
		commutableOperators.add("+");
		commutableOperators.add("*");
		commutableOperators.add("==");
		commutableOperators.add("!=");
		commutableOperators.add("<>");
		// not including & or && because they shortcut
	}

	private MathExpression left = null;
	private MathExpression right = null;
	private String operator = null;
	private String value = null;
	private Integer numericValue = null;
	private boolean isParenthesized = false;
	private boolean isUnary = false;

	public static void setPrecedence(String[][] precedence){
		MathExpression.precedence = precedence;
		operatorChars = null;
	}

	public static MathExpression parse(String s){
		return parse(s, 0, s.length());
	}

	public static MathExpression parse(String sParent, int substringStart, int substringEnd){
		try{
			String s = sParent.substring(substringStart, substringEnd >= 0 ? substringEnd : sParent.length() + substringEnd);
			boolean opsEncountered = false;
			for (int p = precedence.length - 1; p >= 0; p--){
				String[] group = precedence[p];
				for (int i = s.length() - 1; i >= 0; i--){
					if (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\n')
						continue;
					if (! isOperatorChar(s.charAt(i)))
						continue;
					opsEncountered = true;
					if (s.charAt(i) == ']' || s.charAt(i) == ')'){
						String paren = grabParenReverse(s.substring(0, i + 1));
						i -= paren.length() - 1;
						String pairedWith = "";
						boolean isFirstOp = true;
						for (int j = i - 1; j >= 0; j--){
							if (! isOperatorChar(s.charAt(j)))
								pairedWith = s.charAt(j) + pairedWith;
							else{
								isFirstOp = false;
								break;
							}
						}
						boolean isLastOp = true;
						for (int j = i + paren.length(); j < s.length(); j++)
							if (isOperatorChar(s.charAt(j))){
								isLastOp = false;
								break;
							}
						i -= pairedWith.length();
						if (isFirstOp && isLastOp){
							MathExpression right = parse(paren, 1, -1);
							right.isParenthesized = true;
							if (pairedWith.trim().length() == 0)
								return right;
							MathExpression exp = new MathExpression(parse(pairedWith.trim()), paren.charAt(0) == '[' ? "+" : "call", right);
							exp.isParenthesized = true; // causes other parts of the program to treat this as an unbreakable unit
							return exp;
						}
						continue;
					}
					int start = i;
					int end = i + 1;
					for (; start > 0 && isOperatorChar(s.charAt(start)) && ! isParenthesis(s.charAt(start)); start--){}
					if (! isOperatorChar(s.charAt(start)) || isParenthesis(s.charAt(start)))
						start++;
					for (;! isOp(s.substring(start, end)) && end > start; start++){}
					String op = s.substring(start, end);
					i = start;
					boolean isUnary = true;
					for (int j = i - 1; j >= 0; j--){
						if (s.charAt(j) == ' ' || s.charAt(j) == '\t' || s.charAt(j) == '\n')
							continue;
						if (isOperatorChar(s.charAt(j)) && ! isParenthesis(s.charAt(j)))
							break;
						isUnary = false;
						break;
					}
					if (isUnary && s.substring(0, start).trim().length() > 0)
						continue;
					String lookFor = isUnary ? "@" + op : op;
					if (! inArray(lookFor, group))
						continue;
					MathExpression right = parse(s, end, s.length());
					if (isUnary){
						if (right.value != null || right.isUnary || right.isParenthesized)
							return new MathExpression(null, op, right);
						continue;
					}
					// this is it. we want the right-most operator in the lowest group we can find
					return new MathExpression(parse(s, 0, start), op, right);
				}
			}
			if (opsEncountered) // we found operations, but we couldn't figure out the meaning, error
				throw new RuntimeException("Malformed expression: " + s);
			return new MathExpression(s.trim());
		}
		catch (MathExpression.ParseException e){
			throw new MathExpression.ParseException(e.getCause(), sParent, substringStart + e.location());
		}
		catch (Exception e){
			throw new MathExpression.ParseException(e, sParent, substringStart);
		}
	}

	public static class ParseException extends RuntimeException{

		private String string;
		private int location;

		public ParseException(Throwable cause, String string, int location){
			super(cause + " at character " + location + " of: " + string, cause);
			this.string = string;
			this.location = location;
		}

		public String string(){
			return string;
		}

		public int location(){
			return location;
		}
	}

	public static interface LabelInterpretter{

		public Integer interpret(String label);
		public Integer call(String label, MathExpression input);
	}

	private static String[] operatorChars(){
		if (operatorChars != null)
			return operatorChars;
		String allOperators = "()[]";
		for (int i = 0; i < precedence.length; i++)
			for (int j = 0; j < precedence[i].length; j++)
				allOperators += precedence[i][j];
		operatorChars = new String[allOperators.length()];
		for (int i = 0; i < allOperators.length(); i++)
			operatorChars[i] = "" + allOperators.charAt(i);
		return operatorChars;
	}

	private static boolean isOperatorChar(char c){
		return inArray("" + c, operatorChars());
	}

	private static boolean isParenthesis(char c){
		return c == '(' || c == ')' || c == '[' || c == ']';
	}

	private static boolean inArray(String n, String[] h){
		for (int i = 0; i < h.length; i++)
			if (n.equals(h[i]))
				return true;
		return false;
	}

	public static String grabParen(String s){
		int parens = 0;
		if (s.charAt(0) != '(' && s.charAt(0) != '[')
			throw new RuntimeException("Suppose to start string at first '(' for grabParens");
		for (int i = 0; i < s.length(); i++){
			if (s.charAt(i) == '(' || s.charAt(i) == '[') // technically this allows people to mismatch () and []. a recursive design would protect against that.
				parens++;
			else if (s.charAt(i) == ')' || s.charAt(i) == ']')
				parens--;
			if (parens == 0)
				return s.substring(0, i + 1);
		}
		throw new RuntimeException("Unclosed parentheses");
	}

	public static String grabParenReverse(String s){
		int parens = 0;
		if (s.charAt(s.length() - 1) != ')' && s.charAt(s.length() - 1) != ']')
			throw new RuntimeException("Suppose to start string at first ')' or ']' for grabParensReverse: " + s);
		for (int i = s.length() - 1; i >= 0; i--){
			if (s.charAt(i) == ')' || s.charAt(i) == ']') // technically this allows people to mismatch () and []. a recursive design would protect against that.
				parens++;
			else if (s.charAt(i) == '(' || s.charAt(i) == '[')
				parens--;
			if (parens == 0)
				return s.substring(i);
		}
		throw new RuntimeException("Unclosed parentheses");
	}

	private static boolean isOp(String op){
		for (int i = 0; i < precedence.length; i++)
			for (int j = 0; j < precedence[i].length; j++)
				if (precedence[i][j].equals(op) || precedence[i][j].equals("@" + op))
					return true;
		return false;
	}

	public MathExpression(String v){
		if (v == null || v.length() == 0)
			throw new RuntimeException("Zero length value");
		value = v;
	}

	public MathExpression(MathExpression l, String op, MathExpression r){
		left = l;
		operator = op;
		right = r;
		if (left == null)
			isUnary = true;
	}

	private MathExpression(){
	}

	public String value(){
		return value;
	}

	public Integer numericValue(){
		return numericValue;
	}

	public MathExpression left(){
		return left;
	}

	public MathExpression right(){
		return right;
	}

	public String operator(){
		return operator;
	}

	public void simplify(){
		simplify(null);
	}
	public void simplify(LabelInterpretter labels){
		if (numericValue != null)
			return; // looks like we already simplified
		if (value != null){
			if (value.matches("\\d+"))
				numericValue = Integer.parseInt(value);
			else if (value.matches("0x[\\dA-Fa-f]+"))
				numericValue = Hexer.unhex(value.substring(2));
			else if (labels == null)
				return; // simple as can be
			else{
				Integer realValue = labels.interpret(value);
				if (realValue != null)
					numericValue = realValue;
				return;
			}
		}
		if (left != null)
			left.simplify(labels);
		if (right != null)
			right.simplify(labels);
		if (operator == null) // dunno what this means
			return;
		if (right == null) // dunno what this means
			return;
		if (left == null){ // unary
			if (right.numericValue == null)
				return;
			switch (operator.charAt(0)){
				case '!':
					convert(right.numericValue == 0 ? 1 : 0);
					return;
				case '+':
					convert(right.numericValue);
					return;
				case '-':
					convert(- right.numericValue);
					return;
				case '~':
					convert(~ right.numericValue);
					return;
			}
			return;
		}
		if (operator.equals("call") && labels != null){
			Integer callResult = labels.call(left.value, right);
			if (callResult != null)
				convert(callResult);
			return;
		}
		if (left.numericValue == null || right.numericValue == null)
			return;
		if (operator.length() == 1){
			switch (operator.charAt(0)){
				case '+':
					convert(left.numericValue + right.numericValue);
					return;
				case '*':
					convert(left.numericValue * right.numericValue);
					return;
				case '-':
					convert(left.numericValue - right.numericValue);
					return;
				case '/':
					convert(left.numericValue / right.numericValue);
					return;
				case '%':
					convert(left.numericValue % right.numericValue);
					return;
				case '<':
					convert(left.numericValue < right.numericValue ? 1 : 0);
					return;
				case '>':
					convert(left.numericValue > right.numericValue ? 1 : 0);
					return;
				case '&':
					convert(left.numericValue & right.numericValue);
					return;
				case '^':
					convert(left.numericValue ^ right.numericValue);
					return;
				case '|':
					convert(left.numericValue | right.numericValue);
					return;
			}
			return;
		}
		if (operator.equals("<<")){
			convert(left.numericValue << right.numericValue);
			return;
		}
		if (operator.equals(">>")){
			convert(left.numericValue >> right.numericValue);
			return;
		}
		if (operator.equals("<=")){
			convert(left.numericValue <= right.numericValue ? 1 : 0);
			return;
		}
		if (operator.equals(">=")){
			convert(left.numericValue >= right.numericValue ? 1 : 0);
			return;
		}
		if (operator.equals("==")){
			convert(left.numericValue == right.numericValue ? 1 : 0);
			return;
		}
		if (operator.equals("!=") || operator.equals("<>")){
			convert(left.numericValue != right.numericValue ? 1 : 0);
			return;
		}
		if (operator.equals("&&")){
			convert(left.numericValue != 0 && right.numericValue != 0 ? 1 : 0);
			return;
		}
		if (operator.equals("^^")){ // java uses ^ for boolean-xor and integer-xor, depends on which type you're using
			convert(((left.numericValue != 0) ^ (right.numericValue != 0)) ? 1 : 0);
			return;
		}
		if (operator.equals("||")){
			convert(left.numericValue != 0 || right.numericValue != 0 ? 1 : 0);
			return;
		}
	}

	public boolean isParenthesized(){
		return isParenthesized;
	}

	private void convert(int number){
		numericValue = number;
		value = "" + numericValue;
		left = null;
		right = null;
		operator = null;
		isUnary = false;
	}

	public List<String> labels(){
		List<String> labels = new ArrayList<String>();
		if (numericValue == null && value != null){
			labels.add(value);
			return labels;
		}
		if (left != null)
			labels.addAll(left.labels());
		if (right != null)
			labels.addAll(right.labels());
		return labels;
	}

	public int labelCount(String label){
		if (value != null && value.equals(label))
			return 1;
		int count = 0;
		if (right != null)
			count += right.labelCount(label);
		if (left != null)
			count += left.labelCount(label);
		return count;
	}

	public boolean bringToTop(String label){
		if (label == null)
			return false;
		if (value != null){
			if (value.equals(label))
				return true;
			return false;
		}
		if (labelCount(label) != 1)
			return false;
		if (right != null && label.equals(right.value))
			return true;
		if (left != null && label.equals(left.value))
			return true;
		massageIntoCommutable();
		if (! commutable())
			return false;
		if (right != null && right.labelCount(label) == 1){
			if (! samePrecedence(operator, right.operator))
				return false;
			if (! right.bringToTop(label))
				return false;
			if (label.equals(right.right.value)){
				if (! right.commutable())
					return false;
				MathExpression temp = left;
				left = right.right;
				right.right = temp;
				simplify();
				return true;
			}
			else if (label.equals(right.left.value)){
				MathExpression temp = left;
				left = right.left;
				right.left = temp;
				simplify();
				return true;
			}
			throw new RuntimeException("I'm broken...");
		}
		if (left != null && left.labelCount(label) == 1){
			if (! samePrecedence(operator, left.operator))
				return false;
			if (! left.bringToTop(label))
				return false;
			if (label.equals(left.right.value)){
				if (! left.commutable())
					return false;
				MathExpression temp = right;
				right = left.right;
				left.right = temp;
				simplify();
				return true;
			}
			else if (label.equals(left.left.value)){
				MathExpression temp = right;
				right = left.left;
				left.left = temp;
				simplify();
				return true;
			}
			throw new RuntimeException("I'm broken...");
		}
		return false; // never get here because of the labelCount(label) line above
	}

	public void massageIntoCommutable(){
		if (commutable())
			return;
		if ("/".equals(operator)){
			right = new MathExpression(new MathExpression("1"), "/", right);
			operator = "*";
		}
		else if ("-".equals(operator)){
			right = new MathExpression(null, "-", right);
			operator = "+";
		}
		else
			return;
		simplify();
	}

	public boolean commutable(){
		if (operator == null)
			throw new RuntimeException("I'm broken...");
		return commutableOperators.contains(operator);
	}

	private boolean samePrecedence(String op1, String op2){
		if (op1 == null || op2 == null)
			throw new RuntimeException("I'm broken..." + op1 + " " + op2);
		for (int i = 0; i < precedence.length; i++)
			if (inArray(op1, precedence[i]) || inArray("@" + op1, precedence[i]))
				return inArray(op2, precedence[i]) || inArray("@" + op2, precedence[i]);
		return false;
	}

	public MathExpression clone(){
		MathExpression exp = new MathExpression();
		exp.left = left == null ? null : left.clone();
		exp.right = right == null ? null : right.clone();
		exp.operator = operator;
		exp.value = value;
		exp.numericValue = numericValue;
		exp.isParenthesized = isParenthesized;
		exp.isUnary = isUnary;
		return exp;
	}

	public String toString(){
		return toString(true);
	}

	public String toString(boolean addParentheses){
		if (operator != null){
			String str = (left == null ? "" : left + " ") + (operator.equals("call") ? "" : operator) + " " + right;
			if (addParentheses || isParenthesized)
				return "(" + str + ")";
			return str;
		}
		if (numericValue != null)
			return numericValue + "";
		return isParenthesized ? "(" + value + ")" : value;
	}

	/********************
	 *  Testing
	 */

	public static void main(String[] args){
		/*
		// should succeed
		testc("A+B+C", "A");
		testc("(A+B)+C", "A");
		testc("A+(B+C)", "A");
		testc("B+A+C", "A");
		testc("B+C+A", "A");
		testc("A-B+C", "A");
		testc("A+B+-C", "A");
		testc("A+B-C", "A");
		testc("A*B*C", "A");
		testc("B*A*C", "A");
		testc("A/B*C", "A");
		testc("A*B/C", "A");
		testc("A*B/(C*D)", "A");
		// should fail
		testc("A+B+C+A", "A");
		testc("B+C", "A");
		testc("B/A*C", "A");
		//*/

		//*
		// Should work:
		test("(some_label+some_other_label)*100");
		//*
		test("!!2");
		test("1 + 2");
		test("1 + 2 + 3");
		test("1 + 2 * 3");
		test("!1");
		test("1 + !2 * 3");
		test("(1 + !2) * 3");
		test("3*(1 + !2)");
		test("3*(1 + !2 - 8 / 2)");
		test("3*(1 + (!2 - 8) / 2)");
		test("hello[!1]");
		test("x||y&&z");
		test("x&y+z");
		test("1 + 2");
		test("2 * 3");
		test("!0");
		test("!1");
		test("!-2");
		test("+0");
		test("+2");
		test("+0xFFFF");
		test("~7");
		test("~-1");
		test("~0xFF");
		test("-300 + 1");
		test("4 << 1");
		test("4 << (1 + 1)");
		test("16 >> 1");
		test("16 >> (1 + 1)");
		test("5 < 6");
		test("5 < 5");
		test("5 < 4");
		test("5 < ~-64");
		test("5 <= 6");
		test("5 <= 5");
		test("5 <= 4");
		test("5 <= ~-64");
		test("5 > 6");
		test("5 > 5");
		test("5 > 4");
		test("5 > ~-64");
		test("5 >= 6");
		test("5 >= 5");
		test("5 >= 4");
		test("5 >= ~-64");
		test("5 == 6");
		test("5 == 5");
		test("5 == 4");
		test("5 == ~-64");
		test("5 != 6");
		test("5 != 5");
		test("5 != 4");
		test("5 != ~-64");
		test("5 & 4");
		test("5 & 5");
		test("5 & 6");
		test("0xFFFF + 5 & 0xFFFF");
		test("5 | 4");
		test("5 | 5");
		test("5 | 6");
		test("5 && 4");
		test("5 && 0");
		test("5 && (5 > 4)");
		test("5 && (5 < 4)");
		test("5 || 4");
		test("5 || 0");
		test("5 || (5 > 4)");
		test("5 || (5 < 4)");
		test("0 || 0");
		test("x = 0");
		test("1 = 12");
		test("1 = 12 + 5");
		test("(100)");
		test("(100 + 1)");
		test("(100 + 1]"); // a known bug
		//*/
		/*
		//Should fail
		test("~");
		test("~-");
		test("4 + 5 *  ");
		test("5 *");
		test("4 + [5 *]");
		test("4 + [5 *] 6");
		test("4 + [5 *] 6 + 7");
		test("hair()");
		test("(7)gone");
		test("*4");
		test("4 4");
		//*/
	}

	public static void testc(String expression, String label){
		MathExpression exp = parse(expression);
		System.out.println(expression);
		System.out.println(exp);
		System.out.println(exp.bringToTop(label) ? "Brought " + label + " to top" : "Couldn't bring " + label + " to top");
		System.out.println(exp);
		System.out.println();
	}

	public static void test(String s){
		try{
			System.out.print(s + "\t=> ");
			MathExpression exp = parse(s);
			System.out.print(exp + "\t=> ");
			exp.simplify();
			System.out.print(exp + "\n");
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}

}