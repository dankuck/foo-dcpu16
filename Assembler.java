/*
 * Usage: java Assembler input.asm output.x
 */

public class Assembler{

	public static void main(String[] args)
		throws Exception
	{
		String infile = args.length > 0 ? args[0] : getFilename("Input file");
		assembler.Assembler as = new assembler.Assembler(infile);
		String outfile = args.length > 1 ? args[1] : getFilename("Output file (blank to print to screen)");
		if (outfile.length() == 0)
			System.out.println(as.assembleToHex());
		else{
			as.assemble(outfile);
			as.debug(outfile + ".debug");
		}
	}

	public static String getFilename(String ask){
		System.out.print(ask + ": ");
		return System.console().readLine();
	}

}
