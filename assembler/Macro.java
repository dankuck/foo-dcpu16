
package assembler;
import java.util.*;

public class Macro{

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
			frame.addLine(line);
		}
		lines.resetLine();
		return frame;
	}
}
