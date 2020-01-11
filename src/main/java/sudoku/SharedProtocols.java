package sudoku;

/** Class of protocols for exchanged messages between different types of agents. */
public class SharedProtocols
{
	/** Protocol for negotiations between the Player and the Table. */
	public interface NegotiationsProtocol {}

	/** Protocol for registering both the Player and the Table by the Teacher */
	public interface RegisteringProtocol {}
}
