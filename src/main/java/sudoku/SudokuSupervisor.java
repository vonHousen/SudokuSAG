package sudoku;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.PostStop;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;

/**
 * Simple agent-guardian of the whole multi-agent-system solving Sudoku.
 * Existence required by Akka.
 * Parent of Teacher agent.
 */
public class SudokuSupervisor extends AbstractBehavior<SudokuSupervisor.Command>
{
	public interface Command {}

	/**
	 * Message for controlled termination of the agent.
	 */
	public static final class TerminateMsg implements Command
	{
		final long _requestId;
		final ActorRef<String> _replyTo;

		TerminateMsg(long requestId, ActorRef<String> replyTo)
		{
			this._requestId = requestId;
			this._replyTo = replyTo;
		}
	}

	/** Sudoku riddle to be solved by the app. */
	private Sudoku _sudoku;
	/** Child Teacher agent */
	private ActorRef<Teacher.Protocol> _teacher;

	/**
	 * Public method that calls private constructor.
	 * Existence required by Akka.
	 * @return N/A
	 */
	public static Behavior<SudokuSupervisor.Command> create()
	{
		return Behaviors.setup(SudokuSupervisor::new);
	}

	private SudokuSupervisor(ActorContext<SudokuSupervisor.Command> context)
	{
		super(context);
		context.getLog().info("SudokuSupervisor started");
		readSudoku();
		_teacher = getContext()
				.spawn(Teacher.create(new Teacher.CreateMsg("TheOnlyTeacher", _sudoku)), "teacher");
		// getContext().watchWith(_teacher, new TerminateMsg(1L, getContext().getSelf())); TODO
	}

	/**
	 * Main method controlling incoming messages.
	 * Existence required by Akka.
	 * @return N/A
	 */
	@Override
	public Receive<SudokuSupervisor.Command> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(TerminateMsg.class, this::onTermination)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	/**
	 * Handler of PostStop signal.
	 * Expected after stopping SudokuSupervisor agent.
	 * @return N/A
	 */
	private SudokuSupervisor onPostStop()
	{
		getContext().getLog().info("SudokuSAG app stopped");
		return this;
	}

	/**
	 * Behaviour towards TerminateMsg message.
	 * Action performed on termination.
	 * @param terminateMsg	termination message
	 * @return N/A
	 */
	private Behavior<SudokuSupervisor.Command> onTermination(TerminateMsg terminateMsg)
	{
		getContext().getLog().info("SudokuSupervisor has been terminated");
		if (terminateMsg._replyTo != null)
			terminateMsg._replyTo.tell("SudokuSupervisor has been successfully terminated");
		return this;
	}

	/**
	 * Action of reading _sudoku from file.
	 */
	private void readSudoku()
	{
		_sudoku = new Sudoku();	// TODO implement reading _sudoku from file
	}
}
