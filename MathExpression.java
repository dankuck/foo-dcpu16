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

	public static MathExpression parse(String s){
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
				if (s.charAt(i) == '(' || s.charAt(i) == '['){
					String paren = grabParen(s.substring(i));
					if (isFirstOp && s.substring(i + paren.length()).trim().length() == 0){
						MathExpression right = parse(paren.substring(1, paren.length() - 1));
						if (s.substring(0, i).trim().length() == 0)
							return right;
						return new MathExpression(parse(s.substring(0, i)), "+", right);
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
				/*
				for (; i < s.length() && isMoreOperator(s.charAt(i)); i++)
					op += s.charAt(i);
				i--;
				*/
				boolean isUnary = lastWasOperator;
				lastWasOperator = true;
				String lookFor = isUnary ? "@" + op : op;
				if (! inArray(lookFor, group))
					continue;
				MathExpression right = parse(s.substring(i + 1));
				if (isUnary && isFirstToken){
					if (right.value == null){ // then there is more to do on the right.
						continue;
					}
					return new MathExpression(null, op, right);
				}
				// this is it. we want the left-most operator in the lowest group we can find
				return new MathExpression(parse(s.substring(0, start)), op, right);
			}
		}
		return new MathExpression(s.trim());
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
		value = v;
	}

	public MathExpression(MathExpression l, String op, MathExpression r){
		left = l;
		operator = op;
		right = r;
	}

	public void simplify(){
		if (numericValue != null)
			return; // looks like we already simplified
		if (value != null){
			if (value.matches("\\d+"))
				numericValue = Integer.parseInt(value);
			else if (value.matches("0x\\d+"))
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
			// do some unary stuff here
			return;
		}
		if (left.numericValue == null || right.numericValue == null)
			return;
		switch (operator.charAt(0)){
			case '+':
				convert(left.numericValue + right.numericValue);
				return;
			case '*':
				convert(left.numericValue * right.numericValue);
				return;
			default:
				return;
		}
	}

	private void convert(int number){
		numericValue = number;
		value = "" + numericValue;
		left = null;
		right = null;
		operator = null;
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
		testSimplify("1 + 2");
		testSimplify("2 * 3");
		testSimplify("1 + 2 * 3");
	}

	public static void test(String s){
		System.out.println(s + " => " + parse(s));
	}

	public static void testSimplify(String s){
		System.out.print(s + " => ");
		MathExpression exp = parse(s);
		exp.simplify();
		System.out.println(exp);
	}

}