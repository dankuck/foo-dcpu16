
package assembler;
import java.util.*;


public class TextLine extends ArrayList<String>{

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

	public static String joinLine(List<String> line){
		if (line.size() == 0)
			return "";
		line = new ArrayList<String>(line);
		String str = line.remove(0) + " ";
		if (line.size() == 0)
			return str;
		String last = line.remove(line.size() - 1);
		for (String token : line)
			str += token + ", ";
		str += last;
		return str;
	}
}
