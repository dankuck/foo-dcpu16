
package assembler;
import java.util.*;

public class FlowFrame{
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