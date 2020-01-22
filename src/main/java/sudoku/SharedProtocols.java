package sudoku;

/** Class of protocols for exchanged messages between different types of agents. */
public class SharedProtocols
{
	/** Protocol for negotiations between the Player and the Table. */
	public interface NegotiationsProtocol {}

	/** Protocol for registering both the Player and the Table by the Teacher. */
	public interface RegisteringProtocol {}

	/** Protocol for inspecting the Player by the Teacher, initiated by Supervisor. */
	public interface InspectionProtocol {}

	/** Protocol for starting new iteration by the Teacher, affecting both the Player and the Table. */
	public interface NewIterationProtocol {}

	/** Protocol for rewarding Players by the Teacher. */
	public interface AssessmentProtocol {}
}
