/*
* Turns a mathematical expression into a tree of MathExpression nodes.
* When () and [] follow an identifier (not an operator), then we treat it as a + like in Assembler, not as a method like in C
* But if you're doing regular math problems, you don't care about that, because you'll never come across such a thing.
*/


class MathExpression{

	private final static String UNARY = "unary";
	private static String[][] precedents =
										{
											{ "@!", "@+", "@~", "@-" },
											{ "*", "/", "%" },
											{ "+", "-" },
											{ "<<", ">>" },
											{ "<=", "<", ">=", ">" },
											{ "==", "!=" },
											{ "&" },
											{ "^" },
											{ "|" },
											{ "&&" },
											{ "||" },
											{ "=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=" }
										};
	private static String[] operatorChars = null;

	private MathExpression left = null;
	private MathExpression right = null;
	private String operator = null;
	private String value = null;
	private Integer numericValue = null;
	private boolean isParenthesized = false;
	private boolean isUnary = false;

	public static MathExpression parse(String s){
		return parse(s, 0, s.length());
	}

	public static MathExpression parse(String sParent, int substringStart, int substringEnd){
		try{
			String s = sParent.substring(substringStart, substringEnd >= 0 ? substringEnd : sParent.length() + substringEnd);
			boolean opsEncountered = false;
			for (int p = precedents.length - 1; p >= 0; p--){
				String[] group = precedents[p];
				boolean lastWasOperator = true;
				boolean noTokensYet = true;
				boolean noOpsYet = true;
				for (int i = 0; i < s.length(); i++){
					if (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\n')
						continue;
					boolean isFirstToken = noTokensYet;
					noTokensYet = false;
					if (! isOperatorChar(s.charAt(i))){
						lastWasOperator = false;
						continue;
					}
					boolean isFirstOp = noOpsYet;
					noOpsYet = false;
					opsEncountered = true;
					if (s.charAt(i) == '(' || s.charAt(i) == '['){
						String paren = grabParen(s.substring(i));
						if (isFirstOp && s.substring(i + paren.length()).trim().length() == 0){
							MathExpression right = parse(paren, 1, -1);
							right.isParenthesized = true;
							if (s.substring(0, i).trim().length() == 0)
								return right;
							return new MathExpression(parse(s, 0, i), "+", right);
						}
						i += paren.length();
						lastWasOperator = false;
						continue;
					}
					int start = i;
					int end = i + 1;
					for (; end < s.length() && isOperatorChar(s.charAt(end)); end++){}
					for (;! isOp(s.substring(start, end)) && end > start; end--){}
					String op = s.substring(start, end);
					i = end - 1;
					boolean isUnary = lastWasOperator;
					lastWasOperator = true;
					String lookFor = isUnary ? "@" + op : op;
					if (! inArray(lookFor, group))
						continue;
					MathExpression right = parse(s, i + 1, s.length());
					if (isUnary && isFirstToken){
						if (right.value != null || right.isUnary || right.isParenthesized){ // then there is more to do on the right.
							return new MathExpression(null, op, right);
						}
						continue;
					}
					// this is it. we want the left-most operator in the lowest group we can find
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

	private static boolean isMoreOperator(char c){
		return c != '(' && c != '[' && isOperatorChar(c);
	}

	private static String[] operatorChars(){
		if (operatorChars != null)
			return operatorChars;
		String allOperators = "()[]";
		for (int i = 0; i < precedents.length; i++)
			for (int j = 0; j < precedents[i].length; j++)
				allOperators += precedents[i][j];
		operatorChars = new String[allOperators.length()];
		for (int i = 0; i < allOperators.length(); i++)
			operatorChars[i] = "" + allOperators.charAt(i);
		return operatorChars;
	}

	private static boolean isOperatorChar(char c){
		return inArray("" + c, operatorChars());
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

	private static boolean isOp(String op){
		for (int i = 0; i < precedents.length; i++)
			for (int j = 0; j < precedents[i].length; j++)
				if (precedents[i][j].equals(op) || precedents[i][j].equals("@" + op))
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

	public void simplify(){
		if (numericValue != null)
			return; // looks like we already simplified
		if (value != null){
			if (value.matches("\\d+"))
				numericValue = Integer.parseInt(value);
			else if (value.matches("0x[\\dA-Fa-f]+"))
				numericValue = Hexer.unhex(value.substring(2));
			return; // simple as can be
		}
		if (left != null)
			left.simplify();
		if (right != null)
			right.simplify();
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
		if (operator.equals("!=")){
			convert(left.numericValue != right.numericValue ? 1 : 0);
			return;
		}
		if (operator.equals("&&")){
			convert(left.numericValue != 0 && right.numericValue != 0 ? 1 : 0);
			return;
		}
		if (operator.equals("||")){
			convert(left.numericValue != 0 || right.numericValue != 0 ? 1 : 0);
			return;
		}
	}

	private void convert(int number){
		numericValue = number;
		value = "" + numericValue;
		left = null;
		right = null;
		operator = null;
		isParenthesized = false;
		isUnary = false;
	}

	public String toString(){
		if (operator != null)
			return "(" + (left == null ? "" : left + " ") + operator + " " + right + ")";
		return value;
	}

	/********************
	 *  Testing
	 */

	public static void main(String[] args){
		/*
		// Should work:
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
		//*
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
		//*/
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