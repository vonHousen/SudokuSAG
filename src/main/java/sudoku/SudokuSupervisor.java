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

	private Sudoku sudoku;

	public static Behavior<SudokuSupervisor.Command> create()
	{
		return Behaviors.setup(SudokuSupervisor::new);
	}

	private SudokuSupervisor(ActorContext<SudokuSupervisor.Command> context)
	{
		super(context);
		context.getLog().info("SudokuSupervisor started");
	}

	@Override
	public Receive<SudokuSupervisor.Command> createReceive()
	{
		return newReceiveBuilder()
				.onMessage(TerminateMsg.class, this::onTermination)
				.onSignal(PostStop.class, signal -> onPostStop())
				.build();
	}

	private SudokuSupervisor onPostStop()
	{
		getContext().getLog().info("SudokuSAG app stopped");
		return this;
	}

	private Behavior<SudokuSupervisor.Command> onTermination(TerminateMsg terminateMsg)
	{
		getContext().getLog().info("SudokuSupervisor has been terminated");
		if (terminateMsg._replyTo != null)
			terminateMsg._replyTo.tell("SudokuSupervisor has been successfully terminated");
		return this;
	}

	private void readSudoku()
	{
		sudoku = new Sudoku();	// TODO implement reading sudoku from file
	}
}
