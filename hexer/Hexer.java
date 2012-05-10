
package hexer;

public class Hexer{

	private static char[] digits = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

	public static String hex(int number){
		return hex(number, 4);
	}

	public static String hex(int number, int bytes){
		String hex = "";
		for (int i = bytes - 1; i >= 0; i--)
			hex += digits[(number >> (i << 2)) & 0xF];
		return hex;
	}

	public static int unhex(String hex){
		int number = 0;
		for (int i = 0; i < hex.length(); i++){
			char c = hex.charAt(i);
			int digit = 0;
			if (c >= '0' && c <= '9')
				digit = c - '0';
			else if (c >= 'A' && c <= 'F')
				digit = c - 'A' + 10;
			else if (c >= 'a' && c <= 'f')
				digit = c - 'a' + 10;
			number |= digit << ((hex.length() - 1 - i) << 2);
		}
		return number;
	}

	public static String hexArray(int[] numbers){
		return hexArray(numbers, 4);
	}

	public static String hexArray(int[] numbers, int bytes){
		String hex = "";
		for (int i = 0; i < numbers.length; i++)
			hex += hex(numbers[i], bytes) + " ";
		return hex;
	}

	public static int[] unhexArray(String hex){
		hex += " ";
		int lastSpace = -1;
		int[] numbers = new int[hex.length()];
		int currentNumber = 0;
		for (int i = 0; i < hex.length(); i++){
			if (hex.charAt(i) == ' ' || hex.charAt(i) == '\n' || hex.charAt(i) == '\t'){
				if (lastSpace == i - 1){
					lastSpace = i;
					continue; // just a space
				}
				numbers[currentNumber++] = unhex(hex.substring(lastSpace + 1, i));
				lastSpace = i;
			}
		}
		int[] trimNumbers = new int[currentNumber];
		System.arraycopy(numbers, 0, trimNumbers, 0, currentNumber);
		return trimNumbers;
	}

	public static void main(String[] args){
		for (int i = 0; i < 10; i++)
			System.out.println(i + " : " + hex(i, 4));
		for (int i = 100; i < 110; i++)
			System.out.println(i + " : " + hex(i, 4));
		for (int i = 65530; i < 65540; i++)
			System.out.println(i + " : " + hex(i, 4));
		for (int i = 65530; i < 65540; i++)
			System.out.println(i + " : " + hex(i, 8));
		for (int i = 0; i < 10; i++)
			System.out.println(i + " : " + hex(i, 4) + " " + unhex(hex(i, 4)));
		for (int i = 100; i < 110; i++)
			System.out.println(i + " : " + hex(i, 4) + " " + unhex(hex(i, 4)));
		for (int i = 65530; i < 65540; i++)
			System.out.println(i + " : " + hex(i, 4) + " " + unhex(hex(i, 4)));
		for (int i = 65530; i < 65540; i++)
			System.out.println(i + " : " + hex(i, 8) + " " + unhex(hex(i, 8)));
		String bunchONumbers = "45   23 35 A1 4568\n0034\t67 ffff ffff00 ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff0";
		System.out.println(hexArray(unhexArray(bunchONumbers)));
		System.out.println(hexArray(unhexArray(hexArray(unhexArray(bunchONumbers)))));
	}

}
